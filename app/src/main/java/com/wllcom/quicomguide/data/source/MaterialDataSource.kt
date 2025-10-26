package com.wllcom.quicomguide.data.source

import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.dao.MaterialDao
import com.wllcom.quicomguide.data.local.entities.SectionElementChunkEmbeddingEntity
import com.wllcom.quicomguide.data.local.relations.MaterialWithSections
import com.wllcom.quicomguide.data.ml.EmbeddingProvider
import com.wllcom.quicomguide.data.ml.Tokenizer
import com.wllcom.quicomguide.data.parser.XmlMaterialParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tartarus.snowball.ext.englishStemmer
import org.tartarus.snowball.ext.russianStemmer
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

enum class EnumSearchMode { FTS, EMBEDDING, BOTH }

data class Snippet(
    val materialId: Long,
    val materialTitle: String,
    val text: String
)

data class MaterialSearchResult(
    val materialId: Long,
    val materialTitle: String,
    val score: Float,
    val snippet: Snippet
)

data class SearchResponse(
    val conciseAnswer: String?,
    val snippets: List<Snippet>,
    val topMaterials: List<MaterialSearchResult>
)

private const val CONCISE_CHAR_LIMIT = 512
private const val MIN_SCORE_RATIO = 0.98f
private const val MAX_SIM_WITH_SELECTED = 0.93f
private const val MIN_NOVELTY = 0.01f

