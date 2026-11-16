package com.alisttracker

import com.alisttracker.data.Billing
import com.alisttracker.data.SubPayment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BillingTest {
    private fun s(y: Int, m: Int, a: Double = 29.99) = SubPayment(y, m, a)
    private fun d(x: String) = LocalDate.parse(x)
    private fun ym(l: List<SubPayment>) = l.map { it.year * 100 + it.month }
    private val thruDec26 = (2025..2026).flatMap { y -> (1..12).map { s(y, it) } }

    @Test fun nothingToAddWhileEnteredAhead() = assertEquals(emptyList<Int>(), ym(Billing.missing(thruDec26, 29.99, d("2026-11-15"))))
    @Test fun januaryAddedOnThe30th() {
        assertEquals(emptyList<Int>(), ym(Billing.missing(thruDec26, 29.99, d("2027-01-29"))))
        assertEquals(listOf(202701), ym(Billing.missing(thruDec26, 29.99, d("2027-01-30"))))
    }
    @Test fun februaryChargedOnLastDay() = assertEquals(listOf(202701, 202702), ym(Billing.missing(thruDec26, 29.99, d("2027-02-28"))))
    @Test fun runningTwiceAddsNothing() {
        val once = thruDec26 + Billing.missing(thruDec26, 29.99, d("2027-03-15"))
        assertEquals(emptyList<SubPayment>(), Billing.missing(once, 29.99, d("2027-03-15")))
    }
    @Test fun newPriceOnlyChangesFutureMonths() {
        val f = Billing.futureWithPrice(thruDec26, 31.99, d("2026-11-15"))
        assertEquals(listOf(202611, 202612), ym(f))
    }
}
