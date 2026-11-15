package com.alisttracker.data

import org.json.JSONObject

/** The parts of the saved TMDB details that the stats use. */
data class FilmInfo(val runtime: Int, val genres: List<String>, val directors: List<String>, val cast: List<String>)

fun parseFilmInfo(json: String): FilmInfo? = runCatching {
    val o = JSONObject(json)
    val g = o.optJSONArray("genres")
    val genres = if (g == null) emptyList() else (0 until g.length()).map { g.optString(it) }
    val c = o.optJSONArray("cast")
    val cast = if (c == null) emptyList() else (0 until c.length()).mapNotNull { c.optJSONObject(it)?.optString("name") }
    FilmInfo(
        runtime = o.optInt("runtime", 0).coerceAtLeast(0),
        genres = genres.filter { it.isNotBlank() },
        directors = o.optString("director").split(",").map { it.trim() }.filter { it.isNotBlank() },
        cast = cast.filter { it.isNotBlank() }
    )
}.getOrNull()
