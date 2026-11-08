package com.example.vendorchat.chat

import com.example.vendorchat.data.Fa
import com.example.vendorchat.data.VendorRow

object NameMatch {
    data class Hit(val id: Long, val name: String, val score: Int)

    /** امتیاز: 3 = برابر، 2 = یکی شامل دیگری یا همهٔ کلمات مشترک، 1 = اشتراک بخشی. */
    fun rank(query: String, vendors: List<VendorRow>): List<Hit> {
        val q = Fa.normalize(query)
        if (q.isEmpty()) return emptyList()
        val qt = q.split(" ").filter { it.length > 1 }.toSet()
        return vendors.mapNotNull { v ->
            val n = Fa.normalize(v.name)
            if (n.isEmpty()) return@mapNotNull null
            val score = when {
                n == q -> 3
                n.length >= 3 && q.length >= 3 && (n.contains(q) || q.contains(n)) -> 2
                else -> {
                    val nt = n.split(" ").filter { it.length > 1 }.toSet()
                    val common = qt.intersect(nt).size
                    when {
                        common == 0 -> 0
                        common == qt.size || common == nt.size -> 2
                        else -> 1
                    }
                }
            }
            if (score > 0) Hit(v.id, v.name, score) else null
        }.sortedByDescending { it.score }
    }

    sealed class Resolved {
        data class One(val hit: Hit) : Resolved()
        data class Many(val hits: List<Hit>) : Resolved()
        object None : Resolved()
    }

    fun resolve(query: String, vendors: List<VendorRow>): Resolved {
        val hits = rank(query, vendors)
        val top = hits.firstOrNull() ?: return Resolved.None
        if (top.score < 2) return Resolved.Many(hits.take(5))
        val ties = hits.filter { it.score == top.score }
        return if (ties.size == 1) Resolved.One(top) else Resolved.Many(ties.take(5))
    }
}
