package com.example.durtlesreborn.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    /** Read-only feed for the graded reading feature; never written by it. */
    @Query("SELECT * FROM subject WHERE hidden = 0")
    fun allFlow(): Flow<List<SubjectEntity>>

    @Upsert
    suspend fun upsert(subjects: List<SubjectEntity>)

    @Query("SELECT * FROM subject WHERE id = :id")
    suspend fun byId(id: Long): SubjectEntity?

    @Query("SELECT * FROM subject WHERE level <= :maxLevel AND hidden = 0 ORDER BY level, id")
    suspend fun upToLevel(maxLevel: Int): List<SubjectEntity>

    @Query("SELECT * FROM subject WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<SubjectEntity>

    @Query("SELECT * FROM subject WHERE type = 'kanji' AND hidden = 0 AND level = :level")
    suspend fun kanjiForLevel(level: Int): List<SubjectEntity>

    @Query("SELECT COUNT(*) FROM subject")
    suspend fun count(): Int

    /** Stored JSON of one level's vocabulary, for its audio download. */
    @Query(
        "SELECT json FROM subject WHERE type IN ('vocabulary','kana_vocabulary') AND hidden = 0 AND level = :level " +
            "ORDER BY id",
    )
    suspend fun vocabularyJsonForLevel(level: Int): List<String>
}

@Dao
interface AssignmentDao {
    @Upsert
    suspend fun upsert(assignments: List<AssignmentEntity>)

    /** Read-only feed for the graded reading feature; never written by it. */
    @Query("SELECT * FROM assignment WHERE hidden = 0")
    fun allFlow(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignment WHERE id = :id")
    suspend fun byId(id: Long): AssignmentEntity?

    @Query("SELECT * FROM assignment WHERE subjectId IN (:subjectIds)")
    suspend fun bySubjects(subjectIds: List<Long>): List<AssignmentEntity>

    @Query("SELECT * FROM assignment WHERE subjectId = :subjectId")
    suspend fun bySubject(subjectId: Long): AssignmentEntity?

    /** Started, not burned, and available now. */
    @Query(
        "SELECT * FROM assignment WHERE hidden = 0 AND startedAt IS NOT NULL AND burnedAt IS NULL " +
            "AND availableAt IS NOT NULL AND availableAt <= :now ORDER BY availableAt, id",
    )
    suspend fun reviewsDue(now: Long): List<AssignmentEntity>

    /** Started, not burned, and becoming available after [now], soonest first. */
    @Query(
        "SELECT * FROM assignment WHERE hidden = 0 AND startedAt IS NOT NULL AND burnedAt IS NULL " +
            "AND availableAt IS NOT NULL AND availableAt > :now ORDER BY availableAt LIMIT 1",
    )
    suspend fun upcoming(now: Long): List<AssignmentEntity>

    /** Unlocked but not yet started. */
    @Query("SELECT * FROM assignment WHERE hidden = 0 AND unlockedAt IS NOT NULL AND startedAt IS NULL ORDER BY id")
    suspend fun lessonsAvailable(): List<AssignmentEntity>
}

@Dao
interface ReviewStatisticDao {
    @Upsert
    suspend fun upsert(statistics: List<ReviewStatisticEntity>)

    @Query("SELECT * FROM review_statistic WHERE subjectId = :subjectId")
    suspend fun bySubject(subjectId: Long): ReviewStatisticEntity?
}

@Dao
interface StudyMaterialDao {
    @Upsert
    suspend fun upsert(materials: List<StudyMaterialEntity>)

    @Query("SELECT * FROM study_material WHERE subjectId = :subjectId")
    suspend fun bySubject(subjectId: Long): StudyMaterialEntity?
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(state: SyncStateEntity)
}

@Dao
interface SrsSystemDao {
    @Upsert
    suspend fun upsert(systems: List<SrsSystemEntity>)

    @Query("SELECT * FROM srs_system")
    suspend fun all(): List<SrsSystemEntity>
}

/** A subject in search results, with the user's SRS stage if they have an assignment for it. */
data class SearchRow(
    val id: Long,
    val type: String,
    val level: Int,
    val characters: String?,
    val slug: String?,
    val srsStage: Int?,
    val json: String,
)

@Dao
interface SearchDao {
    /**
     * Subjects up to [maxGrantedLevel] whose search text contains [q1] or [q2] (empty matches
     * everything), filtered by type, level and SRS stage range ([stageMin] -1 means any).
     */
    @Query(
        "SELECT s.id AS id, s.type AS type, s.level AS level, s.characters AS characters, s.slug AS slug, " +
            "a.srsStage AS srsStage, s.json AS json FROM subject s LEFT JOIN assignment a ON a.subjectId = s.id " +
            "WHERE s.hidden = 0 AND s.level <= :maxGrantedLevel AND s.level BETWEEN :minLevel AND :maxLevel " +
            "AND s.type IN (:types) " +
            "AND (:q1 = '' OR s.searchText LIKE '%' || :q1 || '%' OR (:q2 != '' AND s.searchText LIKE '%' || :q2 || '%')) " +
            "AND (:stageMin = -1 OR COALESCE(a.srsStage, 0) BETWEEN :stageMin AND :stageMax) " +
            "ORDER BY (s.characters = :q1 OR s.slug = :q1) DESC, s.level, s.id LIMIT :limit",
    )
    suspend fun search(
        q1: String,
        q2: String,
        types: List<String>,
        minLevel: Int,
        maxLevel: Int,
        maxGrantedLevel: Int,
        stageMin: Int,
        stageMax: Int,
        limit: Int,
    ): List<SearchRow>
}

/** What the notification and widget need, computed in one query. */
data class AlertCounts(
    val numLessons: Int,
    val numReviews: Int,
    val newestAvailableAt: Long?,
    val upcomingAvailableAt: Long?,
)

@Dao
interface AlertDao {
    @Query(
        "SELECT " +
            "(SELECT COUNT(*) FROM assignment a JOIN subject s ON s.id = a.subjectId WHERE a.hidden = 0 AND s.hidden = 0 " +
            "AND s.level <= :maxLevel AND a.unlockedAt IS NOT NULL AND a.startedAt IS NULL) AS numLessons, " +
            "(SELECT COUNT(*) FROM assignment a JOIN subject s ON s.id = a.subjectId WHERE a.hidden = 0 AND s.hidden = 0 " +
            "AND s.level <= :maxLevel AND a.startedAt IS NOT NULL AND a.burnedAt IS NULL AND a.availableAt IS NOT NULL " +
            "AND a.availableAt <= :now) AS numReviews, " +
            "(SELECT MAX(a.availableAt) FROM assignment a JOIN subject s ON s.id = a.subjectId WHERE a.hidden = 0 AND s.hidden = 0 " +
            "AND s.level <= :maxLevel AND a.startedAt IS NOT NULL AND a.burnedAt IS NULL AND a.availableAt IS NOT NULL " +
            "AND a.availableAt <= :now) AS newestAvailableAt, " +
            "(SELECT MIN(a.availableAt) FROM assignment a JOIN subject s ON s.id = a.subjectId WHERE a.hidden = 0 AND s.hidden = 0 " +
            "AND s.level <= :maxLevel AND a.startedAt IS NOT NULL AND a.burnedAt IS NULL AND a.availableAt IS NOT NULL " +
            "AND a.availableAt > :now) AS upcomingAvailableAt",
    )
    suspend fun counts(maxLevel: Int, now: Long): AlertCounts
}
