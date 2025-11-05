package com.wllcom.quicomguide.data.source

import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.dao.MaterialDao
import com.wllcom.quicomguide.data.local.entities.MaterialFts
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
            embeddingProvider.embed(title.lowercase())
        }

        // compute embeddings per element
        var sIdx = 0
        for (parsedSec in parsed.sections) {
            val (sectionId, elementIds) = sectionsResult[sIdx]
            for ((elemIdx, elem) in parsedSec.elements.withIndex()) {
                val elementId = elementIds[elemIdx]
                val content = if(elem.first == "example") "Пример " + elem.second else elem.second

                // choose chunking method
                val newContent = encodeWhitespaces(content)
                val maxTokensChunk = (128 + newContent.second).coerceAtMost(512)
                val chunks = if (embeddingProvider.publicTokenizer != null) {
                    chunkTextUsingTokenizer(
                        embeddingProvider.publicTokenizer!!,
                        newContent.first,
                        maxTokensChunk,
                        0
                    )
                } else {
                    chunkTextApproxChar(content, chunkSizeChars = maxTokensChunk, overlapChars = 0)
                }

                // compute chunk embeddings
                val chunkEmbeddings = mutableListOf<FloatArray>()
                for (chunk in chunks) {
                    val prepareChunk = simplifyXml(simplifyKaTeXInTexTags(decodeAllWhitespaces(chunk)))

                    val emb = withContext(Dispatchers.Default) {
                        embeddingProvider.embed(prepareChunk.lowercase())
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
                        chunkText = decodeAllWhitespaces(chunks[idx]),
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
                    embeddingProvider.embed(query.lowercase())
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
                conciseAnswer = buildConciseAnswerDynamicFromChunks(dao, query, sortedChunks)

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

    private fun simplifyXml(input: String): String {
        if (input.isEmpty()) return input

        return input
            .replace(Regex("</?(code|inline-code|tex|inline-tex|table|br)[^>]*>"), "")
//            .replace(Regex("</?(br)[^>]*>"), "")
//            .replace(Regex("</?(code|inline-code)[^>]*>"), " код ")
//            .replace(Regex("</?(tex|inline-tex)[^>]*>"), " формула ")
//            .replace(Regex("</?(table)[^>]*>"), " таблица ")
//            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun simplifyKaTeXInTexTags(input: String): String {
        if (input.isEmpty()) return input

        // Основные regex'ы
        val fullPairRegex = Regex("(?s)<(?:tex|inline-tex)\\b([^>]*)>(.*?)</(?:tex|inline-tex)>",
            setOf(RegexOption.IGNORE_CASE)) // DOT_MATCHES_ALL через (?s) в паттерне

        // Одиночный открывающий тег и остаток до конца (без закрытия)
        val openTagRegex = Regex("(?is)<(tex|inline-tex)\\b([^>]*)>(.*)\$", setOf(RegexOption.IGNORE_CASE))
        // Одиночный закрывающий тег и всё до него (без открытия)
        val closeTagRegex = Regex("(?is)^(.*?)</(tex|inline-tex)>", setOf(RegexOption.IGNORE_CASE))

        // Вспомогательные regex'ы для очистки внутри блока
        val beginEndRegex = Regex("""\\begin\{[^}]*\}|\s*\\end\{[^}]*\}""", RegexOption.IGNORE_CASE)
        val mathDelimitersRegex = Regex("""\$\$?""")
        val parenDelimsRegex = Regex("""\\\(|\\\)|\\\[|\\\]""")
        val dropCommandsRegex = Regex(
            """\\(?:ensuremath|providecommand|renewcommand|newcommand|includegraphics|include|input|mathchoice|html@mathml|htmlClass|htmlStyle|htmlId|@ifundefinedelse|@ifundefined|@ifnextchar|@ifstar|addtocounter|setcounter|addtolength|setlength|raisebox|framebox|makebox|textsuperscript|textsubscript|textnormal|texttt|textsf|textrm|textsc|textmd|textit|textbf|bibliography|noteref|pageref|nameref|labelsep|label|ref|eqref|cite|bibitem|def|gdef|mathsf|mathit|mathbf|mathrm|mathcal|mathbb|mathfrak|left|right|rule|vcenter|smallskip|medskip|bigskip|hline|hdashline|kern|hskip|mskip|hphantom|vphantom|phantom|quad|qquad|enspace|enskip|thinspace|medspace|thickspace|negthinspace|negmedspace|negthickspace|htmlData|TeX|LaTeX|KaTeX|newline|linebreak|pagebreak|nopagebreak|allowbreak|hspace|vspace|hfill|vfill|,|;|:|!|>|<|colorbox|fcolorbox|textcolor|emph|underline|overline|boxed|fbox|bfseries|itshape|scshape|sffamily|rmfamily|ttfamily|mdseries|upshape|slshape)(?:\{[^}]*\})?""",
            RegexOption.IGNORE_CASE
        )
        val spacingCmdsRegex = Regex("""\\(?:,|;|:|!|>|<|quad|qquad|enspace|enskip|thinspace|medspace|thickspace)""", RegexOption.IGNORE_CASE)
        val strayBackslashRegex = Regex("""\\(?=[^\p{L}])""")
        val supersubRegex = Regex("""\^\{([^}]*)\}""")
        val subRegex = Regex("""_\{([^}]*)\}""")
        val bracesRegex = Regex("""[{}]""")

        fun simplifyBlock(blockContent: String): String {
            var s = blockContent
            s = mathDelimitersRegex.replace(s, "")
            s = parenDelimsRegex.replace(s) {
                when (it.value) {
                    """\(""" -> "("
                    """\)""" -> ")"
                    """\[""" -> "["
                    """\]""" -> "]"
                    else -> ""
                }
            }
            s = beginEndRegex.replace(s, " ")
            s = dropCommandsRegex.replace(s, " ")
            s = spacingCmdsRegex.replace(s, " ")
            s = supersubRegex.replace(s) { mr -> "^${mr.groupValues[1]}" }
            s = subRegex.replace(s) { mr -> "_${mr.groupValues[1]}" }
            s = bracesRegex.replace(s, "")
            s = strayBackslashRegex.replace(s, "")
            s = s.replace(Regex("\\s+"), " ").trim()
            return s
        }

        // 1) Сначала заменяем все полностью закрытые пары (самый безопасный шаг)
        var result = fullPairRegex.replace(input) { m ->
            val inner = m.groups[2]?.value ?: ""
            simplifyBlock(inner)
        }

        // 2) Обработка случаев: сначала проверим — есть ли закрывающий тег, который встречается раньше открывающего
        // Мы будем итерировать, т.к. замены меняют строку и могут образовывать новые положения
        while (true) {
            val firstOpenIdx = Regex("(?i)<(?:tex|inline-tex)\\b").find(result)?.range?.first ?: -1
            val firstCloseIdx = Regex("(?i)</(?:tex|inline-tex)>").find(result)?.range?.first ?: -1

            if (firstOpenIdx == -1 && firstCloseIdx == -1) break

            // Случай: закрывающий тег встречается раньше (или нет открывающего)
            if (firstCloseIdx != -1 && (firstOpenIdx == -1 || firstCloseIdx < firstOpenIdx)) {
                // берём всё от начала до закрывающего тега
                val closeMatch = Regex("(?is)^(.*?)</(tex|inline-tex)>").find(result)
                if (closeMatch != null) {
                    val inner = closeMatch.groupValues[1]
                    val cleaned = simplifyBlock(inner)
                    // заменяем от начала до конца закрывающего тега на cleaned
                    val replaceEnd = closeMatch.range.last + 1
                    result = cleaned + result.substring(replaceEnd)
                    continue
                } else {
                    break
                }
            }

            // Случай: открывающий тег встречается раньше
            if (firstOpenIdx != -1 && (firstCloseIdx == -1 || firstOpenIdx < firstCloseIdx)) {
                // Найдём сам открывающий тег (чтобы получить где он заканчивается)
                val openMatch = Regex("(?is)<(tex|inline-tex)\\b([^>]*)>").find(result, firstOpenIdx)
                if (openMatch != null) {
                    val openEnd = openMatch.range.last + 1
                    // ищем закрывающий тег после openEnd
                    val closeAfterOpen = Regex("(?i)</(?:tex|inline-tex)>").find(result, openEnd)
                    if (closeAfterOpen != null) {
                        // Если он найден — это пара, но такие пары уже должны были быть обработаны первым шагом.
                        // Тем не менее — обработаем конкретный кусок между ними (надёжность).
                        val inner = result.substring(openEnd, closeAfterOpen.range.first)
                        val cleaned = simplifyBlock(inner)
                        result = result.substring(0, openMatch.range.first) + cleaned + result.substring(closeAfterOpen.range.last + 1)
                        continue
                    } else {
                        // Нет закрывающего — берём от открывающего до конца текущего фрагмента
                        val inner = result.substring(openEnd)
                        val cleaned = simplifyBlock(inner)
                        // заменяем открывающий тег + остаток на cleaned
                        result = result.substring(0, openMatch.range.first) + cleaned
                        continue
                    }
                } else {
                    break
                }
            }

            break
        }

        return result
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
     * Заменяет внутри <code>, <inline-code>, <table>:
     * '\n' -> '⇪'
     * '\r' -> '➽'
     * '\t' -> '▦'
     * ' '  -> '➲'
     * Возвращает Pair(результирующая строка, общее количество замен).
     */
    fun encodeWhitespaces(input: String): Pair<String, Int> {
        val regex = Regex("(?s)<(code|inline-code|table)(\\s[^>]*)?>(.*?)</\\1>", RegexOption.IGNORE_CASE)
        val sb = StringBuilder()
        var lastIndex = 0
        var totalReplacements = 0

        for (m in regex.findAll(input)) {
            val range = m.range
            sb.append(input, lastIndex, range.first)

            val tagName = m.groupValues[1]
            val attrs = m.groupValues[2]
            val content = m.groupValues[3]

            // Считаем вхождения каждого символа внутри контента
            val countNl = content.count { it == '\n' }
            val countCr = content.count { it == '\r' }
            val countTab = content.count { it == '\t' }
            val countSpace = content.count { it == ' ' }

            totalReplacements += countNl + countCr + countTab + countSpace

            // Выполняем замены внутри содержимого (порядок не критичен для этих символов)
            val newContent = buildString {
                append(content
                    .replace("\r", "➽")
                    .replace("\n", "⇪")
                    .replace("\t", "▦")
                    .replace(" ", "➲"))
            }

            sb.append("<").append(tagName)
            if (attrs.isNotEmpty()) sb.append(attrs)
            sb.append(">")
            sb.append(newContent)
            sb.append("</").append(tagName).append(">")
            lastIndex = range.last + 1
        }

        sb.append(input, lastIndex, input.length)
        return sb.toString() to totalReplacements
    }

    /**
     * Обратная функция: внутри тех же тегов заменяет токены обратно:
     * '⇪' -> '\n'
     * '➽' -> '\r'
     * '▦' -> '\t'
     * '➲' -> ' '
     * Возвращает Pair(результирующая строка, общее количество обратных замен).
     *
     * Реализация восстановления простая: для содержимого используем последовательные replace,
     * а количество замен считаем как (parts.size - 1) для каждого токена.
     */
    fun decodeAllWhitespaces(input: String): String {
        return input
            .replace("⇪", "\n")
            .replace("➽", "\r")
            .replace("▦", "\t")
            .replace("➲", " ")
    }

    /**
     * Fallback char-based chunking (approximate). Uses chunkSizeChars + overlapChars.
     */
    fun chunkTextApproxChar(text: String, chunkSizeChars: Int = 512, overlapChars: Int = 50): List<String> {
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


        val q = query.trim()
        if (q.isEmpty()) {

            sectionContent = sectionContent
                .replace(Regex("</?(code|inline-code|br)[^>]*>"), " ")
                .replace(Regex("</?table[^>]*>"), "<таблица>")
                .replace(Regex("</?(tex|inline-tex)[^>]*>"), "<форматированный текст>")
                .replace(Regex("\\s+"), " ")
                .trim()

            return sectionContent.take(snippetLen)
        }

        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.lowercase() }
        var idx = -1
        for (t in terms) {
            for (section in materialWithSection.sections) {
                for (element in section.elements) {

                    var lcSectionContent = element.content.lowercase()
                    lcSectionContent = lcSectionContent
                        .replace(Regex("</?(code|inline-code|br)[^>]*>"), " ")
                        .replace(Regex("</?table[^>]*>"), "<таблица>")
                        .replace(Regex("</?(tex|inline-tex)[^>]*>"), "<форматированный текст>")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    idx = lcSectionContent.indexOf(t)
                    if (idx >= 0) {
                        sectionContent = element.content
                            .replace(Regex("</?(code|inline-code|br)[^>]*>"), " ")
                            .replace(Regex("</?table[^>]*>"), "<таблица>")
                            .replace(Regex("</?(tex|inline-tex)[^>]*>"), "<форматированный текст>")
                            .replace(Regex("\\s+"), " ")
                            .trim()
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

//        sectionContent = sectionContent
//            .replace(Regex("</?(code|inline-code|br)[^>]*>"), " ")
//            .replace(Regex("</?table[^>]*>"), "<таблица>")
//            .replace(Regex("</?(tex|inline-tex)[^>]*>"), "<форматированный текст>")
//            .replace(Regex("\\s+"), " ")
//            .trim()

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
            .replace(Regex("</?(tex|inline-tex)[^>]*>"), "<форматированный текст>")
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


    private fun replaceTexWithPlaceholder(input: String): String {
        val inlineTexRe = Regex("""<inline-tex\b[^>]*>.*?</inline-tex>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val texRe = Regex("""<tex\b[^>]*>.*?</tex>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return input.replace(inlineTexRe, "<Большая формула>").replace(texRe, "<Большая формула>")
    }

    private fun collectTagEvents(input: String): List<Triple<Int, Boolean, String>> {
        // returns list of (position, isOpen, tagName) ordered by position
        val events = mutableListOf<Triple<Int, Boolean, String>>()
        val openRe = Regex("""<(code|inline-code|table|inline-tex|tex)(\b[^>]*)?>""", setOf(RegexOption.IGNORE_CASE))
        val closeRe = Regex("""</(code|inline-code|table|inline-tex|tex)>""", setOf(RegexOption.IGNORE_CASE))
        openRe.findAll(input).forEach { events.add(Triple(it.range.first, true, it.groupValues[1].lowercase())) }
        closeRe.findAll(input).forEach { events.add(Triple(it.range.first, false, it.groupValues[1].lowercase())) }
        return events.sortedBy { it.first }
    }

    private fun getUnclosedOpenTags(text: String): List<String> {
        val stack = ArrayDeque<String>()
        for ((_, isOpen, tag) in collectTagEvents(text)) {
            if (isOpen) {
                stack.addLast(tag)
            } else {
                if (stack.isNotEmpty() && stack.last() == tag) {
                    stack.removeLast()
                } else {
                    // a closing without matching open inside this text => ignore here
                }
            }
        }
        return stack.toList() // opened but not closed
    }

    private fun getUnopenedCloseTags(text: String): List<String> {
        val stack = ArrayDeque<String>()
        val unopened = mutableListOf<String>()
        for ((_, isOpen, tag) in collectTagEvents(text)) {
            if (isOpen) {
                stack.addLast(tag)
            } else {
                if (stack.isNotEmpty() && stack.last() == tag) {
                    stack.removeLast()
                } else {
                    // closing while no open in this text -> unopened
                    unopened.add(tag)
                }
            }
        }
        return unopened
    }

    private fun assembleSnippetForChunk(
        chunkWithCtx: MaterialDao.SectionElementChunkWithContext,
        query: String,
        dao: MaterialDao
    ): String {
        val chunkEntity = chunkWithCtx.chunk
        val sectionElementId = chunkEntity.sectionElementId
        val chunkIndex = chunkEntity.chunkIndex
        var snippet = chunkEntity.chunkText

        val unopenedClosers = getUnopenedCloseTags(snippet)
        if (unopenedClosers.isNotEmpty()) {
            val prev = dao.getChunkByElementIdAndIndex(sectionElementId, chunkIndex - 1)
            var prefix = ""
            for (t in unopenedClosers) {
                val openTag = "<$t>"
                val closeTag = "</$t>"
                val regexOpen = Regex("<$t(\\b|>)", RegexOption.IGNORE_CASE)
                if (prev != null && prev.chunkText.contains(regexOpen)) {
                    prefix = prev.chunkText.substring(
                        regexOpen.findAll(prev.chunkText).last().range.first
                    )
                } else {
                    if(t == "tex" || t == "inline-tex") {
                        snippet = snippet.removeRange(0, snippet.indexOf(closeTag) + closeTag.length)
                        prefix = "<Большая формула> $prefix"
                    }
                    else
                        prefix = "$openTag...$prefix"
                }

            }
            snippet = "$prefix$snippet"
        }

        val unclosedOpens = getUnclosedOpenTags(snippet)
        if (unclosedOpens.isNotEmpty()) {
            val next = dao.getChunkByElementIdAndIndex(sectionElementId, chunkIndex + 1)
            var addition = ""
            for (t in unclosedOpens) {
                val openTag = "<$t>"
                val closeTag = "</$t>"
                if (next != null && next.chunkText.contains(closeTag, ignoreCase = true)) {
                    val tagSnip = next.chunkText.take(next.chunkText.indexOf(closeTag)) + closeTag
                    addition = "$tagSnip$addition"
                    break
                } else {
                    if(t == "tex" || t == "inline-tex") {
                        snippet = snippet.removeRange(snippet.indexOf(openTag), snippet.length)
                        addition = " <Большая формула>$addition"
                    }
                    else
                        addition = "...$closeTag$addition"
                }

            }
            snippet = snippet + addition
        }

        return snippet.trim()
    }

    fun resetFiltrationSpecialCharactersXml(input: String): String {
        // Основные теги, в которых нужно обрабатывать содержимое (с атрибутами)
        val outerTagRegex = Regex(
            "<(content|example)(\\s+[^>]*)?>(.*?)</\\1>",
            RegexOption.DOT_MATCHES_ALL
        )

        // Теги, внутри которых ничего не меняем
        val protectedTagRegex = Regex(
            "<(code|tex|inline-code|inline-tex)(\\s+[^>]*)?>.*?</\\1>",
            RegexOption.DOT_MATCHES_ALL
        )

        // Универсальный регекс для любых XML-тегов
        val anyTagRegex = Regex("<[^>]+>")

        // Замена служебных символов XML
        fun escapeXml(text: String): String = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;","'")

        // Обработка текста вне <code> и т.п., но с сохранением всех тегов
        fun processOutsideProtected(text: String): String {
            val sb = StringBuilder()
            var lastIndex = 0

            for (tagMatch in anyTagRegex.findAll(text)) {
                val before = text.substring(lastIndex, tagMatch.range.first)
                sb.append(escapeXml(before)) // экранируем только чистый текст
                sb.append(tagMatch.value)    // теги не трогаем
                lastIndex = tagMatch.range.last + 1
            }

            // остаток после последнего тега
            sb.append(escapeXml(text.substring(lastIndex)))
            return sb.toString()
        }

        // Обработка содержимого внутри content/example
        fun processContent(text: String): String {
            val sb = StringBuilder()
            var lastIndex = 0

            // Пропускаем участки, находящиеся внутри защищённых тегов
            for (protectedMatch in protectedTagRegex.findAll(text)) {
                // участок ДО защищённого тега
                val before = text.substring(lastIndex, protectedMatch.range.first)
                sb.append(processOutsideProtected(before))
                // вставляем защищённый тег без изменений
                sb.append(protectedMatch.value)
                lastIndex = protectedMatch.range.last + 1
            }

            // остаток после последнего защищённого участка
            sb.append(processOutsideProtected(text.substring(lastIndex)))
            return sb.toString()
        }

        val result = StringBuilder()
        var lastIndex = 0

        // Ищем все <content ...>...</content> и <example ...>...</example>
        for (outerMatch in outerTagRegex.findAll(input)) {
            val before = input.substring(lastIndex, outerMatch.range.first)
            result.append(before) // часть вне целевых тегов — без изменений

            val tagName = outerMatch.groupValues[1]
            val tagAttrs = outerMatch.groupValues[2] // может быть пустым
            val innerText = outerMatch.groupValues[3]

            val processed = processContent(innerText)

            result.append("<$tagName${tagAttrs}>")
            result.append(processed)
            result.append("</$tagName>")

            lastIndex = outerMatch.range.last + 1
        }

        // Добавляем хвост (если после последнего блока что-то осталось)
        result.append(input.substring(lastIndex))

        return result.toString()
    }

    private suspend fun buildConciseAnswerDynamicFromChunks(
        dao: MaterialDao,
        query: String,
        scoredChunks: List<Pair<MaterialDao.SectionElementChunkWithContext, Float>>,
        recomendTop: Int = 3,
    ): String? {
        if (scoredChunks.isEmpty()) return null

        val topScore = scoredChunks.firstOrNull()?.second ?: return null
        val scoreThreshold = topScore * MIN_SCORE_RATIO

        val selectedSnippets = mutableListOf<String>()
        val selectedEmbeddings = mutableListOf<FloatArray>()
        val sb = StringBuilder()

        for ((chunkWithCtx, score) in scoredChunks) {
            if (score < scoreThreshold) break

            // embedding чанка (raw)
            val candEmb = bytesToFloatArray(chunkWithCtx.chunk.chunkEmbedding)

            // section element average embedding (если есть) и комбинирование
            val sectionEmbBytes = dao.getSectionElementById(chunkWithCtx.elementId)?.embedding
            val combined = if (sectionEmbBytes != null) {
                val sec = bytesToFloatArray(sectionEmbBytes)
                val minLen = minOf(candEmb.size, sec.size)
                val comb = FloatArray(minLen)
                val wChunk = 0.7f
                val wSec = 0.3f
                for (i in 0 until minLen)
                    comb[i] = candEmb[i] * wChunk + sec[i] * wSec

                l2Normalize(comb)
                comb
            } else {
                l2Normalize(candEmb)
                candEmb
            }

            // проверка схожести с уже выбранными
            var maxSim = 0.0f
            for (sel in selectedEmbeddings) {
                val sim = cosineSimilarity(combined, sel)
                if (sim > maxSim) maxSim = sim
                if (maxSim >= MAX_SIM_WITH_SELECTED) break
            }

            val novelty = (1.0f - maxSim) * score
            if (maxSim >= MAX_SIM_WITH_SELECTED || novelty < MIN_NOVELTY) continue

            // собрать фрагмент (учитываем соседние чанки через dao)
            val frag = assembleSnippetForChunk(chunkWithCtx, query, dao)
            if (frag.isBlank()) continue

            val key = frag.trim().lowercase()
            if (selectedSnippets.any { it.trim().lowercase() == key }) continue

            if (sb.isNotEmpty())
                sb.append("<br/><br/>")

            var prefixFrag = "<strong>Материал: ${chunkWithCtx.materialTitle}</strong><br/>"
            var suffixFrag = ""
            if (chunkWithCtx.chunk.chunkIndex != 0)
                prefixFrag += "..."
            if(frag.last() != '.' && frag.last() != ',' && frag.last() != '!' && frag.last() != '?')
                suffixFrag = "..."

            sb.append(prefixFrag + resetFiltrationSpecialCharactersXml(frag) + suffixFrag)
            selectedSnippets.add(frag)
            selectedEmbeddings.add(combined)

            if (selectedSnippets.size >= recomendTop) {
                break
            }
        }

        val result = sb.toString().trim()
        return result.ifEmpty { null }
    }

//    private fun smartTrimSnippet(
//        text: String,
//        query: String,
//        limit: Int = 180
//    ): String {
//        val clean = text.replace(Regex("\\s+"), " ").trim()
//        if (clean.length <= limit) return clean
//
//        val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
//        val target = sentences.firstOrNull { it.contains(query, ignoreCase = true) }
//            ?: run {
//                val take = StringBuilder()
//                for (s in sentences) {
//                    if (take.isNotEmpty() && take.length + 1 + s.length > limit) break
//                    if (take.isNotEmpty()) take.append(" ")
//                    take.append(s)
//                }
//                take.toString()
//            }
//
//        fun trimByWordsPreservingTags(input: String, limit: Int): String {
//            val tagRegex = Regex("""<(code|inline-code|tex|table)(\b[^>]*)?>|</(code|inline-code|tex|table)>|<br\s*/>""")
//            val sb = StringBuilder()
//            var consumed = 0
//            var lastIndex = 0
//
//            for (m in tagRegex.findAll(input)) {
//                val start = m.range.first
//                val end = m.range.last + 1
//
//                if (start > lastIndex) {
//                    val chunk = input.substring(lastIndex, start)
//                    val words = chunk.split(" ")
//                    for (w in words) {
//                        if (w.isBlank()) continue
//                        if (consumed + w.length + 1 > limit) {
//                            return sb.toString().trimEnd() + "..."
//                        }
//                        if (sb.isNotEmpty()) {
//                            sb.append(" ")
//                            consumed++
//                        }
//                        sb.append(w)
//                        consumed += w.length
//                    }
//                }
//
//                val tagText = input.substring(start, end)
//                if (!tagText.startsWith("<br")) {
//                    sb.append(tagText)
//                }
//
//                lastIndex = end
//            }
//
//            if (lastIndex < input.length) {
//                val remainder = input.substring(lastIndex)
//                val words = remainder.split(" ")
//                for (w in words) {
//                    if (w.isBlank()) continue
//                    if (consumed + w.length + 1 > limit) {
//                        return sb.toString().trimEnd() + "..."
//                    }
//                    if (sb.isNotEmpty()) {
//                        sb.append(" ")
//                        consumed++
//                    }
//                    sb.append(w)
//                    consumed += w.length
//                }
//            }
//
//            return sb.toString()
//        }
//
//        fun closeUnclosedTags(text: String): String {
//            val stack = ArrayDeque<String>()
//            val result = StringBuilder(text)
//            val openTag = Regex("""<(code|inline-code|tex|table)(\b[^>]*)?>""")
//            val closeTag = Regex("""</(code|inline-code|tex|table)>""")
//
//            openTag.findAll(text).forEach { stack.addLast(it.groupValues[1]) }
//            closeTag.findAll(text).forEach { stack.removeLastOrNull() }
//
//            while (stack.isNotEmpty()) {
//                val t = stack.removeLast()
//                result.append("</").append(t).append(">")
//            }
//            return result.toString()
//        }
//
//        val limited = trimByWordsPreservingTags(target, limit)
//        return closeUnclosedTags(limited)
//    }
//
//    private fun buildConciseAnswerDynamicFromChunks(
//        query: String,
//        scoredChunks: List<Pair<MaterialDao.SectionElementChunkWithContext, Float>>,
//        recomendCharLimit: Int = CONCISE_CHAR_LIMIT
//    ): String? {
//        if (scoredChunks.isEmpty()) return null
//
//        val topScore = scoredChunks.firstOrNull()?.second ?: return null
//        val scoreThreshold = topScore * MIN_SCORE_RATIO
//
//        val selectedSnippets = mutableListOf<String>()
//        val selectedEmbeddings = mutableListOf<FloatArray>()
//        val sb = StringBuilder()
//
//        for ((chunk, score) in scoredChunks) {
//            if (score < scoreThreshold) break
//
//            val candEmb = bytesToFloatArray(chunk.chunk.chunkEmbedding)
//
//            var maxSim = 0.0f
//            for (sel in selectedEmbeddings) {
//                val sim = cosineSimilarity(candEmb, sel)
//                if (sim > maxSim) maxSim = sim
//                if (maxSim >= MAX_SIM_WITH_SELECTED) break
//            }
//
//            val novelty = (1.0f - maxSim) * score
//            if (maxSim >= MAX_SIM_WITH_SELECTED || novelty < MIN_NOVELTY) continue
//
//            val frag = smartTrimSnippet(chunk.chunk.chunkText, query, limit = 180)
//            if (frag.isBlank()) continue
//
//            val key = frag.trim().lowercase()
//            if (selectedSnippets.any { it.trim().lowercase() == key }) continue
//
//            if (sb.isNotEmpty()) sb.append("<br/><br/>")
//            sb.append(frag)
//            selectedSnippets.add(frag)
//            selectedEmbeddings.add(candEmb)
//
//            if (sb.length >= recomendCharLimit) {
////                val truncated = sb.toString().substring(0, recomendCharLimit).trimEnd()
////                return if (truncated.endsWith(".")) "$truncated..." else "$truncated..."
//                return sb.toString().trim()
//            }
//        }
//
//        val result = sb.toString().trim()
//        return result.ifEmpty { null }
//    }

}