package com.kanjiforge.app.domain

import com.kanjiforge.app.data.reference.model.StudySequenceFamily

sealed interface SessionSpec {
    data object DailyReview : SessionSpec
    data class SingleKanji(val code: Int) : SessionSpec
    data class Collection(val setId: String) : SessionSpec
    data class SequenceSet(
        val family: StudySequenceFamily,
        val level: Int,
        val offset: Int,
        val batchSize: Int = 10,
    ) : SessionSpec
}
