package com.example.vendorchat.chat

import com.example.vendorchat.data.VendorData

object Prompts {
    /** متن یک وندور برای مدل؛ شمارهٔ حساب و شبا فقط در صورت درخواست صریح. */
    fun block(v: VendorData, withSecret: Boolean): String = buildString {
        append("• وندور: ${v.name}")
        val groups = LinkedHashMap<String, MutableList<String>>()
        v.attrs.forEach { a ->
            if (!a.secret || withSecret) {
                val t = if (a.label.isNullOrEmpty()) a.value else "${a.value} (${a.label})"
                groups.getOrPut(a.key) { mutableListOf() }.add(t)
            }
        }
        groups.forEach { (k, vals) -> append("\n  $k: ${vals.joinToString("، ")}") }
        v.addresses.forEach { a ->
            append("\n  آدرس ${a.kind}: " + listOfNotNull(a.province, a.city, a.street).joinToString("، "))
            a.postal?.let { append(" | کد پستی $it") }
            if (a.lat != null && a.lng != null) append(" | موقعیت نقشه ثبت شده")
        }
        v.people.forEach { p ->
            append("\n  پرسنل: ${p.name}")
            val bits = listOfNotNull(p.role, p.mobile, p.phone, p.email)
            if (bits.isNotEmpty()) append(" — " + bits.joinToString(" • "))
        }
        v.banks.forEach { b ->
            append("\n  حساب بانکی: ${b.bank.orEmpty()}")
            b.branch?.let { append(" • شعبه $it") }
            if (withSecret) {
                b.account?.let { append(" • حساب $it") }
                b.sheba?.let { append(" • شبا $it") }
                b.holder?.let { append(" • صاحب حساب $it") }
            } else {
                append(" (شماره حساب و شبا مخفی است و فقط با درخواست صریح کاربر نشان داده می‌شود)")
            }
        }
    }

    private const val SPEC = """اگر کاربر می‌خواهد چیزی ثبت، تغییر یا حذف شود، یا در پیامش اطلاعاتی هست که باید ذخیره شود، یا می‌خواهد وندوری را ببیند یا پیدا کند، بعد از یک جواب کوتاه، یک بلوک عمل با این قالب بنویس (هر خط یک مورد؛ اجزا با | جدا می‌شوند؛ فقط خطوط لازم را بنویس):
<<ACTION>>
vendor: نام وندور (اگر منظور وندور فعلی است @current)
new: yes (فقط برای وندور جدید)
delete: yes (حذف کل وندور)
phone: برچسب | شماره (مثلاً دفتر | 03112233444)
email: برچسب | ایمیل
address: نوع | استان | شهر | خیابان و پلاک | کدپستی | lat,lng (نوع مثل کارخانه، دفتر مرکزی، انبار)
person: نام | سمت | موبایل | ایمیل
bank: نام بانک | شعبه | شماره حساب | شبا | صاحب حساب
set: کلید | مقدار (شناسه ملی، شماره ثبت، کد اقتصادی، وبسایت، توضیحات، ...)
list: کلید | مورد۱، مورد۲ (نوع فعالیت، دسته‌بندی، کالا)
remove: چه چیزی | جزئیات
show: نام وندور (نمایش کارت)
search: کلمات (پیدا کردن وندورها)
<<END>>
قوانین بلوک: اعداد را انگلیسی بنویس؛ چیزی از خودت نساز؛ برای گفتگوی معمولی، سلام و سؤال‌های عمومی اصلاً بلوک ننویس.
مثال‌ها (فقط برای قالب بلوک؛ لحن جواب را طبق دستور لحن بنویس):
کاربر: وندور فولاد اصفهان رو اضافه کن، تلفن دفترش 03112233444، کارخونه‌ش تو شهرک صنعتی اصفهانه، تولیدکننده‌ست
دستیار: آماده‌ش کردم، تأیید کن.
<<ACTION>>
vendor: فولاد اصفهان
new: yes
phone: دفتر | 03112233444
address: کارخانه | | اصفهان | شهرک صنعتی
list: نوع فعالیت | تولیدکننده
<<END>>
کاربر: مدیر فروشش آقای رضایی‌ه، موبایلش 09121234567
دستیار: باشه، اضافه‌ش می‌کنم، تأیید کن.
<<ACTION>>
vendor: @current
person: رضایی | مدیر فروش | 09121234567
<<END>>
کاربر: کی لوله گالوانیزه داره؟
دستیار: الان می‌گردم.
<<ACTION>>
search: لوله گالوانیزه
<<END>>
کاربر: سلام، چطوری؟
دستیار: سلام! خوبم، تو چطوری؟ (بدون بلوک)"""

    private fun context(total: Int, names: List<String>, current: String?, data: String, history: List<Pair<String, String>>) =
        buildString {
            appendLine("تعداد کل وندورها: $total")
            if (names.isNotEmpty()) appendLine("نام وندورها: ${names.take(15).joinToString("، ")}")
            appendLine("وندور فعلی گفتگو: ${current ?: "ندارد"}")
            if (data.isNotBlank()) {
                appendLine("--- داده‌ها ---")
                appendLine(data.take(2200))
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

    /** دستور اصلی گفتگو: جواب طبیعی + در صورت نیاز بلوک عمل. */
    fun chatSystem(
        tone: Int, total: Int, names: List<String>, current: String?, data: String,
        history: List<Pair<String, String>>
    ): String = buildString {
        appendLine("تو «دستیار وندور» هستی؛ یک دستیار هوشمند فارسی‌زبان برای مدیریت وندورها (تأمین‌کنندگان) که مثل یک آدم واقعی و باهوش گفتگو می‌کند، نه مثل یک ربات خشک.")
        appendLine(Tone.instruction(tone))
        appendLine("با سلام، تشکر، شوخی و صحبت عادی هم طبیعی جواب بده. جواب‌ها کوتاه و مفید باشند.")
        appendLine("اطلاعات وندورها فقط از بخش «داده‌ها» است؛ اگر چیزی نیست بگو ثبت نشده و هرگز شماره، آدرس یا اطلاعاتی از خودت نساز.")
        appendLine("تو مستقیم چیزی ذخیره یا حذف نمی‌کنی و هرگز نگو «ثبت کردم» یا «حذف شد». وقتی بلوک عمل می‌نویسی فقط بگو آماده‌ش کردی تا کاربر تأیید کند؛ برنامه خودش انجامش می‌دهد.")
        appendLine(SPEC)
        append(context(total, names, current, data, history))
    }

    /** مرحلهٔ کمکی: فقط استخراج بلوک عمل از پیام. */
    fun extractSystem(names: List<String>, current: String?): String = buildString {
        appendLine("وظیفه: از پیام کاربر اطلاعات قابل ثبت، تغییر یا حذف را استخراج کن. فقط بلوک عمل را خروجی بده، بدون هیچ توضیح. اگر چیزی برای ثبت یا تغییر نبود، فقط بنویس: NONE")
        appendLine(SPEC.substringBefore("مثال‌ها"))
        appendLine("وندور فعلی گفتگو: ${current ?: "ندارد"}")
        if (names.isNotEmpty()) appendLine("وندورهای موجود: ${names.take(15).joinToString("، ")}")
        appendLine("حالا فقط برای پیام زیر بلوک عمل بده.")
    }
}
