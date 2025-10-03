package com.wllcom.quicomguide.data.repo

import com.wllcom.quicomguide.data.local.MaterialDao
import com.wllcom.quicomguide.data.local.SectionDao
import com.wllcom.quicomguide.data.model.MaterialSectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class MaterialMatch(
    val materialId: Long,
    val title: String,
    val snippet: String,
    val score: Int
)

data class SmartSearchResult(val bestSnippet: String?, val matches: List<MaterialMatch>)

class SearchRepository(
    private val sectionDao: SectionDao,
    private val materialDao: MaterialDao
) {

    // Набор простых стоп-слов для русского; можно расширять
    private val stopWords = setOf(
        "как",
        "что",
        "почему",
        "и",
        "в",
        "на",
        "для",
        "не",
        "с",
        "по",
        "из",
        "за",
        "то",
        "это",
        "а",
        "или",
        "бы"
    )

    // Преобразует естественный запрос в FTS-friendly выражение
    private fun toFtsQuery(raw: String): String {
        val tokens = raw
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ") // оставляем буквы и цифры
            .split(Regex("\\s+"))
            .mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
            .filter { it !in stopWords }
            .map { token ->
                // добавляем wildcard для частичного совпадения
                if (token.length > 2) "$token*" else token
            }
        if (tokens.isEmpty()) return raw
        // соединяем через OR — FTS выдаст записи содержащие любые токены
        return tokens.joinToString(" OR ")
    }

    // Основная функция поиска: возвращает SmartSearchResult
    suspend fun searchSmart(rawQuery: String): SmartSearchResult =
        withContext(Dispatchers.Default) {
            if (rawQuery.isBlank()) return@withContext SmartSearchResult(null, emptyList())

            // формируем FTS-запрос
            val ftsQuery = toFtsQuery(rawQuery)

            // получаем matching sections (s.*) через DAO (Flow -> one-shot)
            val matchedSections = try {
                sectionDao.searchSectionsByFts(ftsQuery).first()
            } catch (e: Exception) {
                emptyList<MaterialSectionEntity>()
            }

            if (matchedSections.isEmpty()) return@withContext SmartSearchResult(null, emptyList())

            // Оцениваем секции: простая метрика — количество вхождений токенов
            val tokens = rawQuery
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
                .split(Regex("\\s+"))
                .mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
                .filter { it !in stopWords }

            fun scoreSection(section: MaterialSectionEntity): Int {
                val text = (section.title ?: "") + " " + section.content
                var score = 0
                val low = text.lowercase()
                for (t in tokens) {
                    if (t.length < 1) continue
                    // количество вхождений токена в тексте
                    var idx = low.indexOf(t)
                    while (idx >= 0) {
                        score += 1
                        idx = low.indexOf(t, idx + t.length)
                    }
                }
                return score
            }

            val sectionScores =
                matchedSections.map { it to scoreSection(it) }.filter { it.second > 0 }
            if (sectionScores.isEmpty()) return@withContext SmartSearchResult(null, emptyList())

            // для сниппета берём секцию с максимальным score
            val best = sectionScores.maxByOrNull { it.second }!!

            // формируем простой сниппет: обрезаем content вокруг первого вхождения токена
            val snippet = buildSnippet(best.first.content, tokens, 120)

            // собираем матчинг материалов — агрегируем по materialId, выбираем лучший сниппет/score
            val grouped = sectionScores.groupBy({ it.first.materialId }, { it })
            val matches = mutableListOf<MaterialMatch>()
            for ((materialId, secs) in grouped) {
                val bestSec = secs.maxByOrNull { it.second }!!
                val mat = try {
                    materialDao.getById(materialId)
                } catch (e: Exception) {
                    null
                }
                val title = mat?.title ?: ("Материал $materialId")
                val sn = buildSnippet(bestSec.first.content, tokens, 100)
                matches.add(
                    MaterialMatch(
                        materialId = materialId,
                        title = title,
                        snippet = sn,
                        score = bestSec.second
                    )
                )
            }

            // сортируем matches по убыванию score
            val sortedMatches = matches.sortedByDescending { it.score }

            SmartSearchResult(bestSnippet = snippet, matches = sortedMatches)
        }

    // строит сниппет вокруг первого вхождения любого из tokens
    private fun buildSnippet(text: String, tokens: List<String>, maxLen: Int): String {
        val low = text.lowercase()
        var pos = -1
        var tokenFound: String? = null
        for (t in tokens) {
            if (t.isBlank()) continue
            val idx = low.indexOf(t)
            if (idx >= 0) {
                pos = idx
                tokenFound = text.substring(idx, idx + t.length)
                break
            }
        }
        if (pos < 0) {
            // нет совпадений — вернём начало текста обрезанное
            return if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."
        }
        val start = (pos - maxLen / 3).coerceAtLeast(0)
        val end = (start + maxLen).coerceAtMost(text.length)
        var snippet = text.substring(start, end)
        // подсветка – оборачиваем найденные токены в ** **
        for (t in tokens) {
            if (t.isBlank()) continue
            snippet = snippet.replace(Regex("(?i)${Regex.escape(t)}")) { match ->
                "**${match.value}**"
            }
        }
        if (start > 0) snippet = "..." + snippet
        if (end < text.length) snippet = snippet + "..."
        return snippet
    }
}