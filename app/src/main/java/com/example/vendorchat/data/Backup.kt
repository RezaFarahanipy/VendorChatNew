package com.example.vendorchat.data

import com.example.vendorchat.chat.MiniJson
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** پشتیبان رمزدار: JSON وندورها با AES-256-GCM و کلید مشتق‌شده از رمز کاربر (PBKDF2). */
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

    fun toJson(vendors: List<VendorData>): String = buildString {
        append("{\"app\":\"VendorChat\",\"v\":1,\"vendors\":[")
        vendors.forEachIndexed { i, v ->
            if (i > 0) append(',')
            append("{\"name\":\"").append(esc(v.name)).append("\",\"attrs\":[")
            v.attrs.forEachIndexed { j, a ->
                if (j > 0) append(',')
                append("{\"k\":\"").append(esc(a.key)).append("\",\"v\":\"").append(esc(a.value))
                    .append("\",\"s\":\"").append(if (a.secret) "1" else "0").append("\"}")
            }
            append("]}")
        }
        append("]}")
    }

    fun fromJson(json: String): List<VendorData>? {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return null
        val list = m["vendors"] as? List<*> ?: return null
        val out = ArrayList<VendorData>()
        for (item in list) {
            val vm = item as? Map<*, *> ?: continue
            val name = (vm["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val attrs = ArrayList<Attr>()
            (vm["attrs"] as? List<*>)?.forEach { a ->
                val am = a as? Map<*, *> ?: return@forEach
                val k = am["k"] as? String ?: return@forEach
                val v = am["v"] as? String ?: return@forEach
                attrs.add(Attr(k, v, (am["s"] as? String) == "1"))
            }
            out.add(VendorData(0, name, attrs))
        }
        return out
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

    /** جدول خروجی اکسل: ستون اول نام، سپس کلیدها (بدون اطلاعات رمزگذاری‌شده). */
    fun toTable(vendors: List<VendorData>): List<List<String>> {
        val pref = listOf(
            "تلفن", "موبایل", "ایمیل", "وبسایت", "آدرس", "شهر", "استان", "کد پستی", "مسئول", "سمت",
            "کالا", "شناسه ملی", "شماره ثبت", "کد اقتصادی", "توضیحات"
        )
        val keys = LinkedHashSet<String>()
        vendors.forEach { v -> v.attrs.forEach { if (!it.secret) keys.add(it.key) } }
        val ordered = keys.sortedBy { k -> pref.indexOf(k).let { if (it < 0) 999 else it } }
        val rows = ArrayList<List<String>>()
        rows.add(listOf("نام") + ordered)
        vendors.forEach { v ->
            rows.add(listOf(v.name) + ordered.map { k ->
                v.attrs.filter { it.key == k && !it.secret }.joinToString("، ") { it.value }
            })
        }
        return rows
    }
}
