package com.example.vendorchat.chat

import com.example.vendorchat.data.AddressIn
import com.example.vendorchat.data.BankIn
import com.example.vendorchat.data.Fa
import com.example.vendorchat.data.Geo
import com.example.vendorchat.data.Keys
import com.example.vendorchat.data.PersonIn

/** مجموعهٔ تغییرات یا درخواست‌هایی که مدل از پیام کاربر استخراج می‌کند. */
data class VendorAction(
    val vendor: String = "",                       // خالی = وندور فعلی گفتگو
    val create: Boolean = false,
    val delete: Boolean = false,
    val sets: List<Pair<String, String>> = emptyList(),
    val lists: List<Pair<String, List<String>>> = emptyList(),
    val phones: List<Triple<String, String, String>> = emptyList(),   // کلید (تلفن/موبایل)، برچسب، شماره
    val emails: List<Pair<String, String>> = emptyList(),             // برچسب، ایمیل
    val addresses: List<AddressIn> = emptyList(),
    val people: List<PersonIn> = emptyList(),
    val banks: List<BankIn> = emptyList(),
    val removes: List<Pair<String, String>> = emptyList(),
    val show: String? = null,
    val search: String? = null
) {
    val hasData: Boolean
        get() = sets.isNotEmpty() || lists.isNotEmpty() || phones.isNotEmpty() || emails.isNotEmpty() ||
            addresses.isNotEmpty() || people.isNotEmpty() || banks.isNotEmpty()

    /** آیا چیزی را در دیتابیس تغییر می‌دهد؟ (نیاز به تأیید کاربر) */
    val mutating: Boolean get() = create || delete || hasData || removes.isNotEmpty()
}

/** قالب خط‌به‌خطِ ساده بین مدل و برنامه (برای مدل کوچک مطمئن‌تر از JSON است). */
object ActionDsl {
    private val openRe = Regex("<<\\s*ACTION\\s*>{1,2}", RegexOption.IGNORE_CASE)
    private val closeRe = Regex("<<\\s*END\\s*>{1,2}", RegexOption.IGNORE_CASE)
    private const val MARKER = "<<ACTION>>"

    data class Parsed(val visible: String, val actions: List<VendorAction>)

    /** متن قابل نمایش هنگام استریم: هر چیز قبل از شروع بلوک عمل (و بدون نشانهٔ ناقص). */
    fun visible(text: String): String {
        val m = openRe.find(text)
        if (m != null) return text.substring(0, m.range.first).trimEnd()
        for (k in MARKER.length - 1 downTo 1) {
            if (text.endsWith(MARKER.take(k))) return text.dropLast(k).trimEnd()
        }
        return text
    }

    fun parse(text: String): Parsed {
        val actions = ArrayList<VendorAction>()
        val vis = StringBuilder()
        var pos = 0
        while (true) {
            val o = openRe.find(text, pos) ?: break
            vis.append(text.substring(pos, o.range.first))
            val rest = o.range.last + 1
            val c = closeRe.find(text, rest)
            val end = c?.range?.first ?: text.length
            actions.addAll(parseBlock(text.substring(rest, end)))
            pos = if (c != null) c.range.last + 1 else text.length
        }
        vis.append(text.substring(pos))
        return Parsed(vis.toString().trim(), actions)
    }

    // ---------------- پاک‌سازی مقدارها ----------------
    private val empties = setOf("", "-", "—", "–", "ندارد", "نامشخص", "none", "null", "n/a", "؟", "?")

    private fun txt(s: String): String {
        var t = s.trim().trim('"', '\'', '«', '»', '[', ']', '(', ')').trim()
        if (Fa.normalize(t) in empties.map { Fa.normalize(it) } || t in empties) t = ""
        return t.take(300)
    }

    private fun num(s: String): String = Fa.latinDigits(txt(s)).replace(" ", "").replace("-", "")

    private fun yes(s: String): Boolean {
        val n = Fa.normalize(s)
        return n == "yes" || n == "true" || n == "بله" || n == "آره" || n == "1"
    }

