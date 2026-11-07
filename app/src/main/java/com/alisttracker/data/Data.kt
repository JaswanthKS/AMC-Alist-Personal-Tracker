package com.alisttracker.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Entity(tableName = "movies")
data class Movie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,              // yyyy-MM-dd
    val title: String,
    val ticket: Double = 0.0,
    val fee: Double = 0.0,
    val format: String = "Standard",
    val language: String = "English",
    val theater: String = "",
    val screen: Int? = null,
    val showtime: String = "",     // HH:mm
    val alist: String = "Yes",     // Yes / No / Gift Card / Voucher
    val notes: String = "",
    val swag: String = "",
    val amc: Boolean = true,
    @ColumnInfo(defaultValue = "0") val threeD: Boolean = false,
    @ColumnInfo(defaultValue = "''") val posterPath: String = "",
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,   // 0 = not rated, 1..5 stars
    @ColumnInfo(defaultValue = "0") val concessions: Double = 0.0,   // popcorn / drinks spend for this visit
    @ColumnInfo(defaultValue = "''") val concessionsPay: String = ""   // Cash / Credit Card / Rewards / Gift Card / Other
) {
    val total: Double get() = ticket + fee
}

@Entity(tableName = "watchlist")
data class WatchItem(
    @PrimaryKey val tmdbId: Int,
    val title: String,
    val releaseDate: String = "",
    val posterPath: String = "",
    val addedAt: Long = 0L
)

@Entity(tableName = "movie_info")
data class MovieInfo(@PrimaryKey val title: String, val json: String, val fetchedAt: Long)

data class TitleDate(val title: String, val date: String)

@Entity(tableName = "options", primaryKeys = ["type", "value"])
data class Option(val type: String, val value: String, val sortOrder: Int = 0)

object OptType {
    const val THEATER = "theater"
    const val FORMAT = "format"
    const val LANGUAGE = "language"
    const val SWAG = "swag"
}

@Entity(tableName = "subs", primaryKeys = ["year", "month"])
data class SubPayment(val year: Int, val month: Int, val amount: Double)

@Dao
interface AppDao {
    @Query("SELECT * FROM movies ORDER BY date DESC, showtime DESC, id DESC")
    fun movies(): Flow<List<Movie>>

    @Query("SELECT * FROM subs ORDER BY year, month")
    fun subs(): Flow<List<SubPayment>>

    @Query("SELECT * FROM subs")
    suspend fun allSubs(): List<SubPayment>

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun movieCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: Movie): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(m: List<Movie>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSub(s: SubPayment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubs(s: List<SubPayment>)

    @Delete
    suspend fun delete(m: Movie)

    @Query("SELECT * FROM watchlist ORDER BY CASE WHEN releaseDate = '' THEN 1 ELSE 0 END, releaseDate ASC, addedAt DESC")
    fun watchlist(): Flow<List<WatchItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addWatch(w: WatchItem)

    @Delete
    suspend fun deleteWatch(w: WatchItem)

    @Query("SELECT * FROM options ORDER BY type, sortOrder, value")
    fun options(): Flow<List<Option>>

    @Query("SELECT COUNT(*) FROM options")
    suspend fun optionCount(): Int

    @Query("SELECT * FROM movies")
    suspend fun allMovies(): List<Movie>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addOption(o: Option)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addOptions(o: List<Option>)

    @Query("SELECT title, MIN(date) AS date FROM movies WHERE posterPath = '' GROUP BY title")
    suspend fun titlesWithoutPoster(): List<TitleDate>

    @Query("UPDATE movies SET posterPath = :p WHERE title = :t")
    suspend fun forcePoster(t: String, p: String)

    @Query("SELECT * FROM movie_info")
    fun allInfo(): Flow<List<MovieInfo>>

    @Query("DELETE FROM movie_info WHERE title = :t")
    suspend fun deleteInfo(t: String)

    @Query("UPDATE movies SET posterPath = '' WHERE title = :t")
    suspend fun clearPoster(t: String)

    @Query("DELETE FROM movies")
    suspend fun deleteAllMovies()

    @Query("DELETE FROM subs")
    suspend fun deleteAllSubs()

    @Query("DELETE FROM options")
    suspend fun deleteAllOptions()

    @Query("DELETE FROM movie_info")
    suspend fun clearAllInfo()

    @Query("UPDATE movies SET posterPath = ''")
    suspend fun clearAllPosters()

    @Query("SELECT json FROM movie_info WHERE title = :t")
    suspend fun info(t: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInfo(i: MovieInfo)

    @Query("UPDATE movies SET posterPath = :p WHERE title = :t AND posterPath = ''")
    suspend fun setPoster(t: String, p: String)

    @Delete
    suspend fun deleteOption(o: Option)
}

@Database(entities = [Movie::class, SubPayment::class, Option::class, MovieInfo::class, WatchItem::class], version = 9, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "alist.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build().also { inst = it }
        }
    }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `options` (`type` TEXT NOT NULL, `value` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`type`, `value`))")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `movies` ADD COLUMN `posterPath` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `movie_info` (`title` TEXT NOT NULL, `json` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`title`))")
    }
}

