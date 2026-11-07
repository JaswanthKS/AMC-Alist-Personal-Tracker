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

/** One month's A-List numbers: value of free tickets vs what the membership cost that month. */
data class MonthSummary(val visits: Int, val value: Double, val cost: Double) {
    val net: Double get() = value - cost
    val paidOff: Boolean get() = cost > 0 && value >= cost
}

fun monthSummary(all: List<Movie>, subs: List<SubPayment>, year: Int, month: Int): MonthSummary {
    val prefix = String.format(java.util.Locale.US, "%04d-%02d", year, month)
    val yes = all.filter { it.amc && it.alist == "Yes" && it.date.startsWith(prefix) }
    return MonthSummary(yes.size, yes.sumOf { it.total }, subs.filter { it.year == year && it.month == month }.sumOf { it.amount })
}

/** A-List visits in the last 7 days (today included). */
fun visitsLast7Days(all: List<Movie>, today: java.time.LocalDate = java.time.LocalDate.now()): Int {
    val from = today.minusDays(6).toString(); val to = today.toString()
    return all.count { it.amc && it.alist == "Yes" && it.date >= from && it.date <= to }
}

/** How many months with a payment recorded earned back at least what the membership cost. */
fun monthsPaidOff(all: List<Movie>, subs: List<SubPayment>): Pair<Int, Int> {
    val paid = subs.filter { it.amount > 0 }
    return paid.count { monthSummary(all, subs, it.year, it.month).paidOff } to paid.size
}

/** Popcorn-and-drinks spend across all visits, split by how it was paid. */
data class ConcessionStats(val total: Double, val visits: Int, val byPay: List<Pair<String, Double>>, val cashOut: Double)

fun concessionStats(all: List<Movie>): ConcessionStats {
    val withSpend = all.filter { it.concessions > 0 }
    val byPay = withSpend.groupBy { it.concessionsPay.ifBlank { "Not set" } }.map { (k, v) -> k to v.sumOf { it.concessions } }.sortedByDescending { it.second }
    // "Rewards" and "Gift Card" didn't come out of pocket on the day, so they are kept out of the true cost.
    val cashOut = withSpend.filter { it.concessionsPay != "Rewards" && it.concessionsPay != "Gift Card" }.sumOf { it.concessions }
    return ConcessionStats(withSpend.sumOf { it.concessions }, withSpend.size, byPay, cashOut)
}
