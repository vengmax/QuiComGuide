package com.wllcom.quicomguide.data.ml

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer

class Tokenizer(context: Context, tokenizerAssetPath: String) {

    // ----- Trie Node -----
    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var id: Int = -1
    }

    // ----- Data -----
    private val rootNode = TrieNode()
    private val idToToken = HashMap<Int, String>()
    val tokenToId = HashMap<String, Int>()

    private var unkId = 0
    private var bosId = 0
    private var eosId = 2
    private var padId = 1
    private var metaspace = "▁"
    private var addPrefixSpace = true

    init {
        val json = context.assets.open(tokenizerAssetPath).use {
            BufferedReader(InputStreamReader(it)).readText()
        }
        val root = JSONObject(json)

        // --- PreTokenizer (Metaspace) ---
        root.optJSONObject("pre_tokenizer")?.let { pre ->
            fun processPre(preObj: JSONObject) {
                val type = preObj.optString("type")
                if (type == "Metaspace") {
                    metaspace = preObj.optString("replacement", metaspace)
                    addPrefixSpace = preObj.optBoolean("add_prefix_space", addPrefixSpace)
                } else if (type == "Sequence") {
                    val arr = preObj.optJSONArray("pretokenizers")
                    if (arr != null) {
                        for (k in 0 until arr.length()) {
                            processPre(arr.getJSONObject(k))
                        }
                    }
                }
            }
            processPre(pre)
        }

        // --- Model ---
        val model = root.getJSONObject("model")
        unkId = model.optInt("unk_id", 3)
        val vocab = model.getJSONArray("vocab")
        for (i in 0 until vocab.length()) {
            val entry = vocab.getJSONArray(i)
            val tok = entry.getString(0)
            addToken(tok, i)
        }

        // --- Added tokens ---
        root.optJSONArray("added_tokens")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getInt("id")
                val tok = obj.getString("content")
                addToken(tok, id)
                when (tok) {
                    "<pad>" -> padId = id
                    "<s>" -> bosId = id
                    "</s>" -> eosId = id
                    "<unk>" -> unkId = id
                }
            }
        }
    }

    // ----- Normalizer -----
    private fun normalizeText(text: String): String {
        var s = try {
            Normalizer.normalize(text, Normalizer.Form.NFKC)
        } catch (_: Exception) {
            text
        }

        val replacements = listOf(
            "\u00A0" to " ",
            "\u200B" to "",
            "\uFEFF" to "",
            "\u2018" to "'",
            "\u2019" to "'",
            "\u201C" to "\"",
            "\u201D" to "\"",
            "\u2013" to "-",
            "\u2014" to "-",
            "\u2212" to "-",
            "\u00AD" to "",
            "\u2060" to ""
        )

        for ((k, v) in replacements) {
            if (s.contains(k)) s = s.replace(k, v)
        }

        s = s.replace(Regex(" {2,}"), " ")
        if (addPrefixSpace && !s.startsWith(" ")) s = " $s"
        return s
    }

    private fun applyMetaspace(text: String): String {
        return normalizeText(text).replace(" ", metaspace)
    }

    // ----- Trie -----
    private fun addToken(token: String, id: Int) {
        tokenToId[token] = id
        idToToken[id] = token
        var node = rootNode
        for (ch in token) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        node.id = id
    }

    // ----- Greedy longest-match tokenization -----
    private fun unigramGreedy(text: String): IntArray {
        val n = text.length
        if (n == 0) return intArrayOf()

        val ids = ArrayList<Int>()
        var i = 0
        while (i < n) {
            var node = rootNode
            var lastMatchId = -1
            var lastMatchLen = 0
            var j = i

            while (j < n) {
                val ch = text[j]
                val next = node.children[ch] ?: break
                node = next
                if (node.id >= 0) {
                    lastMatchId = node.id
                    lastMatchLen = j - i + 1
                }
                j++
            }

            if (lastMatchId >= 0) {
                ids.add(lastMatchId)
                i += lastMatchLen
            } else {
                ids.add(unkId)
                i += 1
            }
        }

        return ids.toIntArray()
    }

    // ----- Public API -----
    fun tokenize(text: String): List<String> {
        val norm = applyMetaspace(text)
        val ids = unigramGreedy(norm)
        return ids.map { idToToken[it] ?: "<unk>" }
    }

    fun encode(text: String, addSpecialTokens: Boolean = true): List<Int> {
        val norm = applyMetaspace(text)
        val ids = unigramGreedy(norm).toMutableList()
        val out = ArrayList<Int>()
        if (addSpecialTokens) out.add(bosId)
        out.addAll(ids)
        if (addSpecialTokens) out.add(eosId)
        return out
    }

    fun decode(ids: List<Int>): String {
        val sb = StringBuilder()
        for (id in ids) {
            val tok = idToToken[id] ?: continue
            if (tok.startsWith("<") && tok.endsWith(">")) continue
            sb.append(tok)
        }
        val text = sb.toString().replace(metaspace, " ")
        return if (addPrefixSpace && text.startsWith(" ")) text.substring(1) else text
    }
}