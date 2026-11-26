package com.kanjiforge.app.data.reference.model

import com.kanjiforge.app.domain.FuriganaSegment
import com.kanjiforge.app.domain.formatFuriganaPlain
import com.kanjiforge.app.domain.parseFurigana

enum class PartOfSpeech(val label: String) {
    NOUN("Noun"),
    VERB("Verb"),
    ADJ_I("i-adj"),
    ADJ_NA("na-adj"),
    ADVERB("Adverb");

    fun matchesTags(tags: Collection<String>): Boolean = when (this) {
        NOUN -> "n" in tags
        VERB -> tags.any { tag ->
            tag == "v1" || tag == "v1s" || tag == "vt" || tag == "vi" ||
                tag == "vk" || tag == "vz" || tag == "vn" || tag == "vr" ||
                tag == "vidg" || tag == "vunspec" ||
                tag.startsWith("vs") || tag.startsWith("v5") ||
                tag.startsWith("v4") || tag.startsWith("v2")
        }
        ADJ_I -> "adji" in tags || "adjix" in tags
        ADJ_NA -> "adjna" in tags
        ADVERB -> "adv" in tags || "advto" in tags
    }
}

enum class StudyRatingFilter { UNSTUDIED, AGAIN, HARD, GOOD, EASY }

data class KanjiFilter(
    val jlpt: Int? = null,
    val grade: Int? = null,
    val strokeCount: Int? = null,
    val radical: Int? = null,
    val savedOnly: Boolean = false,
    val dueOnly: Boolean = false,
    val studyRating: StudyRatingFilter? = null,
)

enum class StudySequenceFamily(
    val title: String,
    val levelColumn: String?,
    val rankColumn: String,
    val levelsDescending: Boolean = false,
) {
    JLPT("JLPT levels", "jlpt_level", "jlpt_sequence", levelsDescending = true),
    JLPT_REVISED("JLPT levels (revised)", "jlpt_revised_level", "jlpt_revised_sequence", levelsDescending = true),
    SCHOOL_GRADES("Japanese School Grades", "jouyou_level", "jouyou_sequence"),
    KLC("Kanji Learner’s Course", "kklc_level", "kklc_sequence"),
    JOUYOU_REVISED("Jouyou (2020 revision)", "jouyou_revised_level", "jouyou_revised_sequence"),
    MEDIA_FREQUENCY("Frequency in media", "freq_level", "freq_sequence"),
    MEDIA_FREQUENCY_REVISED("Frequency in media (2023 revision)", "freq_revised_level", "freq_revised_sequence"),
    HEISIG("Heisig", "heisig_level", "heisig_sequence"),
    HEISIG_REVISED("Heisig (revised)", "heisig_revised_level", "heisig_revised_sequence"),
    KANKEN("Kanken", "kanken_level", "kanken_sequence"),
    KANKEN_REVISED("Kanken (revised)", "kanken_revised_level", "kanken_revised_sequence"),
    HADAMITZKY("Hadamitzky", null, "hadamitzky_sequence"),
    KIC("Kanji in Context", "kic_level", "kic_sequence");

    val description: String
        get() = when (this) {
            JLPT -> "JLPT levels, from N5 beginner kanji to N1 advanced kanji."
            JLPT_REVISED -> "A revised ordering of the JLPT kanji levels."
            SCHOOL_GRADES -> "Kanji ordered by Japanese school grade."
            KLC -> "The Kanji Learner's Course step-by-step order."
            JOUYOU_REVISED -> "The revised Joyo kanji school-use order."
            MEDIA_FREQUENCY -> "Kanji ranked by frequency in published media."
            MEDIA_FREQUENCY_REVISED -> "The revised media-frequency ranking."
            HEISIG -> "Heisig's Remembering the Kanji sequence."
            HEISIG_REVISED -> "The revised Heisig sequence."
            KANKEN -> "Kanji ordered by Kanken proficiency levels."
            KANKEN_REVISED -> "The revised Kanken ordering."
            HADAMITZKY -> "A single continuous reference order."
            KIC -> "The Kanji in Context learning sequence."
        }

    val rangeLabel: String
        get() = when (this) {
            JLPT, JLPT_REVISED -> "N5 to N1"
            SCHOOL_GRADES, JOUYOU_REVISED -> "School grades"
            MEDIA_FREQUENCY, MEDIA_FREQUENCY_REVISED -> "By rank"
            HADAMITZKY -> "Full order"
            else -> "By level"
        }

    fun levelLabel(level: Int): String = when (this) {
        JLPT, JLPT_REVISED -> "N$level"
        SCHOOL_GRADES, JOUYOU_REVISED -> when (level) {
            in 1..6 -> "Primary $level"
            in 7..9 -> "Secondary ${level - 6}"
            10 -> "Advanced"
            else -> "Level $level"
        }
        MEDIA_FREQUENCY, MEDIA_FREQUENCY_REVISED -> {
            val first = (level - 1) * 100 + 1
            "#$first–#${level * 100}"
        }
        HADAMITZKY -> "Full sequence"
        else -> "Level $level"
    }

    fun deckTitle(level: Int): String = when (this) {
        JLPT, JLPT_REVISED -> {
            val descriptor = when (level) {
                5 -> "Beginner"
                4 -> "Elementary"
                3 -> "Intermediate"
                2 -> "Upper intermediate"
                else -> "Advanced"
            }
            "${levelLabel(level)} - $descriptor"
        }
        else -> levelLabel(level)
    }
}

