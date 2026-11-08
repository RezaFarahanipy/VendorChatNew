package com.example.vendorchat.data

/** نرمال‌سازی متن فارسی و ساخت کوئری جستجو (FTS4). */
object Fa {
    private val stop = setOf(
        "و", "در", "از", "به", "را", "که", "با", "برای", "یک", "این", "آن", "رو", "هست", "هستن",
        "هستند", "کدوم", "کدام", "چیه", "چیست", "چی", "چه", "آیا", "می", "نمی", "هم", "تا", "بر",
        "میخوام", "میخواهم", "خوام", "خواهم", "لطفا", "بده", "بگو", "بگید", "کیه", "کجاست", "کجا",
        "چنده", "چقدر", "اطلاعات", "درباره", "شماره", "تلفن", "آدرس", "ایمیل", "وندور", "وندورها",
        "کن", "میشه", "من", "ما", "حساب", "شبا", "بانک", "کارت", "ها", "های", "اش", "ش", "است", "هستش"
    )
    private val suffixes = listOf("هایش", "هاش", "شون", "شان", "مون", "تون", "اش", "ها", "ش")

    private val digitMap: Map<Char, Char> =
        ('۰'..'۹').zip('0'..'9').toMap() + ('٠'..'٩').zip('0'..'9').toMap()

    fun latinDigits(s: String): String = s.map { digitMap[it] ?: it }.joinToString("")

    fun normalize(s: String): String =
        latinDigits(s)
            .replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('ة', 'ه')
            .replace('أ', 'ا').replace('إ', 'ا')
            .replace("\u200c", " ")
            .replace(Regex("[\u064B-\u065F\u0670\u0640]"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun strip(t: String): String {
        for (suf in suffixes) {
            if (t.endsWith(suf) && t.length - suf.length >= 3) return t.dropLast(suf.length)
        }
        return t
    }

    /** کلمات معنی‌دار سؤال (بدون کلمات عمومی و با حذف پسوندهای ضمیری مثل «ش» و «ها»). */
    fun tokens(q: String): List<String> =
        normalize(q).split(" ")
            .filter { it.isNotEmpty() && it !in stop }
            .map { strip(it) }
            .filter { it.length > 1 && it !in stop }

    /** کوئری MATCH برای FTS4؛ null یعنی کلمهٔ معنی‌داری وجود ندارد. */
    fun toMatch(q: String): String? =
        tokens(q).takeIf { it.isNotEmpty() }?.joinToString(" OR ") { "$it*" }
}
