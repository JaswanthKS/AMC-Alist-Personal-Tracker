package com.alisttracker.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * A-List membership billing: AMC charges on the 30th of every month (the last day of a shorter month).
 *
 * - Months already recorded are never changed by the automatic fill.
 * - A new price only applies to months that have not been charged yet.
 */
object Billing {
    const val DAY = 30
    /** A-List allows this many movies in a 7-day window. */
    const val WEEKLY_LIMIT = 4
    private const val MAX_FILL_MONTHS = 24

    fun dueDate(ym: YearMonth): LocalDate = ym.atDay(minOf(DAY, ym.lengthOfMonth()))

    /** True once the charge day of that month has arrived (today counts). */
    fun isCharged(year: Int, month: Int, today: LocalDate): Boolean =
        !dueDate(YearMonth.of(year, month)).isAfter(today)

    private fun key(year: Int, month: Int) = year * 100 + month

    /**
     * Months charged by [today] but not recorded yet, starting after the latest recorded month.
     * Nothing is added when no payment was ever recorded, or the price is not above zero.
     * Gaps before the latest recorded month are left alone (they may be months you really skipped).
     */
    fun missing(subs: List<SubPayment>, price: Double, today: LocalDate): List<SubPayment> {
        if (price <= 0.0 || subs.isEmpty()) return emptyList()
        val have = subs.map { key(it.year, it.month) }.toSet()
        val latest = subs.maxOf { key(it.year, it.month) }
        var ym = YearMonth.of(latest / 100, latest % 100).plusMonths(1)
        val out = mutableListOf<SubPayment>()
        var n = 0
        while (n++ < MAX_FILL_MONTHS && !dueDate(ym).isAfter(today)) {
            if (key(ym.year, ym.monthValue) !in have) out += SubPayment(ym.year, ym.monthValue, price)
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** The price being paid now: the one you set, otherwise the amount of the latest month with a payment. */
    fun priceNow(stored: Double, subs: List<SubPayment>): Double =
        if (stored > 0.0) stored
        else subs.filter { it.amount > 0.0 }.maxByOrNull { key(it.year, it.month) }?.amount ?: 0.0

    /** Recorded months not charged yet, with the new price. Months already charged are not included. */
    fun futureWithPrice(subs: List<SubPayment>, price: Double, today: LocalDate): List<SubPayment> =
        subs.filter { it.amount > 0.0 && !isCharged(it.year, it.month, today) && it.amount != price }
            .map { it.copy(amount = price) }
}
