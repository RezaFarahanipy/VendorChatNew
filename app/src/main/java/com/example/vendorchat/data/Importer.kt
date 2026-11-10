package com.example.vendorchat.data

import com.example.vendorchat.chat.ActionDsl
import com.example.vendorchat.chat.VendorAction

data class ImportRow(val name: String, val action: VendorAction)
data class ImportPlan(val rows: List<ImportRow>, val columns: List<String>, val skipped: Int)

/**
 * تبدیل جدول اکسل به وندورها. ردیف اول عنوان ستون‌هاست.
 * عنوان‌ها خودکار تشخیص داده می‌شوند (جزئیات در راهنمای قالب اکسل).
 */
object Importer {
    private val nameHeaders = listOf(
        "نام", "نام وندور", "وندور", "نام شرکت", "شرکت", "نام تامین کننده", "تامین کننده",
        "name", "vendor", "vendor name", "company", "supplier"
    ).map { Fa.normalize(it) }.toSet()

    private val personWords = listOf("مسئول", "پرسنل", "شخص", "رابط", "نماینده", "کارشناس", "مدیر").map { Fa.normalize(it) }
    private val streetWords = listOf("آدرس", "نشانی", "ادرس")
    private val cityWords = listOf("شهر")
    private val provWords = listOf("استان")
    private val postalWords = listOf("کد پستی", "کدپستی")
    private val geoWords = listOf("موقعیت", "لوکیشن", "مختصات", "نقشه")
    private val addrAll = (streetWords + cityWords + provWords + postalWords + geoWords)
        .map { Fa.normalize(it) }.sortedByDescending { it.length }

    private const val MAX_ROWS = 5000

    private sealed class Col {
        object Name : Col()
        data class Flat(val key: String) : Col()
        data class Lst(val key: String) : Col()
        data class Phone(val key: String, val label: String) : Col()
        data class Email(val label: String) : Col()
        data class Addr(val kind: String, val field: String) : Col()
        data class Pers(val group: Int, val field: String) : Col()
        data class Bnk(val group: Int, val field: String) : Col()
    }

    private val trailNum = Regex("\\s*(\\d+)$")

    private fun has(n: String, words: List<String>) = words.any { n.contains(Fa.normalize(it)) }

    private fun describe(c: Col): String = when (c) {
        is Col.Name -> "نام"
        is Col.Flat -> c.key
        is Col.Lst -> c.key
        is Col.Phone -> if (c.label.isEmpty()) c.key else "${c.key} ${c.label}"
        is Col.Email -> if (c.label.isEmpty()) "ایمیل" else "ایمیل ${c.label}"
        is Col.Addr -> "آدرس ${c.kind}"
        is Col.Pers -> "پرسنل ${c.group}"
        is Col.Bnk -> "حساب بانکی ${c.group}"
    }

    private fun classify(header: String): Col? {
        val n = Fa.normalize(header)
        if (n.isEmpty()) return null
        if (n in nameHeaders) return Col.Name
        val canon = Keys.canon(header)
        if (canon == "وبسایت") return Col.Flat("وبسایت")
        if (canon == "ایمیل") return Col.Email("")

        val numMatch = trailNum.find(n)
        val group = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val base = if (numMatch != null) n.removeSuffix(numMatch.value).trim() else n

        // ۱) پرسنل
        if (personWords.any { base.contains(it) }) {
            val field = when {
                base.contains("سمت") -> "role"
                base.contains("موبایل") || base.contains("همراه") -> "mobile"
                base.contains("تلفن") -> "phone"
                base.contains("ایمیل") -> "email"
                else -> "name"
            }
            return Col.Pers(group, field)
        }
        // ۲) آدرس (قبل از بانک، چون «آدرس شعبه ...» آدرس است)
        if (addrAll.any { base.contains(it) }) {
            val field = when {
                has(base, postalWords) -> "postal"
                has(base, geoWords) -> "geo"
                has(base, provWords) -> "province"
                has(base, cityWords) -> "city"
                else -> "street"
            }
            var kind = base
            for (w in addrAll) kind = kind.replace(w, " ")
            kind = kind.replace(Regex("\\s+"), " ").trim().ifEmpty { "اصلی" }
            return Col.Addr(kind, field)
        }
        // ۳) بانک
        if (base.contains("بانک") || base.contains("شبا") || base.contains("حساب") || base.contains("شعبه")) {
            val field = when {
                base.contains("شبا") -> "sheba"
                base.contains("صاحب") -> "holder"
                base.contains("حساب") -> "account"
                base.contains("شعبه") -> "branch"
                else -> "bank"
            }
            return Col.Bnk(group, field)
        }
        // ۴) تلفن و موبایل و ایمیل با برچسب
        val first = base.split(" ").firstOrNull().orEmpty()
        val rest = base.split(" ").drop(1).joinToString(" ")
        when (first) {
            "تلفن" -> return if (rest.startsWith("همراه")) Col.Phone("موبایل", rest.removePrefix("همراه").trim())
            else Col.Phone("تلفن", rest)
            "موبایل", "همراه", "واتساپ" -> return Col.Phone("موبایل", rest)
            "فکس" -> return Col.Phone("تلفن", ("فکس " + rest).trim())
            "ایمیل" -> return Col.Email(rest)
        }
        if (canon in Keys.LIST_KEYS) return Col.Lst(canon)
        if (canon in Keys.PHONE_KEYS) return Col.Phone(canon, "")
        return Col.Flat(canon)
    }

