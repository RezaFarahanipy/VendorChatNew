package com.example.vendorchat.data

/** یک مشخصهٔ ساده (کلید/مقدار) با برچسب اختیاری؛ مثلاً تلفن «دفتر». */
data class Attr(val key: String, val value: String, val secret: Boolean, val label: String? = null)

data class AddressData(
    val id: Long, val kind: String, val province: String?, val city: String?,
    val street: String?, val postal: String?, val lat: Double?, val lng: Double?
)

data class PersonData(
    val id: Long, val name: String, val role: String?, val mobile: String?,
    val phone: String?, val email: String?, val note: String?
)

/** حساب بانکی؛ مقدارها در این مدل رمزگشایی‌شده‌اند (در دیتابیس رمزگذاری می‌شوند). */
data class BankData(
    val id: Long, val bank: String?, val branch: String?, val account: String?,
    val sheba: String?, val holder: String?
)

data class VendorData(
    val id: Long,
    val name: String,
    val attrs: List<Attr>,
    val addresses: List<AddressData> = emptyList(),
    val people: List<PersonData> = emptyList(),
    val banks: List<BankData> = emptyList()
)

data class VendorRow(val id: Long, val name: String)

// ---- ورودی‌ها برای ساخت یا ادغام ----
data class AddressIn(
    val kind: String, val province: String? = null, val city: String? = null,
    val street: String? = null, val postal: String? = null, val lat: Double? = null, val lng: Double? = null
)

data class PersonIn(
    val name: String, val role: String? = null, val mobile: String? = null,
    val phone: String? = null, val email: String? = null, val note: String? = null
)

data class BankIn(
    val bank: String? = null, val branch: String? = null, val account: String? = null,
    val sheba: String? = null, val holder: String? = null
)
