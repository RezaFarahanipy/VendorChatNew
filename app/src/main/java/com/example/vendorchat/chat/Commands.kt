package com.example.vendorchat.chat

import com.example.vendorchat.data.Fa
import com.example.vendorchat.data.Keys

sealed class Cmd {
    data class Add(val vendor: String, val fields: Map<String, String>) : Cmd()
    /** vendor خالی یعنی «وندور فعلی گفتگو» */
    data class Update(val vendor: String, val fields: Map<String, String>, val remove: List<String>) : Cmd()
    data class Delete(val vendor: String) : Cmd()
    object None : Cmd()      // مدل گفته پیام دستور نیست
    object Invalid : Cmd()   // خروجی مدل قابل فهم نبود
}

object Commands {
    private fun norm(l: List<String>) = l.map { Fa.normalize(it) }

    private val strong = norm(listOf(
        "اضافه", "بساز", "ایجاد", "وارد کن", "ویرایش", "تغییر", "عوض", "آپدیت", "به روز",
        "بروز", "اصلاح", "حذف", "پاک", "بردار"
    ))
    private val weak = norm(listOf("ثبت", "بنویس", "بذار", "بزار", "قرار بده", "بکن", "جدید"))
    private val keyWords = norm(listOf(
        "تلفن", "موبایل", "همراه", "شماره", "آدرس", "ایمیل", "شهر", "استان", "کد", "شناسه",
        "سایت", "وبسایت", "مسئول", "کالا", "حساب", "شبا", "بانک"
    ))
    private val questionWords = setOf(
        "چنده", "چیه", "چیست", "کیه", "کجاست", "کجا", "چقدر", "کدوم", "کدام", "چطور", "چرا",
        "آیا", "چند", "کی", "کدومه", "چه"
    )
    private val yes = setOf("بله", "آره", "اره", "تایید", "تایید کن", "تایید میکنم", "ok", "okay", "اوکی", "باشه", "ثبت کن", "انجام بده", "بزن", "yes")
    private val no = setOf("نه", "خیر", "لغو", "انصراف", "نکن", "کنسل", "cancel", "بیخیال", "no")

    fun hasStrong(n: String): Boolean = strong.any { n.contains(it) }

    fun isQuestion(raw: String, n: String): Boolean =
        raw.contains('?') || raw.contains('؟') || n.split(" ").any { it in questionWords }

    fun mentionsField(n: String): Boolean = keyWords.any { n.contains(it) }

    /** آیا پیام احتمالاً ثبت/ویرایش/حذف اطلاعات است؟ (پس مرحلهٔ استخراج اجرا شود) */
    fun likelyAction(raw: String, n: String): Boolean {
        if (hasStrong(n)) return true
        if (isQuestion(raw, n)) return false
        if (weak.any { n.contains(it) }) return true
        if (n.count { it.isDigit() } >= 5) return true
        return mentionsField(n) && n.split(" ").size >= 3
    }

    fun isYes(n: String): Boolean = n in yes || yes.any { n.startsWith("$it ") }
    fun isNo(n: String): Boolean = n in no || no.any { n.startsWith("$it ") }

    fun wantsList(n: String): Boolean =
        (n.contains("لیست") || n.contains("فهرست") || n.contains("همه")) &&
            (n.contains("وندور") || n.contains("تامین") || n.contains("شرکت"))

    fun wantsCount(n: String): Boolean =
        (n.contains("چند") || n.contains("تعداد")) && n.contains("وندور")

    private val showWords = norm(listOf("اطلاعات", "نشون بده", "نشان بده", "نمایش", "مشخصات", "کارت", "پروفایل", "جزئیات"))
    fun wantsShow(n: String): Boolean = showWords.any { n.contains(it) }

    fun wantsSecret(n: String): Boolean =
        n.contains("حساب") || n.contains("شبا") || n.contains("کارت")

