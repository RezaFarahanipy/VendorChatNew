package com.example.vendorchat.chat

import com.example.vendorchat.data.VendorData

object Prompts {
    /** متن یک وندور برای مدل؛ اطلاعات رمزگذاری‌شده فقط در صورت درخواست صریح. */
    fun block(v: VendorData, withSecret: Boolean): String = buildString {
        append("• وندور: ${v.name}")
        val groups = LinkedHashMap<String, MutableList<String>>()
        v.attrs.forEach { a ->
            if (!a.secret || withSecret) groups.getOrPut(a.key) { mutableListOf() }.add(a.value)
        }
        groups.forEach { (k, vals) -> append("\n  $k: ${vals.joinToString("، ")}") }
    }

    fun answerSystem(data: String, total: Int): String = buildString {
        appendLine("تو دستیار فارسی‌زبان یک سامانهٔ مدیریت وندورها (تأمین‌کنندگان) هستی.")
        appendLine("قوانین: فقط بر اساس «داده‌های وندور» زیر پاسخ بده. اگر پاسخ در داده‌ها نیست بگو «در اطلاعات ثبت‌شده پیدا نشد».")
        appendLine("هیچ شماره، آدرس یا اطلاعاتی را از خودت نساز. پاسخ کوتاه، مرتب و به فارسی باشد.")
        appendLine("تعداد کل وندورهای ثبت‌شده: $total")
        appendLine("--- داده‌های وندور ---")
        appendLine(data.take(3000))
        appendLine("--- پایان داده‌ها ---")
    }
}