@Singleton
class MaterialDataSource @Inject constructor(
    private val db: AppDatabase,
    private val embeddingProvider: EmbeddingProvider
) {

    private val dao = db.materialDao()

    suspend fun addMaterial(title: String, xml: String): Long? = withContext(Dispatchers.IO) {

        // parse
        val parsed = XmlMaterialParser.parse(xml)
        if (parsed == null)
            return@withContext null

        // prepare for DAO insert
        val sectionsForDao = parsed.sections.map { sec ->
            Triple(sec.title, sec.orderIndex, sec.elements)
        }

        // insert tree without embeddings, get ids
        val (materialId, sectionsResult) = dao.insertMaterialTree(
            title,
            parsed.xmlRaw.trim(),
            sectionsForDao
        )

        // build contentFts
        val contentBuilder = StringBuilder()
        for (sec in parsed.sections) {
            contentBuilder.append(sec.title).append(" ")
            for ((_, content) in sec.elements) {
                contentBuilder.append(content).append(" ")
            }
        }
        val contentFts = stemRussianEnglishText(contentBuilder.toString()).trim().lowercase()

        // update material row (set contentFts)
        val materialRow = dao.getMaterialById(materialId)
        val updatedMaterialRow = materialRow!!.copy(contentFts = contentFts)
        dao.updateMaterial(updatedMaterialRow)

//        dao.insertMaterialFts(MaterialFts(rowid = materialId, contentFts = contentFts))

        // embed material title
        val materialTitleEmb = withContext(Dispatchers.Default) {
            embeddingProvider.embed(title)
        }

        // compute embeddings per element
        var sIdx = 0
        for (parsedSec in parsed.sections) {
            val (sectionId, elementIds) = sectionsResult[sIdx]
            for ((elemIdx, elem) in parsedSec.elements.withIndex()) {
                val elementId = elementIds[elemIdx]
                val content = elem.second

                // choose chunking method
                val chunks = if (embeddingProvider.publicTokenizer != null) {
                    chunkTextUsingTokenizer(embeddingProvider.publicTokenizer!!, content, 512)
                } else {
                    chunkTextApproxChar(content, chunkSizeChars = 512, overlapChars = 50)
                }

                // compute chunk embeddings
                val chunkEmbeddings = mutableListOf<FloatArray>()
                for (chunk in chunks) {
                    val prepareChunk = chunk
                        .replace(Regex("</?(code|inline-code|tex|table|br)[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    val emb = withContext(Dispatchers.Default) {
                        embeddingProvider.embed(parsedSec.title + prepareChunk)
                    }
                    chunkEmbeddings.add(emb)
                }

                // aggregate: mean pooling of chunk embeddings; then normalize aggregate
                val dim = chunkEmbeddings.firstOrNull()?.size ?: 0
                val agg = FloatArray(dim)
                if (dim > 0 && chunkEmbeddings.isNotEmpty()) {
                    for (ce in chunkEmbeddings) {
                        for (i in 0 until dim) agg[i] += ce[i]
                    }
                    for (i in 0 until dim) agg[i] += materialTitleEmb[i]
                    for (i in 0 until dim) agg[i] = agg[i] / (chunkEmbeddings.size + 1)

                    // normalize aggregated vector for cosine comparison
                    l2Normalize(agg)
                }

                // persist chunk embeddings and aggregated embedding
                val chunkEntities = chunkEmbeddings.mapIndexed { idx, ch ->
                    SectionElementChunkEmbeddingEntity(
                        sectionElementId = elementId,
                        chunkIndex = idx,
                        chunkText = chunks[idx],
                        chunkEmbedding = floatArrayToBytes(ch)
                    )
                }
                if (chunkEntities.isNotEmpty())
                    dao.insertChunkEmbeddings(chunkEntities)

                val aggBytes = if (agg.isNotEmpty()) floatArrayToBytes(agg) else null

                // update element row with embedding (we assume DAO has updateSectionElement)
                val elementRow = dao.getSectionElementById(elementId)
                val updated = elementRow!!.copy(embedding = aggBytes)
                dao.updateSectionElement(updated)
            }
            sIdx++
        }
        return@withContext materialId
    }

    suspend fun updateMaterial(materialId: Long, xml: String): Long? = withContext(Dispatchers.IO) {
        val title = dao.getMaterialById(materialId)!!.title
        val newMaterialId = addMaterial(title, xml) ?: return@withContext null
        dao.moveMaterial(materialId, newMaterialId)
        return@withContext newMaterialId
    }

    suspend fun search(query: String, mode: EnumSearchMode = EnumSearchMode.BOTH, topK: Int = 10): SearchResponse =
        withContext(Dispatchers.IO) {
            val snippets = mutableListOf<Snippet>()
            val topMaterials = mutableListOf<MaterialSearchResult>()
            var conciseAnswer: String? = null

            // 1) FTS branch
            if (mode == EnumSearchMode.FTS || mode == EnumSearchMode.BOTH) {
                val ftsQuery = prepareFtsQuery(query)
                if (ftsQuery.isNotEmpty()) {
                    val ftsRowIds = try {
                        dao.searchMaterialFtsRowIds(ftsQuery)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    for (rowId in ftsRowIds) {
                        val matWithSect = dao.getMaterialWithSections(rowId) ?: continue
                        val snipText = buildSectionSimpleSnippet(matWithSect, query)
                        val snippet = Snippet(
                            materialId = matWithSect.material.id,
                            materialTitle = matWithSect.material.title,
                            text = snipText
                        )
                        snippets.add(snippet)
                        topMaterials.add(
                            MaterialSearchResult(
                                materialId = matWithSect.material.id,
                                materialTitle = matWithSect.material.title,
                                score = 0f,
                                snippet = snippet
                            )
                        )
                    }
                }
            }

            // 2) Embedding branch
            if (mode == EnumSearchMode.EMBEDDING || mode == EnumSearchMode.BOTH) {

                // compute query embedding on Default dispatcher
                val queryEmb = withContext(Dispatchers.Default) {
                    embeddingProvider.embed(query)
                }.also { l2Normalize(it) }

                // getting chunk
                val chunks = dao.getAllChunksWithContext()
                val scoredChunks = mutableListOf<Pair<MaterialDao.SectionElementChunkWithContext, Float>>()
                for (chunk in chunks) {
                    val embVec = bytesToFloatArray(chunk.chunk.chunkEmbedding)
                    val score = cosineSimilarity(queryEmb, embVec)
                    scoredChunks += chunk to score
                }
                val sortedChunks = scoredChunks.sortedByDescending { it.second }.take(200)

                // short answer
                conciseAnswer = buildConciseAnswerDynamicFromChunks(query, sortedChunks, recomendCharLimit = 512)

                // better chunk for material
                val materialToBest = mutableMapOf<Long, Pair<MaterialDao.SectionElementChunkWithContext, Float>>()
                for ((ewc, score) in sortedChunks) {
                    val mId = ewc.materialId
                    val prev = materialToBest[mId]
                    if (prev == null || score > prev.second)
                        materialToBest[mId] = ewc to score
                }

                val topByEmbedding = materialToBest.entries
                    .sortedByDescending { it.value.second }
                    .take(topK)
                    .map { (materialId, pair) ->
                        val (ewc, score) = pair
                        val mat = dao.getMaterialById(materialId)
                        val secEl = dao.getSectionElementById(ewc.elementId)
                        val snippet =
                            if (mat != null) {
                                val snipText = buildSimpleSnippet(secEl?.content ?: "", query)
                                Snippet(
                                    materialId = materialId,
                                    materialTitle = ewc.materialTitle,
                                    text = snipText
                                )
                            } else
                                Snippet(
                                    materialId,
                                    ewc.materialTitle,
                                    secEl?.content?.take(200) ?: "Содержимое не найдено"
                                )

                        MaterialSearchResult(
                            materialId = materialId,
                            materialTitle = ewc.materialTitle,
                            score = score,
                            snippet = snippet
                        )
                    }

                // add embedding results to topMaterials
                // merge top embedding snippets to main snippets list (avoid duplicates)
                val existingMaterialIds = topMaterials.map { it.materialId }.toMutableSet()
                val existingSnippetMaterialIds = snippets.map { it.materialId }.toMutableSet()
                for (msr in topByEmbedding) {
                    if(msr.materialId !in existingMaterialIds){
                        topMaterials.add(msr)
                        existingMaterialIds.add(msr.materialId)
                    }

                    if (msr.materialId !in existingSnippetMaterialIds) {
                        snippets.add(msr.snippet)
                        existingSnippetMaterialIds.add(msr.materialId)
                    }
                }
            }

            SearchResponse(conciseAnswer = conciseAnswer, snippets = snippets, topMaterials = topMaterials)
        }

    private fun l2Normalize(v: FloatArray) {
        var s = 0.0f
        for (x in v) s += x * x
        val norm = sqrt(s.toDouble()).toFloat().coerceAtLeast(1e-9f)
        for (i in v.indices) v[i] = v[i] / norm
    }

    private fun floatArrayToBytes(array: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(4 * array.size)
        array.forEach { bb.putFloat(it) }
        return bb.array()
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(bytes)
        val fa = FloatArray(bytes.size / 4)
        for (i in fa.indices) fa[i] = bb.getFloat(i * 4)
        return fa
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        // both should be normalized to unit length for dot product = cosine
        var dot = 0.0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) dot += a[i] * b[i]
        return dot
    }

    private fun prepareFtsQuery(rawQuery: String): String {
        return stemRussianEnglishText(rawQuery)
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .joinToString(" OR ")
    }

    private fun stemRussianEnglishText(text: String): String {
        val ruStemmer = russianStemmer()
        val enStemmer = englishStemmer()

        val enWords = text.split(Regex("\\W+"))
            .filter { it.isNotBlank() }

        val enStemmedWords = enWords.map { word ->
            enStemmer.current = word.lowercase()
            enStemmer.stem()
            enStemmer.current
        }

        val stemmedWords = enStemmedWords.map { word ->
            ruStemmer.current = word.lowercase()
            ruStemmer.stem()
            ruStemmer.current
        }

        return stemmedWords.joinToString(" ")
    }

    /**
     * Chunking using tokenizer (recommended)
     * - tokenizer.encode(text) -> tokens
     * - split tokens into windows of maxTokens with overlap
     * - decode each window back to string via tokenizer.decode(...)
     */
    fun chunkTextUsingTokenizer(
        tokenizer: Tokenizer,
        text: String,
        maxTokens: Int = 512,
        overlapTokens: Int = 50
    ): List<String> {
        val tokens = tokenizer.encode(text)
        if (tokens.isEmpty()) return listOf(text)
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val end = min(tokens.size, i + maxTokens)
            val window = tokens.slice(i until end)
            val chunkText = tokenizer.decode(window)
            chunks += chunkText
            if (end == tokens.size) break
            i = end - overlapTokens
            if (i < 0) i = 0
        }
        return chunks
    }

    /**
     * Fallback char-based chunking (approximate). Uses chunkSizeChars + overlapChars.
     */
    fun chunkTextApproxChar(text: String, chunkSizeChars: Int = 2000, overlapChars: Int = 400): List<String> {
        if (text.length <= chunkSizeChars) return listOf(text)
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val end = min(text.length, i + chunkSizeChars)
            chunks += text.substring(i, end)
            if (end == text.length) break
            i = end - overlapChars
            if (i < 0) i = 0
        }
        return chunks
    }

    private fun buildSectionSimpleSnippet(
        materialWithSection: MaterialWithSections,
        query: String,
        snippetLen: Int = 200
    ): String {
        var sectionContent =
            materialWithSection.sections.firstOrNull()?.elements?.firstOrNull()?.content?.take(snippetLen) ?: ""
        sectionContent = sectionContent
            .replace(Regex("</?(code|inline-code|br)[^>]*>"), " ")
            .replace(Regex("</?table[^>]*>"), "<таблица>")
            .replace(Regex("</?tex[^>]*>"), "<форматированный текст>")
            .replace(Regex("\\s+"), " ")
            .trim()

        val q = query.trim()
        if (q.isEmpty()) {
            return sectionContent.take(snippetLen)
        }

        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.lowercase() }
        var idx = -1
        for (t in terms) {
            for (section in materialWithSection.sections) {
                for (element in section.elements) {
                    val lcSectionContent = element.content.lowercase()
                    idx = lcSectionContent.indexOf(t)
                    if (idx >= 0) {
                        sectionContent = element.content.replace(Regex("\\s+"), " ").trim()
                        break
                    }
                }
                if (idx >= 0) break
            }
            if (idx >= 0) break
        }

        if (idx < 0) {
            return sectionContent.take(snippetLen)
        }

        sectionContent = sectionContent
            .replace(Regex("</?(code|inline-code|br)[^>]*>"), " ")
            .replace(Regex("</?table[^>]*>"), "<таблица>")
            .replace(Regex("</?tex[^>]*>"), "<форматированный текст>")
            .replace(Regex("\\s+"), " ")
            .trim()

        var start = (idx - snippetLen / 4).coerceAtLeast(0)
        if (start != 0) {
            if(sectionContent[start - 1] != ' ')
                start = sectionContent.indexOf(char = ' ', startIndex = start) + 1
        }
        val end = (idx + snippetLen - snippetLen / 4).coerceAtMost(sectionContent.length)
        var snip = sectionContent.substring(start, end).trim()
        if (start > 0) snip = "..." + snip
        if (end < sectionContent.length) snip = snip + "..."
        return snip
    }

    private fun buildSimpleSnippet(content: String, query: String, snippetLen: Int = 200): String {
        var sectionContent = content
        sectionContent = sectionContent
            .replace(Regex("</?(code|inline-code|br)[^>]*>"), " ")
            .replace(Regex("</?table[^>]*>"), "<таблица>")
            .replace(Regex("</?tex[^>]*>"), "<форматированный текст>")
            .replace(Regex("\\s+"), " ")
            .trim()

        val q = query.trim()
        if (q.isEmpty()) {
            return sectionContent.take(snippetLen)
        }

        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.lowercase() }
        var idx = -1
        for (t in terms) {
            val lcSectionContent = sectionContent.lowercase()
            idx = lcSectionContent.indexOf(t)
            if (idx >= 0) break
        }

        if (idx < 0) {
            return sectionContent.take(snippetLen)
        }

        var start = (idx - snippetLen / 4).coerceAtLeast(0)
        if (start != 0) {
            if (sectionContent[start - 1] != ' ')
                start = sectionContent.indexOf(char = ' ', startIndex = start) + 1
        }
        val end = (idx + snippetLen - snippetLen / 4).coerceAtMost(sectionContent.length)
        var snip = sectionContent.substring(start, end).trim()
        if (start > 0) snip = "..." + snip
        if (end < sectionContent.length) snip = snip + "..."
        return snip
    }


