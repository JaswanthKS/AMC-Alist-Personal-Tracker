package com.alisttracker.data

/**
 * The lists you can add to yourself (theaters, formats, languages, swag).
 *
 * Everything that adds to a list, tidies what was typed, shows a list or syncs it with the sheet goes through this file.
 * To add another list later:
 *   1. add one line to [Lists.kinds] below (the key must be the name of the field on a movie), and
 *   2. add one line to LISTS at the top of the sheet script (AList_Sheet_Sync.gs).
 * The Lists tab, the movie form, Stats and the sheet's Lists tab all pick it up from there.
 */
class ListKind(
    val type: String,                    // key in the options table, and the name sent to the sheet
    val label: String,                   // heading on the Lists tab of the app
    val fallback: String,                // used when the field is left empty ("" = it may stay empty)
    val get: (Movie) -> String,
    val set: (Movie, String) -> Movie
)

object Lists {
    const val MAX_LEN = 60

    val kinds: List<ListKind> = listOf(
        ListKind(OptType.THEATER, "Theaters", "", { it.theater }, { m, v -> m.copy(theater = v) }),
        ListKind(OptType.FORMAT, "Formats", "Standard", { it.format }, { m, v -> m.copy(format = v) }),
        ListKind(OptType.LANGUAGE, "Languages", "English", { it.language }, { m, v -> m.copy(language = v) }),
        ListKind(OptType.SWAG, "Swag / freebies", "", { it.swag }, { m, v -> m.copy(swag = v) })
    )

    fun kind(type: String): ListKind? = kinds.firstOrNull { it.type == type }

    private val junk = Regex("[\\p{Cntrl}\\u2028\\u2029]")
    private val spaces = Regex("\\s+")
    private val threeDEnd = Regex("(?i)\\s*3d\\s*$")

    /** Tidies hand-typed text: no line breaks or hidden characters, single spaces, nothing at the ends, sensible length. */
    fun clean(raw: String): String =
        raw.replace(junk, " ").replace(spaces, " ").trim().take(MAX_LEN).trim()

    /** "IMAX 3D" is the IMAX format with the 3D box ticked (the sheet stores it that way too). Returns the format and whether it was 3D. */
    fun splitFormat(value: String): Pair<String, Boolean> {
        val v = clean(value)
        return if (threeDEnd.containsMatchIn(v)) clean(v.replace(threeDEnd, "")) to true else v to false
    }

    /** The spelling already in use for this value (upper/lower case ignored), or the tidied value when it is new. */
    fun canonical(value: String, known: Collection<String>): String {
        val c = clean(value)
        return known.firstOrNull { it == c } ?: known.firstOrNull { it.equals(c, ignoreCase = true) } ?: c
    }

    /** Every spelling in use for one list: the list itself first, then whatever is on logged movies. */
    fun known(type: String, options: List<Option>, movies: List<Movie>): List<String> {
        val k = kind(type)
        return (options.filter { it.type == type }.map { it.value } + (if (k == null) emptyList() else movies.map { k.get(it) }))
            .filter { it.isNotBlank() }.distinct()
    }

    /** A movie as it should be saved: list fields tidied, matched to the existing spelling, and never left in a state the sheet can't keep. */
    fun normalize(m: Movie, options: List<Option>, movies: List<Movie>): Movie {
        val (fmt, is3d) = splitFormat(m.format)
        var out = m.copy(format = fmt, threeD = m.threeD || is3d)
        for (k in kinds) out = k.set(out, canonical(k.get(out), known(k.type, options, movies)).ifBlank { k.fallback })
        return out
    }

    /** Result of trying to add something to a list: [value] is what to store, or null when nothing should be added. */
    class AddResult(val value: String?, val message: String)

    fun checkNew(type: String, raw: String, options: List<Option>): AddResult {
        var v = clean(raw)
        if (v.isEmpty()) return AddResult(null, "Type a name first")
        if (type == OptType.FORMAT) {
            val (base, is3d) = splitFormat(v)
            if (is3d && base.isEmpty()) return AddResult(null, "3D is a tick box on each movie, not a format")
            v = base
        }
        val same = options.firstOrNull { it.type == type && it.value.equals(v, ignoreCase = true) }
        if (same != null) return AddResult(null, "\"${same.value}\" is already in the list")
        return AddResult(v, "Added \"$v\"")
    }

    /** What to send to the sheet's Lists tab: the app's list, minus names only ever used at non-AMC theaters (the sheet's lists are for the Movie Log). */
    fun forSheet(type: String, options: List<Option>, movies: List<Movie>): List<String> {
        val k = kind(type) ?: return emptyList()
        return options.filter { it.type == type }.map { it.value }.filter { v ->
            v.isNotBlank() && !(movies.any { !it.amc && k.get(it) == v } && movies.none { it.amc && k.get(it) == v })
        }
    }

    fun seenKey(type: String, value: String) = type + "|" + value.trim().lowercase()

    /**
     * Values the app should pick up from the sheet: not in the app's list yet and never seen before.
     * "Seen before" is what keeps something you removed in the app from coming straight back.
     */
    fun newFromSheet(type: String, incoming: List<String>, options: List<Option>, seen: Set<String>): List<String> {
        val have = options.filter { it.type == type }.map { it.value.lowercase() }.toMutableSet()
        val out = mutableListOf<String>()
        for (raw in incoming) {
            val v = clean(raw)
            if (v.isEmpty() || v.lowercase() in have || seenKey(type, v) in seen) continue
            have += v.lowercase(); out += v
        }
        return out
    }
}
