package com.example.vendorchat.data

import com.example.vendorchat.chat.MiniJson
import com.example.vendorchat.chat.VendorAction
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** پشتیبان رمزدار + خروجی اکسل. */
object Backup {
    private val MAGIC = "VCB1".toByteArray(Charsets.US_ASCII)
    private const val ITER = 120_000

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> {}
                c == '\t' -> sb.append("\\t")
                c.code < 32 -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun q(s: String?): String = "\"" + esc(s ?: "") + "\""

    fun toJson(vendors: List<VendorData>): String = buildString {
        append("{\"app\":\"VendorChat\",\"v\":2,\"vendors\":[")
        vendors.forEachIndexed { i, v ->
            if (i > 0) append(',')
            append("{\"name\":").append(q(v.name)).append(",\"attrs\":[")
            v.attrs.forEachIndexed { j, a ->
                if (j > 0) append(',')
                append("{\"k\":").append(q(a.key)).append(",\"v\":").append(q(a.value))
                    .append(",\"s\":").append(q(if (a.secret) "1" else "0"))
                    .append(",\"l\":").append(q(a.label)).append("}")
            }
            append("],\"addresses\":[")
            v.addresses.forEachIndexed { j, a ->
                if (j > 0) append(',')
                append("{\"kind\":").append(q(a.kind)).append(",\"province\":").append(q(a.province))
                    .append(",\"city\":").append(q(a.city)).append(",\"street\":").append(q(a.street))
                    .append(",\"postal\":").append(q(a.postal))
                    .append(",\"lat\":").append(q(a.lat?.toString())).append(",\"lng\":").append(q(a.lng?.toString())).append("}")
            }
            append("],\"people\":[")
            v.people.forEachIndexed { j, p ->
                if (j > 0) append(',')
                append("{\"name\":").append(q(p.name)).append(",\"role\":").append(q(p.role))
                    .append(",\"mobile\":").append(q(p.mobile)).append(",\"phone\":").append(q(p.phone))
                    .append(",\"email\":").append(q(p.email)).append(",\"note\":").append(q(p.note)).append("}")
            }
            append("],\"banks\":[")
            v.banks.forEachIndexed { j, b ->
                if (j > 0) append(',')
                append("{\"bank\":").append(q(b.bank)).append(",\"branch\":").append(q(b.branch))
                    .append(",\"account\":").append(q(b.account)).append(",\"sheba\":").append(q(b.sheba))
                    .append(",\"holder\":").append(q(b.holder)).append("}")
            }
            append("]}")
        }
        append("]}")
    }

