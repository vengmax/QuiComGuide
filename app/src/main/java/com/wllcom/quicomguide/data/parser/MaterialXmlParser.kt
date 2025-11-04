package com.wllcom.quicomguide.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class ParsedSection(
    val title: String,
    val orderIndex: Int,
    val elements: List<Pair<String, String>> // (type, content)
)

data class ParsedMaterial(
//    val title: String,
    val xmlRaw: String,
    val sections: List<ParsedSection>
)

object XmlMaterialParser {

    private val allowedElementNames = setOf("content", "example")
    private val inlineTagsToStrip = setOf("code", "inline-code", "tex", "inline-tex", "table", "br")

    private var toRemoveInlineTags: Boolean = false

    fun parse(xml: String, removeInlineTags: Boolean = false): ParsedMaterial? {
        return parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), removeInlineTags)
    }

    fun parse(input: InputStream, removeInlineTags: Boolean = false): ParsedMaterial? {

        toRemoveInlineTags = removeInlineTags

        val rawXml = input.readBytes().toString(StandardCharsets.UTF_8)
        val safeRawXml = wrapCodeBlocksWithCData(rawXml)

        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(safeRawXml.reader())

            var eventType = parser.eventType
            var materialTitle = ""
            val sections = mutableListOf<ParsedSection>()
            var sectionCounter = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "material") {
                    eventType = parser.next()
                    while (!(eventType == XmlPullParser.END_TAG && parser.name == "material")) {
                        if (eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> materialTitle =
                                    readMixedTextAllowingInline(parser, "title")

                                "section" -> {
                                    val sec = parseSection(parser, sectionCounter)
                                    sections.add(sec)
                                    sectionCounter++
                                }

                                else -> skipTag(parser)
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                eventType = parser.next()
            }

            return ParsedMaterial(/*materialTitle.trim(),*/ rawXml, sections)
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseSection(parser: XmlPullParser, orderIndex: Int): ParsedSection {
        var title = ""
        val elements = mutableListOf<Pair<String, String>>()
        var eventType = parser.next()

        while (!(eventType == XmlPullParser.END_TAG && parser.name == "section")) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name
                when (name) {
                    "title" -> title = readMixedTextAllowingInline(parser, "title")
                    in allowedElementNames -> {
                        val text = readMixedTextAllowingInline(parser, name)
                        elements.add(name to text)
                    }

                    else -> skipTag(parser)
                }
            }
            eventType = parser.next()
        }

        return ParsedSection(title.trim(), orderIndex, elements)
    }

    private fun readMixedTextAllowingInline(parser: XmlPullParser, expectedTag: String): String {
        val sb = StringBuilder()
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == expectedTag)) {
            when (event) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name in inlineTagsToStrip) {
                        sb.append(
                            if (name == "table") {
                                if (toRemoveInlineTags) {
                                    readMixedTextAllowingInline(parser, name)
                                } else {
                                    "<$name${getAttributesString(parser)}>" +
                                            readMixedTextAllowingInline(parser, name) +
                                            "</$name>"
                                }
                            } else {
                                if (toRemoveInlineTags) {
                                    readInnerTextRecursively(parser, name)
                                } else {
                                    "<$name${getAttributesString(parser)}>" +
                                            readInnerTextRecursively(parser, name) +
                                            "</$name>"
                                }
                            }

                        )
                    } else skipTag(parser)
                }
            }
            event = parser.next()
        }
        return sb.toString().trim()
    }

    private fun readInnerTextRecursively(parser: XmlPullParser, expectedTag: String): String {
        val sb = StringBuilder()
        var evt = parser.next()
        while (!(evt == XmlPullParser.END_TAG && parser.name == expectedTag)) {
            when (evt) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> sb.append(readInnerTextRecursively(parser, parser.name))
            }
            evt = parser.next()
        }
        return sb.toString()
    }

    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.TEXT, XmlPullParser.CDSECT,
                XmlPullParser.ENTITY_REF, XmlPullParser.COMMENT,
                XmlPullParser.IGNORABLE_WHITESPACE -> {

                }
            }
        }
    }

    fun wrapCodeBlocksWithCData(rawXml: String): String {
        var result = rawXml

        val codeRegex = Regex(
            """<code\b([^>]*)>(.*?)</code>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        result = codeRegex.replace(result) { m ->
            val attrs = m.groups[1]?.value ?: ""
            val inner = m.groups[2]?.value ?: ""
            val safeInner = inner.replace("]]>", "]]]]><![CDATA[>")
            "<code$attrs><![CDATA[$safeInner]]></code>"
        }

        val inlineRegex = Regex(
            """<inline-code\b([^>]*)>(.*?)</inline-code>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        result = inlineRegex.replace(result) { m ->
            val attrs = m.groups[1]?.value ?: ""
            val inner = m.groups[2]?.value ?: ""
            val safeInner = inner.replace("]]>", "]]]]><![CDATA[>")
            "<inline-code$attrs><![CDATA[$safeInner]]></inline-code>"
        }

        val texRegex = Regex(
            """<tex\b([^>]*)>(.*?)</tex>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        result = texRegex.replace(result) { m ->
            val attrs = m.groups[1]?.value ?: ""
            val inner = m.groups[2]?.value ?: ""
            val safeInner = inner.replace("]]>", "]]]]><![CDATA[>")
            "<tex$attrs><![CDATA[$safeInner]]></tex>"
        }

        val inlineTexRegex = Regex(
            """<inline-tex\b([^>]*)>(.*?)</inline-tex>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        result = inlineTexRegex.replace(result) { m ->
            val attrs = m.groups[1]?.value ?: ""
            val inner = m.groups[2]?.value ?: ""
            val safeInner = inner.replace("]]>", "]]]]><![CDATA[>")
            "<inline-tex$attrs><![CDATA[$safeInner]]></inline-tex>"
        }

        return result
    }

    private fun getAttributesString(parser: XmlPullParser): String {
        val sb = StringBuilder()
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            sb.append(" $name=\"$value\"")
        }
        return sb.toString()
    }
}

