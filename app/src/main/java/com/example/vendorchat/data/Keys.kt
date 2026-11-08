package com.example.vendorchat.data

/** نام‌گذاری یکدست فیلدها (مثلاً «شماره موبایل» و «همراه» هر دو می‌شوند «موبایل»). */
object Keys {
    private val alias: Map<String, String> = HashMap<String, String>().also { m ->
        fun add(canon: String, vararg names: String) {
            m[Fa.normalize(canon)] = canon
            names.forEach { m[Fa.normalize(it)] = canon }
        }
        add("تلفن", "شماره تلفن", "تلفن ثابت", "شماره ثابت", "تل")
        add("موبایل", "شماره موبایل", "همراه", "تلفن همراه", "شماره همراه")
        add("ایمیل", "email", "پست الکترونیک", "ای میل", "آدرس ایمیل")
        add("آدرس", "نشانی", "ادرس", "آدرس دفتر", "آدرس کارخانه")
        add("شهر")
        add("استان")
        add("کد پستی", "کدپستی")
        add("شناسه ملی", "شناسه")
        add("شماره ثبت", "ثبت")
        add("کد اقتصادی", "کداقتصادی")
        add("شماره حساب", "حساب", "شماره حساب بانکی")
        add("شبا", "شماره شبا")
        add("بانک", "نام بانک")
        add("وبسایت", "سایت", "وب سایت", "آدرس سایت")
        add("مسئول", "مدیر", "فرد تماس", "شخص تماس", "رابط", "نماینده")
        add("سمت")
        add("کالا", "محصول", "محصولات", "کالاها", "خدمات", "کالا و خدمات")
        add("توضیحات", "توضیح", "یادداشت")
    }

    /** کلیدهایی که می‌توانند چند مقدار داشته باشند (تلفن‌های متعدد، چند کالا و ...). */
    val MULTI = setOf("تلفن", "موبایل", "ایمیل", "مسئول", "کالا", "آدرس")

    fun canon(k: String): String = alias[Fa.normalize(k)] ?: k.trim().take(40)

    /** این کلیدها رمزگذاری‌شده ذخیره می‌شوند و در جستجو شاخص نمی‌شوند. */
    fun isSecret(canonKey: String): Boolean {
        val n = Fa.normalize(canonKey)
        return n.contains("حساب") || n.contains("شبا") || n.contains("کارت") || n.contains("رمز")
    }
}