data class StudyDeckLevel(val family: StudySequenceFamily, val level: Int, val count: Int)

data class SequenceKanjiRef(val level: Int, val code: Int, val character: String)

data class PagedResult<T>(val items: List<T>, val hasMore: Boolean)

data class WordFilter(
    val jlpt: Int? = null,
    val partOfSpeech: PartOfSpeech? = null,
    val commonOnly: Boolean = false,
)

data class KanjiSummary(
    val code: Int,
    val character: String,
    val meaning: String,
    val strokeCount: Int?,
    val jlpt: Int?,
    val grade: Int?,
    val sequence: Int?,
)

data class RadicalLink(
    val code: Int,
    val character: String,
    val meaning: String,
    val occurrences: Int?,
)

data class KanjiDetail(
    val summary: KanjiSummary,
    val translation: String,
    val on: List<String>,
    val kun: List<String>,
    val nanori: List<String>,
    val romanizedReading: String,
    val strokePaths: List<String>,
    val radicals: List<RadicalLink>,
    val decomposition: String,
    val classicalRadical: Int?,
    val notes: String,
    val words: List<WordSummary>,
    val sentences: List<SentenceSummary>,
    val names: List<NameSummary>,
)

data class QuizCandidateInfo(
    val summary: KanjiSummary,
    val readings: List<String>,
    val sampleWord: String?,
    val sampleSentence: String?,
)

data class WordSummary(
    val id: Long,
    val forms: List<String>,
    val readings: List<String>,
    val romaji: List<String>,
    val glosses: List<String>,
    val tags: List<String>,
    val jlpt: Int?,
    val common: Boolean,
    val translation: String,
    val notes: String,
)

data class SentenceSummary(val id: Long, val text: String, val translation: String) {
    val segments: List<FuriganaSegment> get() = parseFurigana(text)
    val plainText: String get() = formatFuriganaPlain(text)
}

data class NameSummary(val id: Long, val name: String, val reading: String, val type: String)

data class KanaSummary(
    val character: String, val reading: String, val example: String, val meaning: String,
    val translation: String, val origin: String, val katakana: Boolean, val archaic: Boolean,
    val diacritic: Boolean, val strokeCount: Int?, val strokePaths: List<String>,
)

data class RadicalSummary(
    val code: Int, val character: String, val meaning: String, val reading: String,
    val strokeCount: Int?, val sequence: Int?, val strokePaths: List<String>,
)

data class OutlierSummary(
    val id: Long, val name: String, val text: String, val keyConcept: Boolean, val ancientForm: String,
)

data class OutlierCharacterSummary(val id: Long, val name: String, val viewBox: String, val paths: List<String>)
