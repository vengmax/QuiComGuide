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
    val title: String,
    val xmlRaw: String,
    val sections: List<ParsedSection>
)

object XmlMaterialParser {

    private val allowedElementNames = setOf("content", "example")
    private val inlineTagsToStrip = setOf("formula", "inline-code", "code")

    fun parse(xml: String): ParsedMaterial {
        return parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    fun parse(input: InputStream): ParsedMaterial {
        val rawXml = input.readBytes().toString(StandardCharsets.UTF_8)
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(rawXml.reader())

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
                            "title" -> materialTitle = readMixedTextAllowingInline(parser, "title")
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

        return ParsedMaterial(materialTitle.trim(), rawXml, sections)
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
                        sb.append(readInnerTextRecursively(parser, name))
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
            }
        }
    }
}

