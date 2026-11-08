package com.example.vendorchat.chat

import com.example.vendorchat.data.Fa
import com.example.vendorchat.data.Keys

sealed class Cmd {
    data class Add(val vendor: String, val fields: Map<String, String>) : Cmd()
    data class Update(val vendor: String, val fields: Map<String, String>, val remove: List<String>) : Cmd()
    data class Delete(val vendor: String) : Cmd()
    object None : Cmd()      // مدل گفته پیام دستور نیست
    object Invalid : Cmd()   // خروجی مدل قابل فهم نبود
}

object Commands {
    private val triggers = listOf(
        "اضافه", "ثبت", "بساز", "ایجاد", "وارد کن", "ویرایش", "تغییر", "عوض", "آپدیت", "به روز",
        "بروز", "اصلاح", "حذف", "پاک", "بردار", "بنویس", "بذار", "بزار", "قرار بده", "بکن", "جدید"
    ).map { Fa.normalize(it) }

    private val yes = setOf("بله", "آره", "اره", "تایید", "تایید کن", "تایید میکنم", "ok", "okay", "اوکی", "باشه", "ثبت کن", "انجام بده", "بزن", "yes")
    private val no = setOf("نه", "خیر", "لغو", "انصراف", "نکن", "کنسل", "cancel", "بیخیال", "no")

    fun looksLikeCommand(n: String): Boolean = triggers.any { n.contains(it) }
    fun isYes(n: String): Boolean = n in yes || yes.any { n.startsWith("$it ") }
    fun isNo(n: String): Boolean = n in no || no.any { n.startsWith("$it ") }

    fun wantsList(n: String): Boolean =
        (n.contains("لیست") || n.contains("فهرست") || n.contains("همه")) &&
            (n.contains("وندور") || n.contains("تامین") || n.contains("شرکت"))

    fun wantsCount(n: String): Boolean =
        (n.contains("چند") || n.contains("تعداد")) && n.contains("وندور")

    fun wantsSecret(n: String): Boolean =
        n.contains("حساب") || n.contains("شبا") || n.contains("کارت")

    const val ROUTER_SYSTEM = """وظیفه: پیام کاربر را به یک دستور JSON تبدیل کن. فقط JSON خروجی بده، بدون هیچ توضیح.
قالب: {"action":"add|update|delete|none","vendor":"نام وندور","fields":{"کلید":"مقدار"},"remove":["کلید"]}
قوانین:
- add: ساخت وندور جدید (و ثبت اطلاعاتی که گفته شده).
- update: تغییر یا افزودن اطلاعات یک وندور موجود (fields) یا پاک کردن یک اطلاعات (remove).
- delete: حذف کل وندور.
- none: اگر پیام دستور ثبت، ویرایش یا حذف نیست و فقط یک سؤال است.
- کلیدها فارسی و کوتاه باشند، مثل: تلفن، موبایل، شهر، استان، آدرس، کد پستی، شناسه ملی، شماره ثبت، کد اقتصادی، ایمیل، وبسایت، مسئول، سمت، کالا، شماره حساب، شبا، بانک، توضیحات.
- همهٔ مقدارها رشته باشند. اعداد را به انگلیسی بنویس.
مثال‌ها:
پیام: وندور فولاد اصفهان رو اضافه کن، تلفن ۰۳۱۱۲۲۳۳۴۴۴ شهر اصفهان
{"action":"add","vendor":"فولاد اصفهان","fields":{"تلفن":"03112233444","شهر":"اصفهان"},"remove":[]}
پیام: شماره موبایل فولاد اصفهان رو بکن ۰۹۱۲۱۲۳۴۵۶۷
{"action":"update","vendor":"فولاد اصفهان","fields":{"موبایل":"09121234567"},"remove":[]}
پیام: ایمیل پارس لوله رو پاک کن
{"action":"update","vendor":"پارس لوله","fields":{},"remove":["ایمیل"]}
پیام: وندور پارس لوله رو حذف کن
{"action":"delete","vendor":"پارس لوله","fields":{},"remove":[]}
پیام: تلفن فولاد اصفهان چنده؟
{"action":"none","vendor":"","fields":{},"remove":[]}
حالا فقط برای پیام زیر JSON بده."""

    const val REPHRASE =
        "نتونستم دستور رو دقیق بفهمم. ساده‌تر بگو، مثلاً:\n" +
            "• «وندور فولاد اصفهان رو اضافه کن، تلفن ۰۳۱۱۲۲۳۳۴۴۴، شهر اصفهان»\n" +
            "• «موبایل فولاد اصفهان رو بکن ۰۹۱۲۱۲۳۴۵۶۷»\n" +
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
        val vendor = (m["vendor"] as? String)?.trim().orEmpty().take(100)
        if (vendor.isEmpty()) return Cmd.Invalid

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

    fun summary(cmd: Cmd, vendorName: String): String = when (cmd) {
        is Cmd.Add -> buildString {
            append("این وندور ثبت بشه؟\n«$vendorName»")
            cmd.fields.forEach { (k, v) -> append("\n• $k: ${shown(k, v)}") }
            append("\n\nتأیید می‌کنی؟")
        }
        is Cmd.Update -> buildString {
            append("برای «$vendorName» این تغییرات انجام بشه؟")
            cmd.fields.forEach { (k, v) -> append("\n• $k ← ${shown(k, v)}") }
            if (cmd.remove.isNotEmpty()) append("\n• حذف: ${cmd.remove.joinToString("، ")}")
            append("\n\nتأیید می‌کنی؟")
        }
        is Cmd.Delete -> "وندور «$vendorName» با همهٔ اطلاعاتش حذف بشه؟ (قابل بازگشت نیست)\n\nتأیید می‌کنی؟"
        else -> ""
    }
}
