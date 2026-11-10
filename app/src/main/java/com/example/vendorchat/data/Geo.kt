package com.example.vendorchat.data

import java.util.Locale
import kotlin.math.abs

/** استخراج مختصات (عرض، طول جغرافیایی) از متن یا لینک نقشه. */
object Geo {
    private const val NUM = "-?\\d{1,3}(?:\\.\\d+)?"
    private val pair = Regex("(-?\\d{1,3}\\.\\d+)\\s*[,،;\\s]\\s*(-?\\d{1,3}\\.\\d+)")
    private val atRe = Regex("@($NUM),($NUM)")
    private val qRe = Regex("[?&](?:q|ll|query|destination)=($NUM)(?:,|%2C)($NUM)")
    private val bangRe = Regex("!3d($NUM)!4d($NUM)")

    private fun check(a: String, b: String): Pair<Double, Double>? {
        val lat = a.toDoubleOrNull() ?: return null
        val lng = b.toDoubleOrNull() ?: return null
        if (abs(lat) > 90 || abs(lng) > 180) return null
        return lat to lng
    }

    fun parse(raw: String): Pair<Double, Double>? {
        val s = Fa.latinDigits(raw).replace('٫', '.')
        for (re in listOf(bangRe, atRe, qRe)) {
            val m = re.find(s) ?: continue
            return check(m.groupValues[1], m.groupValues[2]) ?: continue
        }
        val m = pair.find(s) ?: return null
        return check(m.groupValues[1], m.groupValues[2])
    }

    fun format(lat: Double, lng: Double): String = String.format(Locale.US, "%.6f,%.6f", lat, lng)
}