    private val mobileRe = Regex("^(?:\\+98|0098|98|0)?9\\d{9}$")
    private val shebaRe = Regex("^[Ii][Rr]\\d{20,24}$")
    private val emailRe = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    /** شمارهٔ ایرانی را یکدست می‌کند (+98912... → 0912...). */
    fun normPhone(raw: String): String {
        var d = num(raw)
        if (d.startsWith("+98")) d = "0" + d.removePrefix("+98")
        else if (d.startsWith("0098")) d = "0" + d.removePrefix("0098")
        else if (d.startsWith("98") && d.length == 12) d = "0" + d.removePrefix("98")
        return d
    }

    private fun isPhoneLike(s: String): Boolean {
        val d = Fa.latinDigits(s).replace(Regex("[\\s\\-+()]"), "")
        return d.length >= 5 && d.all { it.isDigit() }
    }

    private fun splitMulti(s: String): List<String> =
        s.split(Regex("[،;؛,\\n]")).map { it.trim() }.filter { it.isNotEmpty() }

    // ---------------- تجزیهٔ هر خط ----------------
    private fun parsePhone(value: String): List<Triple<String, String, String>> {
        val f = value.split("|").map { txt(it) }.filter { it.isNotEmpty() }
        if (f.isEmpty()) return emptyList()
        var label = ""
        var numbers = ""
        if (f.size == 1) {
            numbers = f[0]
        } else {
            val idx = f.indexOfFirst { isPhoneLike(it.split(Regex("[،;,]"))[0]) }
            if (idx < 0) return emptyList()
            numbers = f[idx]
            label = f.filterIndexed { i, _ -> i != idx }.joinToString(" ")
        }
        val ln = Fa.normalize(label)
        return splitMulti(numbers).filter { isPhoneLike(it) }.map { raw ->
            val p = normPhone(raw)
            val key = when {
                ln.contains("موبایل") || ln.contains("همراه") -> "موبایل"
                ln.contains("فکس") || ln.contains("fax") -> "تلفن"
                mobileRe.matches(p) -> "موبایل"
                else -> "تلفن"
            }
            val lab = if (ln.contains("موبایل") || ln == "همراه") "" else label
            Triple(key, lab, p)
        }
    }

    private fun parseEmail(value: String): List<Pair<String, String>> {
        val f = value.split("|").map { txt(it) }.filter { it.isNotEmpty() }
        val mail = f.firstNotNullOfOrNull { emailRe.find(it)?.value } ?: return emptyList()
        val label = f.filter { !it.contains("@") }.joinToString(" ")
        return listOf(label to mail)
    }

    private val kindWords = listOf(
        "کارخانه", "دفتر", "مرکزی", "انبار", "شعبه", "منزل", "فروشگاه", "نمایندگی", "کارگاه", "اصلی", "معدن", "سایت", "مرکز"
    ).map { Fa.normalize(it) }

    private fun looksLikeKind(s: String): Boolean {
        val n = Fa.normalize(s)
        return n.split(" ").size <= 3 && kindWords.any { n.contains(it) }
    }