//    private fun smartTrimSnippet(text: String, limit: Int = 180): String {
//        val full = text.replace(Regex("\\s+"), " ").trim()
//        if (full.length <= limit) return full
//        val sentences = full.split(Regex("(?<=[.!?])\\s+"))
//        val sb = StringBuilder()
//        for (s in sentences) {
//            if (sb.isNotEmpty() && sb.length + 1 + s.length > limit) break
//            if (sb.isNotEmpty()) sb.append(" ")
//            sb.append(s)
//        }
//        val out = if (sb.isNotEmpty()) sb.toString() else full.substring(0, limit).trimEnd()
//        return if (out.length < full.length) out.trimEnd().trimEnd('.') + "..." else out
//    }

//    private fun smartTrimSnippet(text: String, limit: Int = 180): String {
//        val full = text.replace(Regex("\\s+"), " ").trim()
//        if (full.length <= limit) return full
//
//        val tagRegex = Regex("""<(code|inline-code|tex|table)(\b[^>]*)?>.*?</\1>|<br\s*/>""")
//
//        val sb = StringBuilder()
//        var consumed = 0
//        var lastIndex = 0
//
//        for (match in tagRegex.findAll(full)) {
//            val tagStart = match.range.first
//            val tagEnd = match.range.last + 1
//
//            if (tagStart > lastIndex) {
//                val chunk = full.substring(lastIndex, tagStart)
//                if (consumed + chunk.length >= limit) {
//                    sb.append(chunk.take(limit - consumed).trimEnd()).append("...")
//                    return sb.toString()
//                }
//                sb.append(chunk)
//                consumed += chunk.length
//            }
//
//            sb.append(full.substring(tagStart, tagEnd))
//
//            lastIndex = tagEnd
//        }
//
//        if (lastIndex < full.length) {
//            val tail = full.substring(lastIndex)
//            if (consumed + tail.length > limit) {
//                sb.append(tail.take(limit - consumed).trimEnd()).append("...")
//            } else {
//                sb.append(tail)
//            }
//        }
//
//        return sb.toString()
//    }

    private fun smartTrimSnippet(
        text: String,
        query: String,
        limit: Int = 180
    ): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= limit) return clean

        val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
        val target = sentences.firstOrNull { it.contains(query, ignoreCase = true) }
            ?: run {
                val take = StringBuilder()
                for (s in sentences) {
                    if (take.isNotEmpty() && take.length + 1 + s.length > limit) break
                    if (take.isNotEmpty()) take.append(" ")
                    take.append(s)
                }
                take.toString()
            }

        fun trimByWordsPreservingTags(input: String, limit: Int): String {
            val tagRegex = Regex("""<(code|inline-code|tex|table)(\b[^>]*)?>|</(code|inline-code|tex|table)>|<br\s*/>""")
            val sb = StringBuilder()
            var consumed = 0
            var lastIndex = 0

            for (m in tagRegex.findAll(input)) {
                val start = m.range.first
                val end = m.range.last + 1

                if (start > lastIndex) {
                    val chunk = input.substring(lastIndex, start)
                    val words = chunk.split(" ")
                    for (w in words) {
                        if (w.isBlank()) continue
                        if (consumed + w.length + 1 > limit) {
                            return sb.toString().trimEnd() + "..."
                        }
                        if (sb.isNotEmpty()) {
                            sb.append(" ")
                            consumed++
                        }
                        sb.append(w)
                        consumed += w.length
                    }
                }

                val tagText = input.substring(start, end)
                if (!tagText.startsWith("<br")) {
                    sb.append(tagText)
                }

                lastIndex = end
            }

            if (lastIndex < input.length) {
                val remainder = input.substring(lastIndex)
                val words = remainder.split(" ")
                for (w in words) {
                    if (w.isBlank()) continue
                    if (consumed + w.length + 1 > limit) {
                        return sb.toString().trimEnd() + "..."
                    }
                    if (sb.isNotEmpty()) {
                        sb.append(" ")
                        consumed++
                    }
                    sb.append(w)
                    consumed += w.length
                }
            }

            return sb.toString()
        }

        fun closeUnclosedTags(text: String): String {
            val stack = ArrayDeque<String>()
            val result = StringBuilder(text)
            val openTag = Regex("""<(code|inline-code|tex|table)(\b[^>]*)?>""")
            val closeTag = Regex("""</(code|inline-code|tex|table)>""")

            openTag.findAll(text).forEach { stack.addLast(it.groupValues[1]) }
            closeTag.findAll(text).forEach { stack.removeLastOrNull() }

            while (stack.isNotEmpty()) {
                val t = stack.removeLast()
                result.append("</").append(t).append(">")
            }
            return result.toString()
        }

        val limited = trimByWordsPreservingTags(target, limit)
        return closeUnclosedTags(limited)
    }

    private fun buildConciseAnswerDynamicFromChunks(
        query: String,
        scoredChunks: List<Pair<MaterialDao.SectionElementChunkWithContext, Float>>,
        recomendCharLimit: Int = CONCISE_CHAR_LIMIT
    ): String? {
        if (scoredChunks.isEmpty()) return null

        val topScore = scoredChunks.firstOrNull()?.second ?: return null
        val scoreThreshold = topScore * MIN_SCORE_RATIO

        val selectedSnippets = mutableListOf<String>()
        val selectedEmbeddings = mutableListOf<FloatArray>()
        val sb = StringBuilder()

        for ((chunk, score) in scoredChunks) {
            if (score < scoreThreshold) break

            val candEmb = bytesToFloatArray(chunk.chunk.chunkEmbedding)

            var maxSim = 0.0f
            for (sel in selectedEmbeddings) {
                val sim = cosineSimilarity(candEmb, sel)
                if (sim > maxSim) maxSim = sim
                if (maxSim >= MAX_SIM_WITH_SELECTED) break
            }

            val novelty = (1.0f - maxSim) * score
            if (maxSim >= MAX_SIM_WITH_SELECTED || novelty < MIN_NOVELTY) continue

            val frag = smartTrimSnippet(chunk.chunk.chunkText, query, limit = 180)
            if (frag.isBlank()) continue

            val key = frag.trim().lowercase()
            if (selectedSnippets.any { it.trim().lowercase() == key }) continue

            if (sb.isNotEmpty()) sb.append("<br/><br/>")
            sb.append(frag)
            selectedSnippets.add(frag)
            selectedEmbeddings.add(candEmb)

            if (sb.length >= recomendCharLimit) {
//                val truncated = sb.toString().substring(0, recomendCharLimit).trimEnd()
//                return if (truncated.endsWith(".")) "$truncated..." else "$truncated..."
                return sb.toString().trim()
            }
        }

        val result = sb.toString().trim()
        return result.ifEmpty { null }
    }
}