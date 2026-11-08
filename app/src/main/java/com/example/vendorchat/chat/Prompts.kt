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

    fun chatSystem(
        total: Int,
        names: List<String>,
        current: String?,
        data: String,
        history: List<Pair<String, String>>
    ): String = buildString {
        appendLine("تو «دستیار وندور» هستی؛ یک دستیار فارسی‌زبان، صمیمی و مؤدب که به مدیریت وندورها (تأمین‌کنندگان) کمک می‌کند.")
        appendLine("با سلام، تشکر و صحبت عادی مثل یک دستیار طبیعی جواب بده؛ کوتاه، روان و مرتب بنویس.")
        appendLine("اطلاعات وندورها فقط از بخش «داده‌ها» است. اگر چیزی در داده‌ها نیست بگو ثبت نشده و از خودت شماره، آدرس یا اطلاعات نساز.")
        appendLine("تو چیزی را ذخیره، ویرایش یا حذف نمی‌کنی و هرگز نگو «ثبت کردم» یا «حذف کردم». ثبت، ویرایش و حذف را خود برنامه بعد از تأیید کاربر انجام می‌دهد؛ اگر کاربر خواست، بگو دستورش را واضح بنویسد، مثلاً «وندور X را اضافه کن، تلفن ...».")
        appendLine("تعداد کل وندورها: $total")
        if (names.isNotEmpty()) appendLine("نام وندورها: ${names.take(15).joinToString("، ")}")
        if (current != null) appendLine("وندور فعلی گفتگو: $current")
        if (data.isNotBlank()) {
            appendLine("--- داده‌ها ---")
            appendLine(data.take(2500))
            appendLine("--- پایان داده‌ها ---")
        }
        if (history.isNotEmpty()) {
            appendLine("گفتگوی قبلی:")
            history.forEach { (q, a) ->
                appendLine("کاربر: $q")
                appendLine("دستیار: $a")
            }
        }
    }
}
