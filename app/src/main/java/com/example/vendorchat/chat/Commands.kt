package com.example.vendorchat.chat

import com.example.vendorchat.data.Fa
import com.example.vendorchat.data.Geo

/** تشخیص‌های سریع و بدون مدل (تأیید/لغو، لیست، تعداد، برگردان) و نشانه‌های وجود داده در پیام. */
object Commands {
    private fun norm(l: List<String>) = l.map { Fa.normalize(it) }

    private val strongVerbs = norm(listOf(
        "اضافه", "بساز", "ایجاد", "وارد کن", "ویرایش", "تغییر", "عوض", "آپدیت", "به روز",
        "بروز", "اصلاح", "حذف", "پاک", "بردار", "ثبت کن", "ثبت بشه"
    ))
    private val questionWords = setOf(
        "چنده", "چیه", "چیست", "کیه", "کجاست", "کجا", "چقدر", "کدوم", "کدام", "چطور", "چرا",
        "آیا", "چند", "کی", "کدومه", "چه"
    )
    private val yes = setOf("بله", "آره", "اره", "تایید", "تایید کن", "تایید میکنم", "ok", "okay", "اوکی", "باشه", "ثبت کن", "انجام بده", "بزن", "yes", "اره دیگه", "آره دیگه")
    private val no = setOf("نه", "خیر", "لغو", "انصراف", "نکن", "کنسل", "cancel", "بیخیال", "no")
    private val showWords = norm(listOf("اطلاعات", "نشون بده", "نشان بده", "نمایش", "مشخصات", "کارت", "پروفایل", "جزئیات"))
    private val undoWords = norm(listOf("برگردون", "برگردان", "بازگردان", "undo", "برش گردون", "اشتباه شد", "پشیمون شدم"))

    fun hasStrong(n: String): Boolean = strongVerbs.any { n.contains(it) }

    fun isQuestion(raw: String, n: String): Boolean =
        raw.contains('?') || raw.contains('؟') || n.split(" ").any { it in questionWords }

    /** پیام شبیه دادهٔ قابل ثبت است (برای اجرای مرحلهٔ استخراج کمکی وقتی مدل بلوک عمل ننوشته). */
    fun looksLikeData(raw: String, n: String): Boolean {
        if (hasStrong(n)) return true
        if (isQuestion(raw, n)) return false
        if (raw.contains('@')) return true
        if (n.count { it.isDigit() } >= 7) return true
        if (Geo.parse(raw) != null) return true
        return false
    }

    fun isYes(n: String): Boolean = n in yes || yes.any { n.startsWith("$it ") }
    fun isNo(n: String): Boolean = n in no || no.any { n.startsWith("$it ") }

    fun wantsUndo(n: String): Boolean = n.split(" ").size <= 5 && undoWords.any { n.contains(it) }

    fun wantsList(n: String): Boolean =
        (n.contains("لیست") || n.contains("فهرست") || n.contains("همه")) &&
            (n.contains("وندور") || n.contains("تامین") || n.contains("شرکت")) &&
            n.split(" ").size <= 6

    fun wantsCount(n: String): Boolean =
        (n.contains("چند") || n.contains("تعداد")) && n.contains("وندور")

    fun wantsShow(n: String): Boolean = showWords.any { n.contains(it) } && !hasStrong(n)

    fun wantsSecret(n: String): Boolean =
        n.contains("حساب") || n.contains("شبا") || n.contains("کارت")
}