    private fun parseAddress(value: String): AddressIn? {
        val raw = value.split("|").map { txt(it) }.filter { it.isNotEmpty() }.toMutableList()
        if (raw.isEmpty()) return null
        var lat: Double? = null
        var lng: Double? = null
        var postal: String? = null
        val it = raw.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val g = Geo.parse(f)
            if (g != null) {
                lat = g.first
                lng = g.second
                it.remove()
                continue
            }
            val d = Fa.latinDigits(f).replace(Regex("[\\s\\-]"), "")
            if (d.length == 10 && d.all { c -> c.isDigit() } && f.none { c -> c.isLetter() && c.code > 127 }) {
                postal = d
                it.remove()
            }
        }
        var kind = "اصلی"
        if (raw.isNotEmpty() && looksLikeKind(raw[0])) kind = raw.removeAt(0)
        var province: String? = null
        var city: String? = null
        var street: String? = null
        when {
            raw.size >= 3 -> {
                province = raw[0]; city = raw[1]; street = raw.drop(2).joinToString("، ")
            }
            raw.size == 2 -> {
                city = raw[0]; street = raw[1]
            }
            raw.size == 1 -> street = raw[0]
        }
        if (province == null && city == null && street == null && postal == null && lat == null) return null
        return AddressIn(kind, province, city, street, postal, lat, lng)
    }

    private fun parsePerson(value: String): PersonIn? {
        val f = value.split("|").map { txt(it) }.filter { it.isNotEmpty() }
        if (f.isEmpty()) return null
        var name: String? = null
        var role: String? = null
        var mobile: String? = null
        var phone: String? = null
        var email: String? = null
        var note: String? = null
        for (x in f) {
            val m = emailRe.find(x)
            when {
                m != null -> email = m.value
                isPhoneLike(x) -> {
                    val p = normPhone(x)
                    if (mobileRe.matches(p)) mobile = p else phone = p
                }
                name == null -> name = x
                role == null -> role = x
                note == null -> note = x
            }
        }
        val nm = name ?: return null
        return PersonIn(nm, role, mobile, phone, email, note)
    }

    private fun parseBank(value: String): BankIn? {
        val f = value.split("|").map { txt(it) }.filter { it.isNotEmpty() }
        if (f.isEmpty()) return null
        var bank: String? = null
        var branch: String? = null
        var account: String? = null
        var sheba: String? = null
        var holder: String? = null
        for (x in f) {
            val compact = Fa.latinDigits(x).replace(Regex("[\\s\\-]"), "")
            when {
                shebaRe.matches(compact) -> sheba = compact.uppercase()
                compact.length >= 10 && compact.all { it.isDigit() } -> account = compact
                bank == null -> bank = x
                branch == null -> branch = x.removePrefix("شعبه").trim().ifEmpty { x }
                holder == null -> holder = x
            }
        }
        if (bank == null && branch == null && account == null && sheba == null && holder == null) return null
        return BankIn(bank, branch, account, sheba, holder)
    }

    // ---------------- ساخت اکشن ----------------
    private class B {
        var vendor = ""
        var create = false
        var delete = false
        val sets = ArrayList<Pair<String, String>>()
        val lists = ArrayList<Pair<String, List<String>>>()
        val phones = ArrayList<Triple<String, String, String>>()
        val emails = ArrayList<Pair<String, String>>()
        val addresses = ArrayList<AddressIn>()
        val people = ArrayList<PersonIn>()
        val banks = ArrayList<BankIn>()
        val removes = ArrayList<Pair<String, String>>()
        var show: String? = null
        var search: String? = null

        fun hasContent() = vendor.isNotEmpty() || create || delete || sets.isNotEmpty() || lists.isNotEmpty() ||
            phones.isNotEmpty() || emails.isNotEmpty() || addresses.isNotEmpty() || people.isNotEmpty() ||
            banks.isNotEmpty() || removes.isNotEmpty() || show != null || search != null

        fun build(): VendorAction {
            // مسیردهی set: به ساختارهای درست
            val flat = ArrayList<Pair<String, String>>()
            val lst = ArrayList(lists)
            val ph = ArrayList(phones)
            val em = ArrayList(emails)
            val pe = ArrayList(people)
            val ba = ArrayList(banks)
            val ad = ArrayList(addresses)
            var aStreet: String? = null
            var aCity: String? = null
            var aProv: String? = null
            var aPostal: String? = null
            var aGeo: Pair<Double, Double>? = null
            var bBank: String? = null
            var bBranch: String? = null
            var bAcc: String? = null
            var bSheba: String? = null
            for ((k, v) in sets) {
                when {
                    k in Keys.PHONE_KEYS -> parsePhone(v).forEach { ph.add(it) }
                    k == "ایمیل" -> parseEmail(v).forEach { em.add(it) }
                    k in Keys.LIST_KEYS -> lst.add(k to splitMulti(v))
                    k == "آدرس" -> aStreet = v
                    k == "شهر" -> aCity = v
                    k == "استان" -> aProv = v
                    k == "کد پستی" -> aPostal = num(v)
                    k == "موقعیت" -> aGeo = Geo.parse(v)
                    k == "مسئول" -> pe.add(PersonIn(v))
                    k == "سمت" -> if (pe.isNotEmpty()) pe[pe.size - 1] = pe.last().copy(role = v)
                    k == "بانک" -> bBank = v
                    k == "شعبه" -> bBranch = v
                    k == "شماره حساب" -> bAcc = num(v)
                    k == "شبا" -> bSheba = num(v).uppercase()
                    else -> flat.add(k to v)
                }
            }
            if (aStreet != null || aCity != null || aProv != null || aPostal != null || aGeo != null) {
                ad.add(AddressIn("اصلی", aProv, aCity, aStreet, aPostal, aGeo?.first, aGeo?.second))
            }
            if (bBank != null || bBranch != null || bAcc != null || bSheba != null) {
                ba.add(BankIn(bBank, bBranch, bAcc, bSheba, null))
            }
            return VendorAction(
                vendor, create, delete, flat, lst, ph, em, ad, pe, ba, ArrayList(removes), show, search
            )
        }
    }

    private val knownKeys = setOf(
        "vendor", "new", "delete", "phone", "email", "address", "person", "bank", "set", "list",
        "remove", "show", "search"
    )

    private fun cleanVendor(v: String): String {
        val t = txt(v)
        return if (t.equals("@current", ignoreCase = true) || t == "@") "" else t.take(100)
    }

    fun parseBlock(block: String): List<VendorAction> {
        val out = ArrayList<VendorAction>()
        var b = B()
        for (lineRaw in block.lines()) {
            val line = lineRaw.trim().trimStart('-', '*', '•', ' ')
            val i = line.indexOf(':')
            if (i < 1) continue
            val key = line.substring(0, i).trim().lowercase()
            if (key !in knownKeys) continue
            val value = line.substring(i + 1).trim()
            when (key) {
                "vendor" -> {
                    if (b.hasContent()) {
                        out.add(b.build())
                        b = B()
                    }
                    b.vendor = cleanVendor(value)
                }
                "new" -> b.create = yes(value)
                "delete" -> b.delete = yes(value)
                "phone" -> b.phones.addAll(parsePhone(value))
                "email" -> b.emails.addAll(parseEmail(value))
                "address" -> parseAddress(value)?.let { b.addresses.add(it) }
                "person" -> parsePerson(value)?.let { b.people.add(it) }
                "bank" -> parseBank(value)?.let { b.banks.add(it) }
                "set" -> {
                    val sep = listOf("|", "=", ":").firstOrNull { value.contains(it) }
                    if (sep != null) {
                        val k = value.substringBefore(sep).trim()
                        val raw = txt(value.substringAfter(sep))
                        val latin = Fa.latinDigits(raw)
                        val v = if (latin.all { c -> c.isDigit() || c == ' ' || c == '-' || c == '+' }) latin else raw
                        if (k.isNotEmpty() && v.isNotEmpty()) b.sets.add(Keys.canon(k) to v)
                    }
                }
                "list" -> {
                    val parts = value.split("|", limit = 2)
                    if (parts.size == 2) {
                        val vals = splitMulti(parts[1]).map { txt(it) }.filter { it.isNotEmpty() }
                        if (vals.isNotEmpty()) b.lists.add(Keys.canon(parts[0]) to vals)
                    }
                }
                "remove" -> {
                    val parts = value.split("|", limit = 2)
                    val what = txt(parts[0])
                    if (what.isNotEmpty()) b.removes.add(what to (parts.getOrNull(1)?.let { txt(it) } ?: ""))
                }
                "show" -> b.show = cleanVendor(value).ifEmpty { "@current" }
                "search" -> b.search = txt(value).ifEmpty { null }
            }
        }
        if (b.hasContent()) out.add(b.build())
        return out
    }

    private val phoneRun = Regex("\\+?\\d[\\d\\-\\s]{5,}\\d")

    /** تشخیص ساده بدون مدل (تلفن، موبایل، ایمیل، مختصات) وقتی مدل در دسترس نیست. */
    fun guess(raw: String): VendorAction? {
        val s = Fa.latinDigits(raw)
        val n = Fa.normalize(raw)
        if (n.contains("کد پستی") || n.contains("شناسه") || n.contains("حساب") || n.contains("شبا") || n.contains("ثبت")) return null
        val emails = emailRe.findAll(s).map { "" to it.value }.toList()
        val phones = ArrayList<Triple<String, String, String>>()
        for (m in phoneRun.findAll(s)) {
            val d = normPhone(m.value)
            if (d.length in 8..13) phones.add(Triple(if (mobileRe.matches(d)) "موبایل" else "تلفن", "", d))
        }
        val geo = Geo.parse(raw)
        val addr = if (geo != null) listOf(AddressIn("اصلی", lat = geo.first, lng = geo.second)) else emptyList()
        if (emails.isEmpty() && phones.isEmpty() && addr.isEmpty()) return null
        return VendorAction(phones = phones, emails = emails, addresses = addr)
    }

    // ---------------- توضیح برای کارت تأیید ----------------
    private fun mask(s: String): String = if (s.length > 4) "••••" + s.takeLast(4) else "••••"

    fun describe(a: VendorAction): List<String> {
        val l = ArrayList<String>()
        if (a.delete) l.add("همهٔ اطلاعات این وندور پاک می‌شود و قابل بازگشت نیست (ولی با «برگردان» می‌شود برگرداند).")
        for ((k, v) in a.sets) l.add("$k: ${if (Keys.isSecret(k)) mask(v) else v}")
        for ((k, v) in a.lists) l.add("$k: ${v.joinToString("، ")}")
        for ((k, lab, v) in a.phones) {
            val title = if (lab.isNotEmpty()) "$k ($lab)" else k
            l.add("$title: $v")
        }
        for ((lab, v) in a.emails) l.add("ایمیل" + (if (lab.isNotEmpty()) " ($lab)" else "") + ": $v")
        for (x in a.addresses) {
            val place = listOfNotNull(x.province, x.city, x.street).joinToString("، ")
            val extra = buildString {
                if (x.postal != null) append(" | کد پستی ${x.postal}")
                if (x.lat != null && x.lng != null) append(" | 📍 موقعیت ثبت می‌شود")
            }
            l.add("آدرس ${x.kind}: $place$extra")
        }
        for (p in a.people) {
            val bits = listOfNotNull(p.role, p.mobile, p.phone, p.email)
            l.add("پرسنل: ${p.name}" + if (bits.isNotEmpty()) " — " + bits.joinToString(" • ") else "")
        }
        for (b in a.banks) {
            val bits = ArrayList<String>()
            b.bank?.let { bits.add(it) }
            b.branch?.let { bits.add("شعبه $it") }
            b.account?.let { bits.add("حساب ${mask(it)}") }
            b.sheba?.let { bits.add("شبا ${mask(it)}") }
            b.holder?.let { bits.add("صاحب حساب $it") }
            l.add("حساب بانکی: " + bits.joinToString(" • ") + " (رمزگذاری می‌شود)")
        }
        for ((w, d) in a.removes) l.add("حذف: $w" + if (d.isNotEmpty()) " ($d)" else "")
        if (l.isEmpty() && a.create) l.add("فقط نام وندور ثبت می‌شود؛ اطلاعاتش را بعداً با چت اضافه کن.")
        return l
    }

    private val claims = listOf(
        "ثبت کردم", "ثبتش کردم", "ثبت شد", "ذخیره کردم", "ذخیره شد", "حذف کردم", "حذف شد", "اضافه کردم",
        "اضافه شد", "به روز کردم", "به روز شد", "ثبتشد", "ذخیرش کردم", "اضافش کردم", "پاک کردم", "پاک شد"
    ).map { Fa.normalize(it) }

    /** آیا مدل ادعا کرده چیزی را ذخیره/حذف کرده؟ (باید فقط برنامه این را بگوید) */
    fun claimsSaved(text: String): Boolean {
        val n = Fa.normalize(text)
        return claims.any { n.contains(it) }
    }
}
