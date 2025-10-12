package com.wllcom.quicomguide.data.source

import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.dao.SectionElementWithContext
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.entities.MaterialFts
import com.wllcom.quicomguide.data.local.entities.SectionElementChunkEmbeddingEntity
import com.wllcom.quicomguide.data.local.entities.SectionElementEntity
import com.wllcom.quicomguide.data.ml.EmbeddingProvider
import com.wllcom.quicomguide.data.ml.Tokenizer
import com.wllcom.quicomguide.data.parser.XmlMaterialParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tartarus.snowball.ext.russianStemmer
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

enum class SearchMode { FTS, EMBEDDING, BOTH }

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

private const val CONCISE_CHAR_LIMIT = 500
private const val MIN_SCORE_RATIO = 0.15f
private const val MAX_SIM_WITH_SELECTED = 0.90f
private const val MIN_NOVELTY = 0.02f

@Singleton
class SmartSearchDataSource @Inject constructor(
    private val db: AppDatabase,
    private val embeddingProvider: EmbeddingProvider
) {

    private val dao = db.materialDao()
    private val queryDao = db.materialQueryDao()

    suspend fun addMaterial(xml: String) = withContext(Dispatchers.IO) {
        // parse
        val parsed = XmlMaterialParser.parse(xml)

        // prepare for DAO insert
        val sectionsForDao = parsed.sections.map { sec ->
            Triple(sec.title, sec.orderIndex, sec.elements) // elements: List<Pair<elementType, content>>
        }

        // insert tree without embeddings, get ids
        val (materialId, sectionsResult) = dao.insertMaterialTreeReturningIds(parsed.title, parsed.xmlRaw, sectionsForDao)

        // build contentFts
        val contentBuilder = StringBuilder()
        for (sec in parsed.sections) {
            contentBuilder.append(sec.title).append(" ")
            for ((_, content) in sec.elements) {
                contentBuilder.append(content).append(" ")
            }
        }
        val contentFts = contentBuilder.toString().trim()
        // update material row (set contentFts); insertMaterial uses REPLACE so provide id
        val materialRow = queryDao.getMaterialById(materialId)
        if (materialRow != null) {
            val updatedMaterialRow = materialRow.copy(contentFts = contentFts)
            dao.updateMaterial(updatedMaterialRow)
        }
        else {
            dao.insertMaterial(
                MaterialEntity(
                    id = materialId,
                    title = parsed.title,
                    xmlRaw = parsed.xmlRaw,
                    contentFts = contentFts
                )
            )
        }
        // insert FTS if needed (if you use contentEntity FTS this may be optional)
        try {
            dao.insertMaterialFts(MaterialFts(rowid = materialId, contentFts = contentFts))
        } catch (_: Exception) { /* ignore if auto-managed */ }

        // compute embeddings per element (sequentially or parallelize as needed)
        // iterate parsed.sections and use sectionsResult in the same order
        var sIdx = 0
        for (parsedSec in parsed.sections) {
            val (sectionId, elementIds) = sectionsResult[sIdx]
            for ((elemIdx, elem) in parsedSec.elements.withIndex()) {
                val elementId = elementIds[elemIdx]
                val content = elem.second

                // choose chunking method
                val chunks = if (embeddingProvider.publicTokenizer != null) {
                    chunkTextUsingTokenizer(embeddingProvider.publicTokenizer!!, content)
                } else {
                    chunkTextApproxChar(content, chunkSizeChars = 2000, overlapChars = 400)
                }

                // compute chunk embeddings on Default dispatcher
                val chunkEmbeddings = mutableListOf<FloatArray>()
                for (chunk in chunks) {
                    val emb = withContext(Dispatchers.Default) {
                        embeddingProvider.embed(chunk) // assume embedder returns normalized vector; if not, normalize here
                    }
                    // don't re-normalize if embedder gives normalized vectors; but safe to l2Normalize if uncertain
                    // l2Normalize(emb) // skip if embedder already normalizes
                    chunkEmbeddings.add(emb)
                }

                // aggregate: mean pooling of chunk embeddings; then normalize aggregate
                val dim = chunkEmbeddings.firstOrNull()?.size ?: 0
                val agg = FloatArray(dim)
                if (dim > 0 && chunkEmbeddings.isNotEmpty()) {
                    for (ce in chunkEmbeddings) {
                        for (i in 0 until dim) agg[i] += ce[i]
                    }
                    for (i in 0 until dim) agg[i] = agg[i] / chunkEmbeddings.size
                    // normalize aggregated vector for cosine comparison
                    l2Normalize(agg)
                }

                // persist chunk embeddings and aggregated embedding
                val chunkEntities = chunkEmbeddings.mapIndexed { idx, ch ->
                    SectionElementChunkEmbeddingEntity(
                        sectionElementId = elementId,
                        chunkIndex = idx,
                        embedding = floatArrayToBytes(ch)
                    )
                }
                if (chunkEntities.isNotEmpty()) {
                    dao.insertChunkEmbeddings(chunkEntities)
                }

                val aggBytes = if (agg.isNotEmpty()) floatArrayToBytes(agg) else null

                // update element row with embedding (we assume DAO has updateSectionElement)
                val elementRow = queryDao.getElementById(elementId)
                if (elementRow != null) {
                    val updated = elementRow.copy(embedding = aggBytes)
                    dao.updateSectionElement(updated)
                } else {
                    // fallback: construct object (we know sectionId, etc.)
                    dao.updateSectionElement(
                        SectionElementEntity(
                            id = elementId,
                            sectionId = sectionId,
                            elementType = elem.first,
                            content = content,
                            orderIndex = elemIdx,
                            embedding = aggBytes
                        )
                    )
                }
            }
            sIdx++
        }
    }

    // ----------------------
    // snippet builder (simple)
    // ----------------------
    private fun buildSnippetSimple(material: MaterialEntity, query: String, snippetLen: Int = 200): Snippet {
        val full = (material.contentFts ?: material.xmlRaw).replace(Regex("\\s+"), " ").trim()
        val q = query.trim()
        if (q.isEmpty()) {
            return Snippet(material.id, material.title, full.take(snippetLen))
        }

        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.lowercase() }
        val lcFull = full.lowercase()
        var idx = -1
        for (t in terms) {
            idx = lcFull.indexOf(t)
            if (idx >= 0) break
        }

        if (idx < 0) {
            return Snippet(material.id, material.title, full.take(snippetLen))
        }

        val start = (idx - snippetLen / 4).coerceAtLeast(0)
        val end = (idx + snippetLen - snippetLen / 4).coerceAtMost(full.length)
        var snip = full.substring(start, end).trim()
        if (start > 0) snip = "..." + snip
        if (end < full.length) snip = snip + "..."
        return Snippet(material.id, material.title, snip)
    }


    suspend fun search(query: String, mode: SearchMode = SearchMode.BOTH, topK: Int = 10): SearchResponse = withContext(Dispatchers.IO) {
        val snippets = mutableListOf<Snippet>()
        val topMaterials = mutableListOf<MaterialSearchResult>()
        var conciseAnswer: String? = null

        // 1) FTS branch
        if (mode == SearchMode.FTS || mode == SearchMode.BOTH) {
            val ftsQuery = query.trim()
            if (ftsQuery.isNotEmpty()) {
                val ftsRowIds = try {
                    queryDao.searchMaterialFtsRowIds(ftsQuery)
                } catch (e: Exception) {
                    emptyList()
                }
                for (rowId in ftsRowIds) {
                    val mat = queryDao.getMaterialById(rowId) ?: continue
                    val snip = buildSnippetSimple(mat, query)
                    snippets.add(snip)
                }
            }
        }

        // 2) Embedding branch
        if (mode == SearchMode.EMBEDDING || mode == SearchMode.BOTH) {
            // compute query embedding on Default dispatcher
            val queryEmb = withContext(Dispatchers.Default) {
                embeddingProvider.embed(query)
            }.also { l2Normalize(it) } // normalize query embedding (safe even if embedder normalized)

            // get elements with embeddings
            val elements = queryDao.getAllElementsWithContextHavingEmbedding()


            val elementScores = mutableListOf<Pair<SectionElementWithContext, Float>>()
            for (ewc in elements) {
                val embBytes = ewc.element.embedding ?: continue
                val embVec = bytesToFloatArray(embBytes)
                val score = cosineSimilarity(queryEmb, embVec)
                elementScores.add(ewc to score)
            }

            val sortedElements = elementScores.sortedByDescending { it.second }.take(200) // limit

            conciseAnswer = buildConciseAnswerDynamic(sortedElements, charLimit = 500)


            val materialToBest = mutableMapOf<Long, Pair<SectionElementWithContext, Float>>()
            for ((ewc, score) in elementScores) {
                val mId = ewc.materialId
                val prev = materialToBest[mId]
                if (prev == null || score > prev.second) materialToBest[mId] = ewc to score
            }
            val topByEmbedding = materialToBest.entries
                .sortedByDescending { it.value.second }
                .take(topK)
                .map { (materialId, pair) ->
                    val (ewc, score) = pair
                    val mat = queryDao.getMaterialById(materialId)
                    val snippet = if (mat != null) buildSnippetSimple(mat, query) else Snippet(materialId, ewc.materialTitle, ewc.element.content.take(200))
                    MaterialSearchResult(materialId = materialId, materialTitle = ewc.materialTitle, score = score, snippet = snippet)
                }

            // add embedding results to topMaterials
            topMaterials.addAll(topByEmbedding)

            // merge top embedding snippets to main snippets list (avoid duplicates)
            val existingMatIds = snippets.map { it.materialId }.toMutableSet()
            for (msr in topByEmbedding) {
                if (msr.materialId !in existingMatIds) {
                    snippets.add(msr.snippet)
                    existingMatIds.add(msr.materialId)
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

    private fun stemRussianText(text: String): String {
        val stemmer = russianStemmer()

        val words = text.split(Regex("\\W+"))
            .filter { it.isNotBlank() }

        val stemmedWords = words.map { word ->
            stemmer.current = word.lowercase()
            stemmer.stem()
            stemmer.current
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

    // --- helper: smart trim snippet ---
    private fun smartTrimSnippet(text: String, limit: Int = 180): String {
        val full = text.replace(Regex("\\s+"), " ").trim()
        if (full.length <= limit) return full
        // попробуем разбить на предложения и собрать до лимита
        val sentences = full.split(Regex("(?<=[.!?])\\s+"))
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.isNotEmpty() && sb.length + 1 + s.length > limit) break
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(s)
        }
        val out = if (sb.isNotEmpty()) sb.toString() else full.substring(0, limit).trimEnd()
        return if (out.length < full.length) out.trimEnd().trimEnd('.') + "..." else out
    }


    private fun buildConciseAnswerDynamic(
        scoredElements: List<Pair<SectionElementWithContext, Float>>,
        charLimit: Int = CONCISE_CHAR_LIMIT
    ): String? {
        if (scoredElements.isEmpty()) return null

        // Precompute top score
        val topScore = scoredElements.firstOrNull()?.second ?: return null
        val scoreThreshold = topScore * MIN_SCORE_RATIO

        val selectedSnippets = mutableListOf<String>()
        val selectedEmbeddings = mutableListOf<FloatArray>() // aggregate embeddings of selected snippets (we'll compare candidate embedding vs these)

        val sb = StringBuilder()

        for ((ewc, score) in scoredElements) {
            if (score < scoreThreshold) break

            val embBytes = ewc.element.embedding ?: continue
            val candEmb = bytesToFloatArray(embBytes)

            var maxSim = 0.0f
            for (sel in selectedEmbeddings) {
                val sim = cosineSimilarity(candEmb, sel)
                if (sim > maxSim) maxSim = sim
                if (maxSim >= MAX_SIM_WITH_SELECTED) break
            }

            val novelty = (1.0f - maxSim) * score

            if (maxSim >= MAX_SIM_WITH_SELECTED) {
                continue
            }
            if (novelty < MIN_NOVELTY) {
                continue
            }

            val frag = smartTrimSnippet(ewc.element.content, limit = 180)
            if (frag.isBlank()) continue

            val key = frag.trim().lowercase()
            if (selectedSnippets.any { it.trim().lowercase() == key }) continue

            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(frag)
            selectedSnippets.add(frag)
            selectedEmbeddings.add(candEmb)

            if (sb.length >= charLimit) {
                val truncated = sb.toString().substring(0, charLimit).trimEnd()
                return if (truncated.endsWith(".")) "$truncated..." else "$truncated..."
            }
        }

        val result = sb.toString().trim()
        return result.ifEmpty { null }
    }
}