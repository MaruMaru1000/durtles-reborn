package com.example.durtlesreborn.data

import com.example.durtlesreborn.reading.core.AssignmentLite
import com.example.durtlesreborn.reading.core.SubjectLite
import com.example.durtlesreborn.reading.core.SubjectType
import com.example.durtlesreborn.reading.core.WaniKaniReadOnly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app's implementation of the reading feature's read-only view of WaniKani. It only queries the
 * existing database; the reading modules never write to it or change its schema.
 */
class AppWaniKaniReadOnly(private val database: AppDatabase) : WaniKaniReadOnly {
    override fun subjects(): Flow<List<SubjectLite>> =
        database.subjects().allFlow().map { subjects -> subjects.map(::toLite) }

    override fun assignments(): Flow<List<AssignmentLite>> =
        database.assignments().allFlow().map { assignments ->
            assignments.map { AssignmentLite(it.subjectId.toInt(), it.srsStage) }
        }

    private fun toLite(entity: SubjectEntity): SubjectLite {
        val content = runCatching { SubjectContent.parse(entity.json) }.getOrNull()
        val meanings = content?.meanings?.filter { it.acceptedAnswer }?.map { it.text }
            ?.ifEmpty { content.meanings.map { it.text } }
            .orEmpty()
        val readings = content?.readings?.filter { it.acceptedAnswer }?.map { it.reading }
            ?.ifEmpty { content.readings.map { it.reading } }
            .orEmpty()
        return SubjectLite(
            id = entity.id.toInt(),
            type = when (entity.type) {
                "radical" -> SubjectType.RADICAL
                "kanji" -> SubjectType.KANJI
                "kana_vocabulary" -> SubjectType.KANA_VOCABULARY
                else -> SubjectType.VOCABULARY
            },
            characters = entity.characters,
            readings = readings,
            meanings = meanings,
            level = entity.level,
        )
    }
}
