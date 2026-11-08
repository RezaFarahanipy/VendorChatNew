package com.example.vendorchat.chat

import com.example.vendorchat.data.VendorData

/** پیام‌های صفحهٔ چت (متن، کارت وندور، کارت تأیید، چیپ‌های انتخاب). */
sealed class Msg {
    data class Text(val fromUser: Boolean, val text: String) : Msg()
    data class Cards(val vendors: List<VendorData>) : Msg()
    data class Chips(val title: String, val names: List<String>) : Msg()
    /** state: 0 منتظر تأیید، 1 انجام شد، 2 لغو شد */
    data class Action(val kind: String, val title: String, val lines: List<String>, val state: Int) : Msg()
}