    private fun s(m: Map<*, *>, k: String): String? = (m[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    fun fromJson(json: String): List<VendorData>? {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return null
        val list = m["vendors"] as? List<*> ?: return null
        val out = ArrayList<VendorData>()
        for (item in list) {
            val vm = item as? Map<*, *> ?: continue
            val name = s(vm, "name") ?: continue
            val attrs = ArrayList<Attr>()
            (vm["attrs"] as? List<*>)?.forEach { a ->
                val am = a as? Map<*, *> ?: return@forEach
                val k = am["k"] as? String ?: return@forEach
                val v = am["v"] as? String ?: return@forEach
                attrs.add(Attr(k, v, (am["s"] as? String) == "1", s(am, "l")))
            }
            val addrs = (vm["addresses"] as? List<*>)?.mapNotNull { a ->
                val am = a as? Map<*, *> ?: return@mapNotNull null
                AddressData(
                    0, s(am, "kind") ?: "اصلی", s(am, "province"), s(am, "city"), s(am, "street"),
                    s(am, "postal"), s(am, "lat")?.toDoubleOrNull(), s(am, "lng")?.toDoubleOrNull()
                )
            }.orEmpty()
            val people = (vm["people"] as? List<*>)?.mapNotNull { a ->
                val am = a as? Map<*, *> ?: return@mapNotNull null
                val nm = s(am, "name") ?: return@mapNotNull null
                PersonData(0, nm, s(am, "role"), s(am, "mobile"), s(am, "phone"), s(am, "email"), s(am, "note"))
            }.orEmpty()
            val banks = (vm["banks"] as? List<*>)?.mapNotNull { a ->
                val am = a as? Map<*, *> ?: return@mapNotNull null
                BankData(0, s(am, "bank"), s(am, "branch"), s(am, "account"), s(am, "sheba"), s(am, "holder"))
            }.orEmpty()
            out.add(VendorData(0, name, attrs, addrs, people, banks))
        }
        return out
    }

    /** تبدیل یک وندور کامل به «اکشن» برای ادغام در دیتابیس (بازیابی پشتیبان). */
    fun toAction(v: VendorData): VendorAction {
        val sets = ArrayList<Pair<String, String>>()
        val lists = LinkedHashMap<String, MutableList<String>>()
        val phones = ArrayList<Triple<String, String, String>>()
        val emails = ArrayList<Pair<String, String>>()
        for (a in v.attrs) {
            when {
                a.key in Keys.PHONE_KEYS -> phones.add(Triple(a.key, a.label.orEmpty(), a.value))
                a.key == "ایمیل" -> emails.add(a.label.orEmpty() to a.value)
                a.key in Keys.LIST_KEYS -> lists.getOrPut(a.key) { mutableListOf() }.add(a.value)
                else -> sets.add(a.key to a.value)
            }
        }
        return VendorAction(
            vendor = v.name, sets = sets, lists = lists.map { it.key to it.value }, phones = phones, emails = emails,
            addresses = v.addresses.map { AddressIn(it.kind, it.province, it.city, it.street, it.postal, it.lat, it.lng) },
            people = v.people.map { PersonIn(it.name, it.role, it.mobile, it.phone, it.email, it.note) },
            banks = v.banks.map { BankIn(it.bank, it.branch, it.account, it.sheba, it.holder) }
        )
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(password, salt, ITER, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(json: String, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(json.toByteArray(Charsets.UTF_8))
    }

    /** null یعنی رمز اشتباه است یا فایل پشتیبان معتبر نیست. */
    fun decrypt(bytes: ByteArray, password: CharArray): String? {
        if (bytes.size < MAGIC.size + 16 + 12 + 16) return null
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return null
        return try {
            val salt = bytes.copyOfRange(4, 20)
            val iv = bytes.copyOfRange(20, 32)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            String(c.doFinal(bytes, 32, bytes.size - 32), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- خروجی اکسل (هم‌قالب با ورودی؛ بدون اطلاعات بانکی) ----------------
    private fun withLabel(v: String, label: String?) = if (label.isNullOrEmpty()) v else "$v ($label)"

    fun toTable(vendors: List<VendorData>): List<List<String>> {
        val kinds = LinkedHashSet<String>()
        vendors.forEach { v -> v.addresses.forEach { kinds.add(it.kind) } }
        val maxPeople = minOf(3, vendors.maxOfOrNull { it.people.size } ?: 0)
        val fixed = setOf("نوع فعالیت", "دسته‌بندی", "کالا", "شناسه ملی", "شماره ثبت", "کد اقتصادی", "وبسایت", "ایمیل", "تلفن", "موبایل", "توضیحات")
        val custom = LinkedHashSet<String>()
        vendors.forEach { v -> v.attrs.forEach { if (!it.secret && it.key !in fixed) custom.add(it.key) } }

        val header = ArrayList<String>()
        header.addAll(listOf("نام وندور", "نوع فعالیت", "دسته‌بندی", "کالا و خدمات", "شناسه ملی", "شماره ثبت", "کد اقتصادی", "وبسایت", "ایمیل", "تلفن", "موبایل"))
        for (k in kinds) {
            val suffix = if (k == "اصلی") "" else " $k"
            header.addAll(listOf("آدرس$suffix", "شهر$suffix", "استان$suffix", "کد پستی$suffix", "موقعیت$suffix"))
        }
        for (i in 1..maxPeople) {
            val g = if (i == 1) "مسئول" else "پرسنل $i"
            val nameH = if (i == 1) "نام مسئول" else "نام پرسنل $i"
            header.addAll(listOf(nameH, "سمت $g", "موبایل $g", "تلفن $g", "ایمیل $g"))
        }
        header.add("توضیحات")
        header.addAll(custom)

        val rows = ArrayList<List<String>>()
        rows.add(header)
        for (v in vendors) {
            fun join(key: String) = v.attrs.filter { it.key == key && !it.secret }.joinToString("، ") { withLabel(it.value, it.label) }
            val r = ArrayList<String>()
            r.add(v.name)
            r.add(join("نوع فعالیت")); r.add(join("دسته‌بندی")); r.add(join("کالا"))
            r.add(join("شناسه ملی")); r.add(join("شماره ثبت")); r.add(join("کد اقتصادی")); r.add(join("وبسایت"))
            r.add(join("ایمیل")); r.add(join("تلفن")); r.add(join("موبایل"))
            for (k in kinds) {
                val a = v.addresses.firstOrNull { it.kind == k }
                r.add(a?.street.orEmpty()); r.add(a?.city.orEmpty()); r.add(a?.province.orEmpty()); r.add(a?.postal.orEmpty())
                r.add(if (a?.lat != null && a.lng != null) Geo.format(a.lat, a.lng) else "")
            }
            for (i in 0 until maxPeople) {
                val p = v.people.getOrNull(i)
                r.add(p?.name.orEmpty()); r.add(p?.role.orEmpty()); r.add(p?.mobile.orEmpty())
                r.add(p?.phone.orEmpty()); r.add(p?.email.orEmpty())
            }
            r.add(join("توضیحات"))
            for (k in custom) r.add(join(k))
            rows.add(r)
        }
        return rows
    }
}