    private const val ROUTER_BASE = """وظیفه: پیام کاربر را به یک دستور JSON تبدیل کن. فقط JSON خروجی بده، بدون هیچ توضیح.
قالب: {"action":"add|update|delete|none","vendor":"نام وندور","fields":{"کلید":"مقدار"},"remove":["کلید"]}
قوانین:
- add: ساخت وندور جدید (و ثبت اطلاعاتی که گفته شده).
- update: تغییر یا افزودن اطلاعات یک وندور موجود (fields) یا پاک کردن یک اطلاعات (remove).
- delete: حذف کل وندور.
- none: اگر پیام دستور ثبت، ویرایش یا حذف نیست و فقط سؤال یا صحبت عادی است.
- اگر کاربر اسم وندور نگفته و به وندور فعلی اشاره دارد (مثل «اش»، «شماره‌اش»، «همین وندور»)، مقدار vendor را @current بنویس.
- کلیدها فارسی و کوتاه باشند، مثل: تلفن، موبایل، شهر، استان، آدرس، کد پستی، شناسه ملی، شماره ثبت، کد اقتصادی، ایمیل، وبسایت، مسئول، سمت، کالا، شماره حساب، شبا، بانک، توضیحات.
- همهٔ مقدارها رشته باشند. اعداد را به انگلیسی بنویس.
مثال‌ها:
پیام: وندور فولاد اصفهان رو اضافه کن، تلفن ۰۳۱۱۲۲۳۳۴۴۴ شهر اصفهان
{"action":"add","vendor":"فولاد اصفهان","fields":{"تلفن":"03112233444","شهر":"اصفهان"},"remove":[]}
پیام: شماره موبایل فولاد اصفهان رو بکن ۰۹۱۲۱۲۳۴۵۶۷
{"action":"update","vendor":"فولاد اصفهان","fields":{"موبایل":"09121234567"},"remove":[]}
پیام: شماره تماسش ۰۲۱۴۴۳۳۳۳۲۰
{"action":"update","vendor":"@current","fields":{"تلفن":"02144333320"},"remove":[]}
پیام: ایمیل پارس لوله رو پاک کن
{"action":"update","vendor":"پارس لوله","fields":{},"remove":["ایمیل"]}
پیام: وندور پارس لوله رو حذف کن
{"action":"delete","vendor":"پارس لوله","fields":{},"remove":[]}
پیام: سلام، حالت چطوره؟
{"action":"none","vendor":"","fields":{},"remove":[]}
پیام: تلفن فولاد اصفهان چنده؟
{"action":"none","vendor":"","fields":{},"remove":[]}"""

    fun routerSystem(current: String?, names: List<String>): String = buildString {
        append(ROUTER_BASE)
        append("\n\nوندور فعلی گفتگو: ").append(current ?: "ندارد")
        if (names.isNotEmpty()) append("\nوندورهای موجود: ").append(names.take(15).joinToString("، "))
        append("\nحالا فقط برای پیام زیر JSON بده.")
    }

    const val REPHRASE =
        "نتونستم دستور رو دقیق بفهمم. ساده‌تر بگو، مثلاً:\n" +
            "• «وندور فولاد اصفهان رو اضافه کن، تلفن ۰۳۱۱۲۲۳۳۴۴۴، شهر اصفهان»\n" +
            "• «موبایلش رو بکن ۰۹۱۲۱۲۳۴۵۶۷»\n" +
            "• «ایمیل فولاد اصفهان رو پاک کن»\n" +
            "• «وندور فولاد اصفهان رو حذف کن»"

    private fun clean(v: String): String = Fa.latinDigits(v).trim().take(300)

