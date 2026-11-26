package com.kanjiforge.app.data.reference

import com.kanjiforge.app.data.reference.model.KanaSummary
import com.kanjiforge.app.data.reference.model.KanjiDetail
import com.kanjiforge.app.data.reference.model.KanjiFilter
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.OutlierCharacterSummary
import com.kanjiforge.app.data.reference.model.OutlierSummary
import com.kanjiforge.app.data.reference.model.PagedResult
import com.kanjiforge.app.data.reference.model.QuizCandidateInfo
import com.kanjiforge.app.data.reference.model.RadicalSummary
import com.kanjiforge.app.data.reference.model.SequenceKanjiRef
import com.kanjiforge.app.data.reference.model.StudyDeckLevel
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.data.reference.model.WordFilter
import com.kanjiforge.app.data.reference.model.WordSummary
import com.kanjiforge.app.domain.Rating

interface ReferenceCatalog : AutoCloseable {
    suspend fun listStudyDeckLevels(family: StudySequenceFamily): List<StudyDeckLevel>
    suspend fun listStudyDeckRefs(family: StudySequenceFamily): Map<Int, List<SequenceKanjiRef>>
    suspend fun searchStudyDeck(
        family: StudySequenceFamily,
        level: Int,
        offset: Int = 0,
        limit: Int = 200,
    ): List<KanjiSummary>

    suspend fun searchKanji(
        query: String,
        filters: KanjiFilter = KanjiFilter(),
        offset: Int = 0,
        limit: Int = 60,
        savedCodes: Set<Int> = emptySet(),
        dueCodes: Set<Int> = emptySet(),
        reviewCodesByRating: Map<Rating, Set<Int>> = emptyMap(),
        reviewedCodes: Set<Int> = emptySet(),
    ): PagedResult<KanjiSummary>

    suspend fun getKanji(character: String): KanjiDetail?
    suspend fun getKanjiByCode(code: Int): KanjiDetail?
    suspend fun getKanjiSummaries(characters: Collection<String>): Map<String, KanjiSummary>
    suspend fun getQuizCandidates(
        codes: Collection<Int>,
        includeWords: Boolean,
        includeSentences: Boolean,
    ): Map<Int, QuizCandidateInfo>

    suspend fun searchWords(
        query: String,
        filters: WordFilter = WordFilter(),
        offset: Int = 0,
        limit: Int = 50,
    ): PagedResult<WordSummary>

    suspend fun getWord(id: Long): WordSummary?
    suspend fun getWords(ids: Collection<Long>): Map<Long, WordSummary>

    suspend fun listKana(): List<KanaSummary>
    suspend fun listAllRadicals(): List<RadicalSummary>
    suspend fun searchOutlier(query: String, offset: Int = 0, limit: Int = 50): PagedResult<OutlierSummary>
    suspend fun listAllOutlierCharacters(): List<OutlierCharacterSummary>
}
