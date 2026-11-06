package com.alisttracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** TMDB (free API) look-ups for posters and movie details. */
object Posters {
    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")

    private fun get(token: String, path: String, query: String): String {
        val bearer = token.length > 40          // v4 read-access token vs. v3 api key
        val key = if (bearer) "" else "&api_key=$token"
        val c = URL("https://api.themoviedb.org/3/$path?$query$key").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("accept", "application/json")
        if (bearer) c.setRequestProperty("Authorization", "Bearer $token")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /**
     * Picks the right film among search results:
     * exact-title matches first; a film released in the year watched (or the year before) wins,
     * unless a much better-known film has the same title (re-releases such as classics).
     */
    private fun pick(results: JSONArray, title: String, year: Int): JSONObject? {
        val all = (0 until results.length()).map { results.getJSONObject(it) }
        if (all.isEmpty()) return null
        val t = norm(title)
        val exact = all.filter { norm(it.str("title")) == t || norm(it.str("original_title")) == t }
        val pool = (if (exact.isNotEmpty()) exact else all).let { l -> l.filter { it.str("poster_path").isNotBlank() }.ifEmpty { l } }
        val bestOverall = pool.maxByOrNull { it.optInt("vote_count", 0) } ?: return null
        val inYear = pool.filter {
            val y = it.str("release_date").take(4).toIntOrNull()
            year > 0 && y != null && (y == year || y == year - 1)
        }.maxByOrNull { it.optInt("vote_count", 0 ) }
        if (inYear == null) return bestOverall
        val big = bestOverall.optInt("vote_count", 0)
        return if (big >= 1000 && big >= 20 * inYear.optInt("vote_count", 0)) bestOverall else inYear
    }

    private val trailingYear = Regex("""^(.*\S)\s*[\s(]\s*((?:19|20)\d{2})\)?\s*$""")

    /** "Back to the Future 1985" or "Back to the Future (1985)" pins the release year. */
    private fun search(token: String, title: String, watchYear: Int): JSONObject? {
        val m = trailingYear.find(title.trim())
        if (m != null) {
            val clean = m.groupValues[1].trim()
            val y = m.groupValues[2].toInt()
            val q = URLEncoder.encode(clean, "UTF-8")
            val res = JSONObject(get(token, "search/movie", "query=$q&include_adult=false&primary_release_year=$y")).getJSONArray("results")
            if (res.length() > 0) return pick(res, clean, y)
        }
        val q = URLEncoder.encode(title.trim(), "UTF-8")
        val res = JSONObject(get(token, "search/movie", "query=$q&include_adult=false")).getJSONArray("results")
        return pick(res, title, watchYear)
    }

    /** Poster path, "-" when nothing was found, null on a network error. */
    suspend fun lookup(token: String, title: String, year: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val r = search(token, title, year)
            val p = r?.str("poster_path").orEmpty()
            if (p.isBlank()) "-" else p
        }.getOrNull()
    }

    /** Compact JSON with synopsis, cast, director, etc. Null on a network error or no match. */
    suspend fun details(token: String, title: String, year: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val id = search(token, title, year)?.getInt("id") ?: return@runCatching null
            val d = JSONObject(get(token, "movie/$id", "append_to_response=credits&language=en-US"))
            val out = JSONObject()
            out.put("overview", d.str("overview"))
            out.put("tagline", d.str("tagline"))
            out.put("runtime", d.optInt("runtime", 0))
            out.put("release", d.str("release_date"))
            out.put("rating", d.optDouble("vote_average", 0.0))
            out.put("poster", d.str("poster_path"))
            out.put("backdrop", d.str("backdrop_path"))
            val genres = JSONArray()
            d.optJSONArray("genres")?.let { g -> for (i in 0 until g.length()) genres.put(g.getJSONObject(i).str("name")) }
            out.put("genres", genres)
            val credits = d.optJSONObject("credits")
            val directors = ArrayList<String>()
            credits?.optJSONArray("crew")?.let { c ->
                for (i in 0 until c.length()) {
                    val o = c.getJSONObject(i)
                    if (o.str("job") == "Director") directors.add(o.str("name"))
                }
            }
            out.put("director", directors.joinToString(", "))
            val cast = JSONArray()
            credits?.optJSONArray("cast")?.let { c ->
                for (i in 0 until minOf(c.length(), 14)) {
                    val o = c.getJSONObject(i)
                    cast.put(JSONObject().put("name", o.str("name")).put("character", o.str("character")).put("photo", o.str("profile_path")))
                }
            }
            out.put("cast", cast)
            out.toString()
        }.getOrNull()
    }

    data class Hit(val id: Int, val title: String, val release: String, val poster: String)

    /** Free-text movie search for the watchlist. Empty on a network error. */
    suspend fun searchMany(token: String, query: String): List<Hit> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val res = JSONObject(get(token, "search/movie", "query=$q&include_adult=false")).getJSONArray("results")
            (0 until minOf(res.length(), 15)).map {
                val o = res.getJSONObject(it)
                Hit(o.getInt("id"), o.str("title"), o.str("release_date"), o.str("poster_path"))
            }
        }.getOrDefault(emptyList())
    }

    /** All posters TMDB has for this film (English or text-free first). Empty on a network error. */
    suspend fun posterOptions(token: String, title: String, year: Int): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val r = search(token, title, year) ?: return@runCatching emptyList<String>()
            val id = r.getInt("id")
            val posters = JSONObject(get(token, "movie/$id/images", "include_image_language=en,null")).optJSONArray("posters") ?: JSONArray()
            val list = (0 until posters.length()).map { posters.getJSONObject(it).str("file_path") }.filter { it.isNotBlank() }
            val first = r.str("poster_path")
            (listOfNotNull(first.ifBlank { null }) + list).distinct().take(30)
        }.getOrDefault(emptyList())
    }
}
