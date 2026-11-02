package com.example.durtlesreborn.reading.store

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "source")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    /** NHK_EASY, RSS or HTML_INDEX. */
    val kind: String,
    val addedAt: Long,
)

/** Tiny key/value table: keeps "the defaults were seeded" so removing every source sticks. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "article", indices = [Index("sourceId"), Index("fetchedAt")])
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val sourceName: String,
    val url: String,
    val title: String,
    val text: String,
    /** [com.example.durtlesreborn.reading.core.TokenCodec] output. */
    @ColumnInfo(name = "tokens") val encodedTokens: String,
    val fetchedAt: Long,
    val knownPercent: Int,
    val uncoveredPercent: Int,
    val knownKanjiPercent: Int,
    val readyLevel: Int?,
    val sizeBytes: Long,
)

/** One example sentence for a WaniKani item, kept apart from the app's own database. */
@Entity(tableName = "example", indices = [Index("subjectId"), Index("articleId")])
data class ExampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Int,
    val sentence: String,
    val articleId: Long,
    val createdAt: Long,
)

/** What the bridge returns for "seen in your reading". */
data class ExampleRow(val sentence: String, val articleTitle: String, val sourceName: String, val articleId: Long)

data class ArticleSummary(
    val id: Long,
    val title: String,
    val sourceName: String,
    val url: String,
    val knownPercent: Int,
    val uncoveredPercent: Int,
    val knownKanjiPercent: Int,
    val readyLevel: Int?,
    val fetchedAt: Long,
)

@Dao
interface ReadingDao {
    @Query("SELECT value FROM meta WHERE key = :key")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: MetaEntity)

    @Query("SELECT * FROM source ORDER BY addedAt")
    suspend fun sources(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSource(source: SourceEntity): Long

    @Query("DELETE FROM source WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("UPDATE source SET url = :url, kind = :kind WHERE id = :id")
    suspend fun updateSource(id: Long, url: String, kind: String)

    @Query("SELECT * FROM article ORDER BY fetchedAt DESC")
    suspend fun articles(): List<ArticleEntity>

    @Query("SELECT * FROM article WHERE id = :id")
    suspend fun article(id: Long): ArticleEntity?

    @Query("SELECT * FROM article WHERE sourceId = :sourceId AND url = :url")
    suspend fun articleAt(sourceId: Long, url: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity): Long

    @Query("UPDATE article SET knownPercent = :known, uncoveredPercent = :uncovered, knownKanjiPercent = :kanji, readyLevel = :ready WHERE id = :id")
    suspend fun updateScore(id: Long, known: Int, uncovered: Int, kanji: Int, ready: Int?)

    @Query("DELETE FROM article WHERE id = :id")
    suspend fun deleteArticle(id: Long)

    @Query("DELETE FROM article")
    suspend fun clearArticles()

    @Query("DELETE FROM example")
    suspend fun clearExamples()

    @Query("DELETE FROM example WHERE articleId = :articleId")
    suspend fun deleteExamplesFor(articleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamples(examples: List<ExampleEntity>)

    @Query("SELECT e.sentence, a.title AS articleTitle, a.sourceName AS sourceName, a.id AS articleId " +
        "FROM example e JOIN article a ON a.id = e.articleId " +
        "WHERE e.subjectId = :subjectId ORDER BY e.createdAt DESC LIMIT :limit")
    suspend fun examples(subjectId: Int, limit: Int): List<ExampleRow>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM article")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM article ORDER BY fetchedAt ASC")
    suspend fun articlesOldestFirst(): List<ArticleEntity>

    @Query("SELECT COUNT(*) FROM article")
    suspend fun articleCount(): Int
}
