package com.example.vendorchat.data

data class Attr(val key: String, val value: String, val secret: Boolean)
data class VendorData(val id: Long, val name: String, val attrs: List<Attr>)
data class VendorRow(val id: Long, val name: String)
