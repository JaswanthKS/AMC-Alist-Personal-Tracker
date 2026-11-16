package com.alisttracker

import com.alisttracker.data.Lists
import com.alisttracker.data.Movie
import com.alisttracker.data.OptType
import com.alisttracker.data.Option
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks for the add-your-own lists (theaters, formats, languages, swag). Run with: gradlew testDebugUnitTest */
class ListsTest {
    private val opts = listOf(
        Option(OptType.THEATER, "AMC Empire 25"), Option(OptType.THEATER, "AMC Lincoln Square 13"),
        Option(OptType.FORMAT, "IMAX"), Option(OptType.FORMAT, "IMAX 70mm"), Option(OptType.FORMAT, "70mm"), Option(OptType.FORMAT, "Dolby Cinema"), Option(OptType.FORMAT, "Standard"),
        Option(OptType.LANGUAGE, "English"), Option(OptType.LANGUAGE, "Telugu"),
        Option(OptType.SWAG, "Poster"))
    private val logged = listOf(
        Movie(date = "2026-01-01", title = "A", theater = "AMC Newport 11", format = "Prime"),
        Movie(date = "2026-01-02", title = "B", theater = "Regal Union Square", format = "RPX", amc = false))
    private fun mv(f: String = "Standard", l: String = "English", t: String = "AMC Empire 25", s: String = "", d3: Boolean = false) =
        Movie(date = "2026-11-15", title = "T", format = f, language = l, theater = t, swag = s, threeD = d3)

    @Test fun tidiesTypedText() {
        assertEquals("AMC Garden State 16", Lists.clean("  AMC   Garden\tState  16 "))
        assertEquals("AMC Kips Bay 15", Lists.clean("AMC\nKips Bay\u0001 15"))
        assertEquals(60, Lists.clean("x".repeat(200)).length)
        assertEquals("", Lists.clean(" \n "))
    }

    @Test fun addingToAList() {
        assertEquals("AMC Garden State 16", Lists.checkNew(OptType.THEATER, " AMC Garden  State 16 ", opts).value)
        assertEquals(null, Lists.checkNew(OptType.THEATER, "AMC Empire 25", opts).value)       // already there
        assertEquals(null, Lists.checkNew(OptType.FORMAT, "imax", opts).value)                 // same, different case
        assertEquals(null, Lists.checkNew(OptType.FORMAT, " dolby  cinema ", opts).value)
        assertEquals(null, Lists.checkNew(OptType.LANGUAGE, "   ", opts).value)                // blank
        assertEquals("IMAX", Lists.checkNew(OptType.LANGUAGE, "IMAX", opts).value)             // same name in another list is fine
        assertEquals(null, Lists.checkNew(OptType.FORMAT, "3D", opts).value)                   // 3D is a tick box
        assertEquals("ScreenX", Lists.checkNew(OptType.FORMAT, "ScreenX 3D", opts).value)
        assertEquals(null, Lists.checkNew(OptType.FORMAT, "IMAX 3d", opts).value)
        assertEquals("4DX", Lists.checkNew(OptType.FORMAT, "4DX", opts).value)
        assertEquals("Cinema 3D", Lists.checkNew(OptType.THEATER, "Cinema 3D", opts).value)
        assertEquals("=SUM(A1)", Lists.checkNew(OptType.THEATER, "=SUM(A1)", opts).value)      // kept as text; the sheet script stores it safely
        assertEquals("Malayalam", Lists.checkNew(OptType.LANGUAGE, "Malayalam", opts).value)
    }

    @Test fun savingAMovie() {
        var n = Lists.normalize(mv(f = "imax", l = "telugu ", t = "amc empire 25"), opts, logged)
        assertEquals(listOf("IMAX", "Telugu", "AMC Empire 25"), listOf(n.format, n.language, n.theater))
        n = Lists.normalize(mv(f = "", l = " ", t = "", s = ""), opts, logged)
        assertEquals(listOf("Standard", "English", "", ""), listOf(n.format, n.language, n.theater, n.swag))
        n = Lists.normalize(mv(f = "IMAX 3D"), opts, logged)
        assertEquals("IMAX" to true, n.format to n.threeD)
        n = Lists.normalize(mv(f = "3d"), opts, logged)
        assertEquals("Standard" to true, n.format to n.threeD)
        n = Lists.normalize(mv(f = "IMAX", d3 = true), opts, logged)
        assertEquals("IMAX" to true, n.format to n.threeD)
        n = Lists.normalize(mv(f = "prime", t = "amc newport 11"), opts, logged)
        assertEquals("Prime" to "AMC Newport 11", n.format to n.theater)
        n = Lists.normalize(mv(f = "ScreenX", t = "AMC  Kips Bay 15", l = "Korean", s = "Tote Bag"), opts, logged)
        assertEquals(listOf("ScreenX", "AMC Kips Bay 15", "Korean", "Tote Bag"), listOf(n.format, n.theater, n.language, n.swag))
        assertEquals(n, Lists.normalize(n, opts, logged))   // saving twice changes nothing
        for (f in listOf("IMAX", "IMAX 70mm", "70mm", "Dolby Cinema", "Prime", "Standard", "XL", "Vistavision"))
            assertEquals(f to false, Lists.normalize(mv(f = f), opts, logged).let { it.format to it.threeD })
    }

    @Test fun syncingListsWithTheSheet() {
        val opts2 = opts + Option(OptType.THEATER, "Regal Union Square") + Option(OptType.THEATER, "AMC Unused 9") + Option(OptType.FORMAT, "RPX")
        assertEquals(listOf("AMC Empire 25", "AMC Lincoln Square 13", "AMC Unused 9"), Lists.forSheet(OptType.THEATER, opts2, logged))
        assertEquals(false, "RPX" in Lists.forSheet(OptType.FORMAT, opts2, logged))
        val both = logged + Movie(date = "2026-02-01", title = "C", theater = "Regal Union Square", amc = true)
        assertEquals(true, "Regal Union Square" in Lists.forSheet(OptType.THEATER, opts2, both))
        assertEquals(listOf("AMC Garden State 16"),
            Lists.newFromSheet(OptType.THEATER, listOf("AMC Empire 25", "amc lincoln square 13", "AMC Garden State 16", " ", "AMC Garden State 16"), opts, emptySet()))
        assertEquals(listOf("ScreenX"),   // Vistavision was removed in the app earlier, so it stays removed
            Lists.newFromSheet(OptType.FORMAT, listOf("Vistavision", "ScreenX"), opts, setOf(Lists.seenKey(OptType.FORMAT, "Vistavision"))))
    }

    @Test fun everyListWorksTheSameWay() {
        assertEquals(Lists.kinds.size, Lists.kinds.map { it.type }.distinct().size)
        for (k in Lists.kinds) assertEquals("Zed", k.get(k.set(mv(), "Zed")))
    }
}
