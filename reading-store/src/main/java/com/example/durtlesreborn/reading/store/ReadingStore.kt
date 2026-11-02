package com.example.durtlesreborn.reading.store

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, MetaEntity::class, ArticleEntity::class, ExampleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun dao(): ReadingDao
}

/** The three numbers the cache policy needs per article. */
data class CachedArticle(val id: Long, val fetchedAt: Long, val sizeBytes: Long)

/** Which articles to drop to stay inside the retention window and the size budget. */
object CachePolicy {
    fun deletions(
        articles: List<CachedArticle>,
        now: Long,
        retentionMillis: Long,
        maxBytes: Long,
    ): List<Long> {
        val keep = ArrayList<CachedArticle>(articles.size)
        val drop = ArrayList<Long>()
        val cutoff = now - retentionMillis
        for (article in articles) {
            if (article.fetchedAt < cutoff) drop += article.id else keep += article
        }
        var total = keep.sumOf { it.sizeBytes }
        for (article in keep.sortedBy { it.fetchedAt }) {
            if (total <= maxBytes) break
            drop += article.id
            total -= article.sizeBytes
        }
        return drop
    }
}

/**
 * Owns `reading.db`. Nothing here touches the app's own database, and every failure is caught: a
 * damaged file is deleted and recreated empty, so the rest of the app never sees an exception.
 */
class ReadingStore(
    private val context: Context,
    private val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var database: ReadingDatabase? = null

    private fun db(): ReadingDatabase = database ?: synchronized(this) {
        database ?: Room.databaseBuilder(context, ReadingDatabase::class.java, DB_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
            .also { database = it }
    }

    val dao: ReadingDao get() = db().dao()

    /**
     * Runs [block] against the database; if the file turns out to be damaged it is recreated empty
     * and the block is tried once more. The retry is what makes deletion-by-OS harmless.
     */
    private suspend fun <T> safely(block: suspend (ReadingDao) -> T): T {
        return try {
            block(dao)
        } catch (e: SQLiteException) {
            recreate()
            block(dao)
        }
    }

    private fun recreate() {
        synchronized(this) {
            runCatching { database?.close() }
            database = null
            context.deleteDatabase(DB_NAME)
        }
    }

    suspend fun sources(): List<SourceEntity> = safely { it.sources() }

    /** Whether the default sources have ever been added. Removing them all then sticks. */
    suspend fun seeded(): Boolean = safely { it.meta(SEEDED_KEY) != null }

    suspend fun markSeeded() = safely { it.putMeta(MetaEntity(SEEDED_KEY, "1")) }

    suspend fun addSource(name: String, url: String, kind: String): Long = safely {
        it.addSource(SourceEntity(name = name, url = url, kind = kind, addedAt = clock()))
    }

    suspend fun deleteSource(id: Long) = safely { it.deleteSource(id) }

    suspend fun updateSource(id: Long, url: String, kind: String) = safely { it.updateSource(id, url, kind) }

    suspend fun articles(): List<ArticleEntity> = safely { it.articles() }

    suspend fun article(id: Long): ArticleEntity? = safely { it.article(id) }

    suspend fun articleAt(sourceId: Long, url: String): ArticleEntity? = safely { it.articleAt(sourceId, url) }

    suspend fun insertArticle(article: ArticleEntity): Long = safely { it.insertArticle(article) }

    suspend fun updateScore(id: Long, known: Int, uncovered: Int, kanji: Int, ready: Int?) =
        safely { it.updateScore(id, known, uncovered, kanji, ready) }

    suspend fun examples(subjectId: Int, limit: Int): List<ExampleRow> = safely { it.examples(subjectId, limit) }

    suspend fun insertExamples(examples: List<ExampleEntity>) = safely { it.insertExamples(examples) }

    /** Drops articles older than the retention window and then the oldest until under the size budget. */
    suspend fun prune() = safely { dao ->
        val articles = dao.articlesOldestFirst().map { CachedArticle(it.id, it.fetchedAt, it.sizeBytes) }
        val drop = CachePolicy.deletions(
            articles = articles,
            now = clock(),
            retentionMillis = retentionDays * DAY_MILLIS,
            maxBytes = maxBytes,
        )
        for (id in drop) {
            dao.deleteExamplesFor(id)
            dao.deleteArticle(id)
        }
    }

    /** "Clear all reading data": article text, tokens, scores and example sentences. */
    suspend fun clearAll() = safely { dao ->
        dao.clearExamples()
        dao.clearArticles()
    }

    companion object {
        const val DB_NAME = "reading.db"
        private const val SEEDED_KEY = "default_sources_seeded"
        const val DEFAULT_RETENTION_DAYS = 30
        const val DEFAULT_MAX_BYTES = 50L * 1024 * 1024
        private const val DAY_MILLIS = 24 * 3_600_000L
    }
}
