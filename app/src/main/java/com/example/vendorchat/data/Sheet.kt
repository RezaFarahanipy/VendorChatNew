package com.example.vendorchat.data

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream

/** خواندن فایل‌های اکسل (xlsx) و CSV بدون هیچ کتابخانهٔ خارجی. خروجی: جدول رشته‌ها. */
object Sheet {
    fun read(bytes: ByteArray): List<List<String>> {
        val isZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        return if (isZip) xlsx(bytes) else csv(bytes)
    }

    // ---------------- xlsx ----------------
    private val siRe = Regex("<si\\b[^>]*?(?:/>|>(.*?)</si>)", RegexOption.DOT_MATCHES_ALL)
    private val tRe = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val rphRe = Regex("<rPh\\b[^>]*>.*?</rPh>", RegexOption.DOT_MATCHES_ALL)
    private val rowRe = Regex("<row\\b[^>]*?(?:/>|>(.*?)</row>)", RegexOption.DOT_MATCHES_ALL)
    private val cellRe = Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val rAttr = Regex("\\sr=\"([A-Za-z]+)\\d+\"")
    private val tAttr = Regex("\\st=\"(\\w+)\"")
    private val vRe = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
    private val isRe = Regex("<is>(.*?)</is>", RegexOption.DOT_MATCHES_ALL)

    fun xlsx(bytes: ByteArray): List<List<String>> {
        var shared: String? = null
        val sheets = sortedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val name = e.name
                if (name == "xl/sharedStrings.xml" || (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))) {
                    val text = String(zin.readBytes(), Charsets.UTF_8)
                    if (name == "xl/sharedStrings.xml") shared = text else sheets[name] = text
                }
                e = zin.nextEntry
            }
        }
        val sst = parseShared(shared)
        val xml = sheets["xl/worksheets/sheet1.xml"] ?: sheets.values.firstOrNull() ?: return emptyList()
        return parseSheet(xml, sst)
    }

    private fun parseShared(xml: String?): List<String> {
        if (xml == null) return emptyList()
        return siRe.findAll(xml).map { m ->
            val inner = rphRe.replace(m.groupValues[1], "")
            tRe.findAll(inner).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()
    }

    private fun colIndex(letters: String): Int {
        var n = 0
        for (c in letters.uppercase()) n = n * 26 + (c - 'A' + 1)
        return n - 1
    }

    private fun number(v: String?): String {
        if (v == null) return ""
        val t = v.trim()
        return try {
            val bd = BigDecimal(t).stripTrailingZeros()
            if (bd.scale() <= 0) bd.toBigInteger().toString() else bd.toPlainString()
        } catch (e: Exception) {
            t
        }
    }

    private fun parseSheet(xml: String, sst: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        for (rm in rowRe.findAll(xml)) {
            val body = rm.groupValues[1]
            val cells = ArrayList<String>()
            var pos = 0
            for (cm in cellRe.findAll(body)) {
                val attrs = cm.groupValues[1]
                val inner = cm.groupValues[2]
                val idx = rAttr.find(attrs)?.groupValues?.get(1)?.let { colIndex(it) } ?: pos
                val t = tAttr.find(attrs)?.groupValues?.get(1)
                val v = vRe.find(inner)?.groupValues?.get(1)
                val text = when (t) {
                    "s" -> sst.getOrNull(v?.trim()?.toIntOrNull() ?: -1) ?: ""
                    "inlineStr" -> isRe.find(inner)?.let { x ->
                        tRe.findAll(x.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
                    } ?: ""
                    "str" -> unescape(v ?: "")
                    "e" -> ""
                    "b" -> if (v?.trim() == "1") "TRUE" else "FALSE"
                    else -> number(v)
                }
                while (cells.size < idx) cells.add("")
                if (cells.size == idx) cells.add(text) else cells[idx] = text
                pos = idx + 1
            }
            rows.add(cells)
        }
        return rows
    }

    private val numEnt = Regex("&#(\\d+);")
    private val hexEnt = Regex("&#x([0-9a-fA-F]+);")
    private val xEsc = Regex("_x([0-9A-Fa-f]{4})_")

    private fun unescape(s: String): String {
        if (s.indexOf('&') < 0 && s.indexOf('_') < 0) return s
        return s
            .replace(numEnt) { it.groupValues[1].toInt().toChar().toString() }
            .replace(hexEnt) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace(xEsc) { it.groupValues[1].toInt(16).toChar().toString() }
    }

    // ---------------- csv ----------------
    fun csv(bytes: ByteArray): List<List<String>> {
        var text = String(bytes, Charsets.UTF_8)
        if (text.contains('\uFFFD')) text = String(bytes, charset("windows-1256"))
        text = text.removePrefix("\uFEFF")
        val first = text.lineSequence().firstOrNull().orEmpty()
        val delim = listOf(',', ';', '\t')
            .maxByOrNull { d -> first.count { it == d } }
            ?.takeIf { d -> first.contains(d) } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else inQ = false
                } else sb.append(c)
            } else {
                when (c) {
                    '"' -> if (sb.isEmpty()) inQ = true else sb.append(c)
                    delim -> {
                        row.add(sb.toString())
                        sb.setLength(0)
                    }
                    '\n' -> {
                        row.add(sb.toString())
                        sb.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                    }
                    '\r' -> {}
                    else -> sb.append(c)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows
    }
}