    private val labelSuffix = Regex("^(.*?)\\s*\\(([^)]*)\\)\\s*$")
    private val splitRe = Regex("[،;؛\\n]")

    private fun fixPhone(raw: String): String {
        val p = ActionDsl.normPhone(raw)
        return if (p.length == 10 && p.all { it.isDigit() } && !p.startsWith("0")) "0$p" else p
    }

    private class Acc {
        val sets = ArrayList<Pair<String, String>>()
        val lists = ArrayList<Pair<String, List<String>>>()
        val phones = ArrayList<Triple<String, String, String>>()
        val emails = ArrayList<Pair<String, String>>()
        val addr = LinkedHashMap<String, HashMap<String, String>>()
        val pers = java.util.TreeMap<Int, HashMap<String, String>>()
        val bank = java.util.TreeMap<Int, HashMap<String, String>>()
    }

    fun plan(table: List<List<String>>): ImportPlan? {
        val rows = table.filter { r -> r.any { it.isNotBlank() } }
        if (rows.size < 2) return null
        val header = rows[0].map { it.trim() }
        val cols = header.map { classify(it) }
        var nameCol = cols.indexOfFirst { it is Col.Name }
        if (nameCol < 0) nameCol = 0
        val colLabels = LinkedHashSet<String>()
        cols.forEachIndexed { i, c -> if (c != null && i != nameCol) colLabels.add(describe(c)) }

        val out = ArrayList<ImportRow>()
        var skipped = 0
        for (r in rows.drop(1).take(MAX_ROWS)) {
            val name = r.getOrNull(nameCol)?.trim().orEmpty()
            if (name.isEmpty()) {
                skipped++
                continue
            }
            val acc = Acc()
            cols.forEachIndexed { i, c ->
                if (c == null || i == nameCol || c is Col.Name) return@forEachIndexed
                val raw = r.getOrNull(i)?.trim().orEmpty()
                if (raw.isEmpty()) return@forEachIndexed
                when (c) {
                    is Col.Flat -> {
                        val v = if (c.key in setOf("شناسه ملی", "شماره ثبت", "کد اقتصادی")) Fa.latinDigits(raw) else raw
                        acc.sets.add(c.key to v)
                    }
                    is Col.Lst -> acc.lists.add(c.key to raw.split(splitRe).map { it.trim() }.filter { it.isNotEmpty() })
                    is Col.Phone -> for (item in raw.split(splitRe).map { it.trim() }.filter { it.isNotEmpty() }) {
                        val m = labelSuffix.find(item)
                        val numPart = m?.groupValues?.get(1) ?: item
                        val lab = m?.groupValues?.get(2)?.trim().takeUnless { it.isNullOrEmpty() } ?: c.label
                        val p = fixPhone(numPart)
                        if (p.length >= 5) {
                            val key = if (c.key == "تلفن" && Regex("^09\\d{9}$").matches(p) && lab.isEmpty()) "موبایل" else c.key
                            acc.phones.add(Triple(key, lab, p))
                        }
                    }
                    is Col.Email -> for (item in raw.split(splitRe).map { it.trim() }.filter { it.isNotEmpty() }) {
                        val m = labelSuffix.find(item)
                        val mail = m?.groupValues?.get(1) ?: item
                        val lab = m?.groupValues?.get(2)?.trim().takeUnless { it.isNullOrEmpty() } ?: c.label
                        if (mail.contains("@")) acc.emails.add(lab to mail.trim())
                    }
                    is Col.Addr -> acc.addr.getOrPut(c.kind) { HashMap() }[c.field] = raw
                    is Col.Pers -> acc.pers.getOrPut(c.group) { HashMap() }[c.field] = raw
                    is Col.Bnk -> acc.bank.getOrPut(c.group) { HashMap() }[c.field] = raw
                    is Col.Name -> {}
                }
            }
            val addresses = acc.addr.mapNotNull { (kind, f) ->
                val g = f["geo"]?.let { Geo.parse(it) }
                val postal = f["postal"]?.let { Fa.latinDigits(it).replace(Regex("[\\s\\-]"), "") }
                if (f["street"] == null && f["city"] == null && f["province"] == null && postal == null && g == null) null
                else AddressIn(kind, f["province"], f["city"], f["street"], postal, g?.first, g?.second)
            }
            val people = acc.pers.values.mapNotNull { f ->
                val nm = f["name"] ?: return@mapNotNull null
                val mob = f["mobile"]?.let { fixPhone(it) }
                val ph = f["phone"]?.let { fixPhone(it) }
                PersonIn(nm, f["role"], mob, ph, f["email"])
            }
            val banks = acc.bank.values.mapNotNull { f ->
                val account = f["account"]?.let { Fa.latinDigits(it).replace(Regex("[\\s\\-]"), "") }
                val sheba = f["sheba"]?.let { Fa.latinDigits(it).replace(Regex("[\\s\\-]"), "").uppercase() }
                if (f["bank"] == null && f["branch"] == null && account == null && sheba == null && f["holder"] == null) null
                else BankIn(f["bank"], f["branch"]?.removePrefix("شعبه")?.trim()?.ifEmpty { f["branch"] }, account, sheba, f["holder"])
            }
            out.add(
                ImportRow(
                    name.take(100),
                    VendorAction(
                        vendor = name.take(100), sets = acc.sets, lists = acc.lists, phones = acc.phones,
                        emails = acc.emails, addresses = addresses, people = people, banks = banks
                    )
                )
            )
        }
        if (out.isEmpty()) return null
        return ImportPlan(out, colLabels.toList(), skipped)
    }
}