/** Concessions spend and how it was paid. */
val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `movies` ADD COLUMN `concessions` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `movies` ADD COLUMN `concessionsPay` TEXT NOT NULL DEFAULT ''")
    }
}

/** Star rating on each visit. */
val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `movies` ADD COLUMN `rating` INTEGER NOT NULL DEFAULT 0")
    }
}

/** 3D flag on each visit. */
val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `movies` ADD COLUMN `threeD` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Watchlist table. */
val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `watchlist` (`tmdbId` INTEGER NOT NULL, `title` TEXT NOT NULL, `releaseDate` TEXT NOT NULL, `posterPath` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`tmdbId`))")
    }
}

/** Matching got smarter: clear cached posters/details so they are fetched again. */
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `movie_info`")
        db.execSQL("UPDATE `movies` SET `posterPath` = ''")
    }
}

/** Fills the dropdown lists (theaters, formats, ...) from the Excel "Lists" sheet plus anything already logged. */
suspend fun seedOptionsIfEmpty(dao: AppDao) {
    if (dao.optionCount() > 0) return
    val movies = dao.allMovies()
    fun mk(type: String, base: List<String>, extra: List<String>) =
        (base + extra).filter { it.isNotBlank() }.distinct().mapIndexed { i, v -> Option(type, v, i) }
    dao.addOptions(
        mk(OptType.THEATER, listOf("AMC Empire 25", "AMC Lincoln Square 13", "AMC Newport 11", "AMC 34th Street 14"), movies.map { it.theater }) +
        mk(OptType.FORMAT, listOf("IMAX", "IMAX 70mm", "70mm", "Dolby Cinema", "Prime", "Standard", "XL", "Vistavision"), movies.map { it.format }) +
        mk(OptType.LANGUAGE, listOf("English", "Telugu", "Tamil", "Hindi"), movies.map { it.language }) +
        mk(OptType.SWAG, listOf("Poster", "Pin", "Collectible Ticket", "Popcorn Bucket", "Key Chain", "Other"), movies.map { it.swag })
    )
}

/** First-launch import of the data exported from the Excel tracker (assets/seed.json). */
suspend fun seedIfEmpty(ctx: Context, dao: AppDao) {
    if (dao.movieCount() > 0) return
    val json = JSONObject(ctx.assets.open("seed.json").bufferedReader().use { it.readText() })
    val ms = json.getJSONArray("movies")
    val movies = (0 until ms.length()).map {
        val o = ms.getJSONObject(it)
        Movie(
            date = o.getString("date"), title = o.getString("title"),
            ticket = o.getDouble("ticket"), fee = o.getDouble("fee"),
            format = o.getString("format"), language = o.getString("language"),
            theater = o.getString("theater"),
            screen = if (o.isNull("screen")) null else o.getInt("screen"),
            showtime = o.getString("showtime"), alist = o.getString("alist"),
            notes = o.getString("notes"), swag = o.getString("swag"),
            amc = o.getBoolean("amc")
        )
    }
    dao.upsertAll(movies)
    val ss = json.getJSONArray("subs")
    dao.upsertSubs((0 until ss.length()).map {
        val o = ss.getJSONObject(it)
        SubPayment(o.getInt("year"), o.getInt("month"), o.getDouble("amount"))
    })
}
