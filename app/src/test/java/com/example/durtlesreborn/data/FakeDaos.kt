package com.example.durtlesreborn.data

internal class FakePending : PendingReviewDao {
    val rows = mutableListOf<PendingReview>()
    private var nextId = 1L
    override suspend fun insert(review: PendingReview): Long {
        val id = nextId++
        rows += review.copy(id = id)
        return id
    }
    override suspend fun all() = rows.sortedBy { it.id }
    override suspend fun count() = rows.size
    override suspend fun delete(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun markAttempted(id: Long) {
        val i = rows.indexOfFirst { it.id == id }
        rows[i] = rows[i].copy(attempts = rows[i].attempts + 1)
    }
}

internal class FakeSubjects : SubjectDao {
    val rows = mutableMapOf<Long, SubjectEntity>()
    override fun allFlow() = kotlinx.coroutines.flow.flowOf(rows.values.filter { !it.hidden })
    override suspend fun upsert(subjects: List<SubjectEntity>) { subjects.forEach { rows[it.id] = it } }
    override suspend fun byId(id: Long) = rows[id]
    override suspend fun upToLevel(maxLevel: Int) = rows.values.filter { it.level <= maxLevel && !it.hidden }
    override suspend fun byIds(ids: List<Long>) = ids.mapNotNull { rows[it] }
    override suspend fun kanjiForLevel(level: Int) = rows.values.filter { it.type == "kanji" && !it.hidden && it.level == level }
    override suspend fun count() = rows.size
    override suspend fun vocabularyJsonForLevel(level: Int) = rows.values
        .filter { !it.hidden && it.level == level && (it.type == "vocabulary" || it.type == "kana_vocabulary") }
        .sortedBy { it.id }
        .map { it.json }
}

internal class FakeAssignments : AssignmentDao {
    val rows = mutableMapOf<Long, AssignmentEntity>()
    override fun allFlow() = kotlinx.coroutines.flow.flowOf(rows.values.filter { !it.hidden })
    override suspend fun bySubjects(subjectIds: List<Long>) = rows.values.filter { it.subjectId in subjectIds }
    override suspend fun upsert(assignments: List<AssignmentEntity>) { assignments.forEach { rows[it.id] = it } }
    override suspend fun byId(id: Long) = rows[id]
    override suspend fun bySubject(subjectId: Long) = rows.values.firstOrNull { it.subjectId == subjectId }
    override suspend fun reviewsDue(now: Long) = rows.values.filter { (it.availableAt ?: Long.MAX_VALUE) <= now }
    override suspend fun lessonsAvailable() = rows.values.filter { it.unlockedAt != null && it.startedAt == null }
    override suspend fun upcoming(now: Long) = rows.values.filter { (it.availableAt ?: 0L) > now }.sortedBy { it.availableAt }.take(1)
}

internal class FakeStatistics : ReviewStatisticDao {
    val rows = mutableMapOf<Long, ReviewStatisticEntity>()
    override suspend fun upsert(statistics: List<ReviewStatisticEntity>) { statistics.forEach { rows[it.id] = it } }
    override suspend fun bySubject(subjectId: Long) = rows.values.firstOrNull { it.subjectId == subjectId }
}

internal class FakeMaterials : StudyMaterialDao {
    val rows = mutableMapOf<Long, StudyMaterialEntity>()
    override suspend fun upsert(materials: List<StudyMaterialEntity>) { materials.forEach { rows[it.id] = it } }
    override suspend fun bySubject(subjectId: Long) = rows.values.firstOrNull { it.subjectId == subjectId }
}

internal class FakeState : SyncStateDao {
    val rows = mutableMapOf<String, String>()
    override suspend fun get(key: String) = rows[key]
    override suspend fun put(state: SyncStateEntity) { rows[state.key] = state.value }
}


internal class FakeSrsSystems : SrsSystemDao {
    val rows = mutableMapOf<Long, SrsSystemEntity>()
    override suspend fun upsert(systems: List<SrsSystemEntity>) { systems.forEach { rows[it.id] = it } }
    override suspend fun all() = rows.values.toList()
}

internal class FakeLevels : LevelDao {
    val rows = mutableMapOf<Int, List<LevelRow>>()
    override suspend fun items(level: Int) = rows[level].orEmpty()
}