    /** خروجی خام مدل را به دستور تبدیل می‌کند. */
    fun parse(raw: String): Cmd {
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return Cmd.Invalid
        val m = MiniJson.parse(raw.substring(a, b + 1)) as? Map<*, *> ?: return Cmd.Invalid
        val action = (m["action"] as? String)?.trim()?.lowercase() ?: return Cmd.Invalid
        if (action == "none") return Cmd.None
        var vendor = (m["vendor"] as? String)?.trim().orEmpty().take(100)
        if (vendor == "@current") vendor = ""
        if (action == "add" && vendor.isEmpty()) return Cmd.Invalid

        val fields = LinkedHashMap<String, String>()
        (m["fields"] as? Map<*, *>)?.forEach { (k, v) ->
            val key = (k as? String)?.let { Keys.canon(it) } ?: return@forEach
            val value = (v as? String)?.let { clean(it) } ?: return@forEach
            if (key.isNotEmpty() && value.isNotEmpty() && Fa.normalize(key) != Fa.normalize("نام")) {
                fields[key] = value
            }
        }
        val remove = (m["remove"] as? List<*>)?.mapNotNull { (it as? String)?.let { s -> Keys.canon(s) } }
            ?.filter { it.isNotEmpty() }.orEmpty()

        return when (action) {
            "add" -> Cmd.Add(vendor, fields)
            "update" -> if (fields.isEmpty() && remove.isEmpty()) Cmd.Invalid else Cmd.Update(vendor, fields, remove)
            "delete" -> Cmd.Delete(vendor)
            else -> Cmd.Invalid
        }
    }

    private fun shown(k: String, v: String) = if (Keys.isSecret(k)) "•••• (رمزگذاری می‌شود)" else v

    fun kind(cmd: Cmd): String = when (cmd) {
        is Cmd.Add -> "add"
        is Cmd.Update -> "update"
        is Cmd.Delete -> "delete"
        else -> ""
    }

    fun title(cmd: Cmd, name: String): String = when (cmd) {
        is Cmd.Add -> "ثبت وندور جدید: «$name»"
        is Cmd.Update -> "ویرایش «$name»"
        is Cmd.Delete -> "حذف «$name»"
        else -> ""
    }

    fun lines(cmd: Cmd): List<String> = when (cmd) {
        is Cmd.Add ->
            if (cmd.fields.isEmpty()) listOf("فقط نام وندور ثبت می‌شود؛ اطلاعاتش را بعداً با چت اضافه کن.")
            else cmd.fields.map { (k, v) -> "$k: ${shown(k, v)}" }
        is Cmd.Update ->
            cmd.fields.map { (k, v) -> "$k ← ${shown(k, v)}" } + cmd.remove.map { "حذف: $it" }
        is Cmd.Delete -> listOf("همهٔ اطلاعات این وندور پاک می‌شود و قابل بازگشت نیست.")
        else -> emptyList()
    }
}

/** تشخیص ساده و بدون مدل برای الگوهای رایج (تلفن، موبایل، ایمیل، سایت و کدها). */
object Fallback {
    private val emailRe = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val siteRe = Regex("(https?://|www\\.)[^\\s]+")
    private val numRe = Regex("\\d[\\d\\-\\s]{5,}\\d")

    fun guess(raw: String): Map<String, String> {
        val s = Fa.latinDigits(raw)
        val n = Fa.normalize(raw)
        val out = LinkedHashMap<String, String>()
        emailRe.find(s)?.let { out["ایمیل"] = it.value }
        siteRe.find(s)?.let { out["وبسایت"] = it.value }
        val num = numRe.find(s)?.value?.filter { it.isDigit() }
        if (num != null && num.length >= 7) {
            val key = when {
                n.contains("شناسه ملی") -> "شناسه ملی"
                n.contains("کد پستی") || n.contains("کدپستی") -> "کد پستی"
                n.contains("کد اقتصادی") -> "کد اقتصادی"
                n.contains("شماره ثبت") -> "شماره ثبت"
                n.contains("شبا") -> "شبا"
                n.contains("حساب") -> "شماره حساب"
                num.startsWith("09") && num.length == 11 -> "موبایل"
                else -> "تلفن"
            }
            out[key] = num
        }
        return out
    }
}
