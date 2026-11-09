package com.example.vendorchat.data

data class ImportRow(val name: String, val fields: List<Pair<String, String>>)
data class ImportPlan(val rows: List<ImportRow>, val columns: List<String>, val skipped: Int)

/** تبدیل جدول اکسل به وندورها: ردیف اول عنوان ستون‌هاست و هر ردیف بعدی یک وندور. */
object Importer {
    private val nameHeaders = listOf(
        "نام", "نام وندور", "وندور", "نام شرکت", "شرکت", "نام تامین کننده", "تامین کننده",
        "name", "vendor", "vendor name", "company", "supplier"
    ).map { Fa.normalize(it) }.toSet()

    private const val MAX_ROWS = 5000

    fun fixValue(key: String, v: String): String =
        if ((key == "تلفن" || key == "موبایل") && v.length == 10 && v.all { it.isDigit() }) "0$v" else v

    private fun values(key: String, raw: String): List<Pair<String, String>> {
        val parts = if (key in Keys.MULTI) {
            raw.split(Regex("[،;؛\\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        } else listOf(raw)
        return parts.map { key to fixValue(key, Fa.latinDigits(it).trim()) }
    }

    fun plan(table: List<List<String>>): ImportPlan? {
        val rows = table.filter { r -> r.any { it.isNotBlank() } }
        if (rows.size < 2) return null
        val header = rows[0].map { it.trim() }
        var nameCol = header.indexOfFirst { Fa.normalize(it) in nameHeaders }
        if (nameCol < 0) nameCol = 0
        val cols = ArrayList<Pair<Int, String>>()
        header.forEachIndexed { i, h ->
            if (i != nameCol && h.isNotEmpty()) cols.add(i to Keys.canon(h))
        }
        val out = ArrayList<ImportRow>()
        var skipped = 0
        for (r in rows.drop(1).take(MAX_ROWS)) {
            val name = r.getOrNull(nameCol)?.trim().orEmpty()
            if (name.isEmpty()) {
                skipped++
                continue
            }
            val fields = ArrayList<Pair<String, String>>()
            for ((i, k) in cols) {
                val raw = r.getOrNull(i)?.trim().orEmpty()
                if (raw.isNotEmpty()) fields.addAll(values(k, raw))
            }
            out.add(ImportRow(name.take(100), fields))
        }
        if (out.isEmpty()) return null
        return ImportPlan(out, cols.map { it.second }.distinct(), skipped)
    }
}
