package com.alisttracker.ui

import com.alisttracker.data.Movie
import com.alisttracker.data.SubPayment

data class Stats(
    val totalMovies: Int,
    val aListMovies: Int,
    val aListValue: Double,
    val giftCardMovies: Int,
    val giftCardValue: Double,
    val voucherMovies: Int,
    val voucherValue: Double,
    val subsPaid: Double,
    val savings: Double,
    val outOfPocket: Double,
    val totalSpent: Double,
    val valuePerDollar: Double,
    val byTheater: List<Pair<String, Int>>,
    val byFormat: List<Pair<String, Int>>,
    val avgTicketByFormat: List<Pair<String, Double>>,
    val perMonth: Map<Int, IntArray> // year -> 12 counts
)

fun computeStats(all: List<Movie>, subs: List<SubPayment>, theaters: List<String>, formats: List<String>): Stats {
    val amc = all.filter { it.amc }
    val yes = amc.filter { it.alist == "Yes" }
    val gift = amc.filter { it.alist == "Gift Card" }
    val vouch = amc.filter { it.alist == "Voucher" }
    val no = amc.filter { it.alist == "No" }
    val subsPaid = subs.sumOf { it.amount }
    val aListValue = yes.sumOf { it.total }
    val oop = no.sumOf { it.total }
    val spent = subsPaid + oop
    val perMonth = sortedMapOf<Int, IntArray>()
    amc.forEach {
        val y = it.date.substring(0, 4).toInt()
        val m = it.date.substring(5, 7).toInt()
        perMonth.getOrPut(y) { IntArray(12) }[m - 1]++
    }
    return Stats(
        totalMovies = amc.size,
        aListMovies = yes.size,
        aListValue = aListValue,
        giftCardMovies = gift.size, giftCardValue = gift.sumOf { it.total },
        voucherMovies = vouch.size, voucherValue = vouch.sumOf { it.total },
        subsPaid = subsPaid,
        savings = aListValue - subsPaid,
        outOfPocket = oop,
        totalSpent = spent,
        valuePerDollar = if (spent > 0) aListValue / spent else 0.0,
        byTheater = (theaters + amc.map { it.theater }).filter { it.isNotBlank() }.distinct().map { t -> t to amc.count { it.theater == t } }.sortedByDescending { it.second },
        byFormat = (formats + amc.map { it.format }).filter { it.isNotBlank() }.distinct().map { f -> f to amc.count { it.format == f } }.sortedByDescending { it.second },
        avgTicketByFormat = amc.groupBy { it.format }.map { (k, v) -> k to v.map { it.ticket }.average() }
            .sortedByDescending { it.second },
        perMonth = perMonth
    )
}
