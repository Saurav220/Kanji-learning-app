package com.kanjiforge.app.data.reference

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.kanjiforge.app.data.reference.model.KanaSummary
import com.kanjiforge.app.data.reference.model.KanjiDetail
import com.kanjiforge.app.data.reference.model.KanjiFilter
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.NameSummary
import com.kanjiforge.app.data.reference.model.OutlierCharacterSummary
import com.kanjiforge.app.data.reference.model.OutlierSummary
import com.kanjiforge.app.data.reference.model.PagedResult
import com.kanjiforge.app.data.reference.model.PartOfSpeech
import com.kanjiforge.app.data.reference.model.QuizCandidateInfo
import com.kanjiforge.app.data.reference.model.RadicalLink
import com.kanjiforge.app.data.reference.model.RadicalSummary
import com.kanjiforge.app.data.reference.model.SentenceSummary
import com.kanjiforge.app.data.reference.model.SequenceKanjiRef
import com.kanjiforge.app.data.reference.model.StudyDeckLevel
import com.kanjiforge.app.data.reference.model.StudyRatingFilter
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.data.reference.model.WordFilter
import com.kanjiforge.app.data.reference.model.WordSummary
import com.kanjiforge.app.domain.Rating
import com.kanjiforge.app.domain.formatFuriganaPlain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

class SqliteReferenceCatalog private constructor(
    private val handle: SQLiteDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReferenceCatalog {

    override suspend fun listStudyDeckLevels(family: StudySequenceFamily): List<StudyDeckLevel> =
        withContext(ioDispatcher) {
            val levelColumn = family.levelColumn
            if (levelColumn == null) {
                return@withContext handle.rawQuery(
                    "SELECT COUNT(*) FROM kanji_sequence WHERE ${family.rankColumn}>0",
                    null,
                ).use { cursor ->
                    if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                        listOf(StudyDeckLevel(family, 0, cursor.getInt(0)))
                    } else {
                        emptyList()
                    }
                }
            }
            val order = if (family.levelsDescending) "DESC" else "ASC"
            handle.rawQuery(
                "SELECT $levelColumn,COUNT(*) FROM kanji_sequence " +
                    "WHERE $levelColumn>0 GROUP BY $levelColumn ORDER BY $levelColumn $order",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(StudyDeckLevel(family, cursor.getInt(0), cursor.getInt(1)))
                }
            }
        }

    override suspend fun listStudyDeckRefs(family: StudySequenceFamily): Map<Int, List<SequenceKanjiRef>> =
        withContext(ioDispatcher) {
            val levelColumn = family.levelColumn
            val levelExpr = levelColumn?.let { "s.$it" } ?: "0"
            val where = if (levelColumn == null) "s.${family.rankColumn}>0" else "s.$levelColumn>0"
            val order = if (family.levelsDescending) "DESC" else "ASC"
            val grouped = linkedMapOf<Int, MutableList<SequenceKanjiRef>>()
            handle.rawQuery(
                "SELECT $levelExpr,k.code,k.character FROM kanji k " +
                    "JOIN kanji_sequence s ON s.code=k.code WHERE $where " +
                    "ORDER BY $levelExpr $order,s.${family.rankColumn} IS NULL,s.${family.rankColumn},k.code",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val level = cursor.getInt(0)
                    val ref = SequenceKanjiRef(level, cursor.getInt(1), cursor.text(2))
                    grouped.getOrPut(level) { mutableListOf() }.add(ref)
                }
            }
            grouped
        }

    override suspend fun searchStudyDeck(
        family: StudySequenceFamily,
        level: Int,
        offset: Int,
        limit: Int,
    ): List<KanjiSummary> = withContext(ioDispatcher) {
        val levelColumn = family.levelColumn
        val where = if (levelColumn == null) "s.${family.rankColumn}>0" else "s.$levelColumn=?"
        val args = if (levelColumn == null) null else arrayOf(level.toString())
        handle.rawQuery(
            "SELECT k.code,k.character,COALESCE(NULLIF(k.meaning,''),k.translation,''),k.stroke_count,k.jlpt,k.grade,k.sequence " +
                "FROM kanji k JOIN kanji_sequence s ON s.code=k.code WHERE $where " +
                "ORDER BY s.${family.rankColumn} IS NULL,s.${family.rankColumn},k.code " +
                "LIMIT ${limit.coerceIn(1, 7071)} OFFSET ${offset.coerceAtLeast(0)}",
            args,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toKanjiSummary()) }
        }
    }

    override suspend fun searchKanji(
        query: String,
        filters: KanjiFilter,
        offset: Int,
        limit: Int,
        savedCodes: Set<Int>,
        dueCodes: Set<Int>,
        reviewCodesByRating: Map<Rating, Set<Int>>,
        reviewedCodes: Set<Int>,
    ): PagedResult<KanjiSummary> = withContext(ioDispatcher) {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        val needle = query.trim()
        if (needle.isNotEmpty()) {
            clauses += "(k.character LIKE ? OR k.meaning LIKE ? OR k.translation LIKE ? OR k.on_json LIKE ? OR k.kun_json LIKE ?)"
            repeat(5) { args += "%$needle%" }
        }
        filters.jlpt?.let { clauses += "k.jlpt=?"; args += it.toString() }
        filters.grade?.let { clauses += "k.grade=?"; args += it.toString() }
        filters.strokeCount?.let { clauses += "k.stroke_count=?"; args += it.toString() }
        filters.radical?.let { code ->
            clauses += """(k.classical_radical=? OR k.radicals_json LIKE ? OR k.radicals_json LIKE ?)"""
            args += code.toString()
            args += "%\"code\":$code,%"
            args += "%\"code\":$code}%"
        }
        filters.studyRating?.let { rating ->
            when (rating) {
                StudyRatingFilter.UNSTUDIED -> {
                    if (reviewedCodes.isNotEmpty()) {
                        clauses += "k.code NOT IN (${reviewedCodes.sorted().joinToString(",")})"
                    }
                }
                else -> {
                    val domainRating = Rating.valueOf(rating.name)
                    val matchingCodes = reviewCodesByRating[domainRating].orEmpty()
                    if (matchingCodes.isEmpty()) return@withContext PagedResult(emptyList(), false)
                    clauses += "k.code IN (${matchingCodes.sorted().joinToString(",")})"
                }
            }
        }
        val selected = when {
            filters.savedOnly && filters.dueOnly -> savedCodes intersect dueCodes
            filters.savedOnly -> savedCodes
            filters.dueOnly -> dueCodes
            else -> null
        }
        if (selected != null) {
            if (selected.isEmpty()) return@withContext PagedResult(emptyList(), false)
            clauses += "k.code IN (${selected.sorted().joinToString(",")})"
        }
        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val rows = handle.rawQuery(
            "SELECT k.code,k.character,COALESCE(NULLIF(k.meaning,''),k.translation,''),k.stroke_count,k.jlpt,k.grade,k.sequence " +
                "FROM kanji k $where ORDER BY k.sequence IS NULL,k.sequence,k.code LIMIT ${safeLimit + 1} OFFSET $safeOffset",
            args.toTypedArray(),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toKanjiSummary()) }
        }
        PagedResult(rows.take(safeLimit), rows.size > safeLimit)
    }

    override suspend fun getKanji(character: String): KanjiDetail? = withContext(ioDispatcher) {
        readKanjiDetail("character=?", character)
    }

    override suspend fun getKanjiByCode(code: Int): KanjiDetail? = withContext(ioDispatcher) {
        readKanjiDetail("code=?", code.toString())
    }

    private fun readKanjiDetail(whereClause: String, arg: String): KanjiDetail? {
        val main = handle.rawQuery(
            "SELECT code,character,COALESCE(NULLIF(meaning,''),translation,''),stroke_count,jlpt,grade,sequence," +
                "translation,on_json,kun_json,nanori_json,romanized_reading,stroke_paths_json,radicals_json," +
                "decomposition,classical_radical,notes FROM kanji WHERE $whereClause LIMIT 1",
            arrayOf(arg),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toKanjiDetailBase() else return null }
        val code = main.first.code
        val words = handle.rawQuery(
            "SELECT w.id,w.forms_json,w.readings_json,w.romaji_json,w.glosses_json,w.tags_json,w.jlpt,w.common,w.translation,w.notes " +
                "FROM kanji_word l JOIN words w ON w.id=l.word_id WHERE l.kanji_code=? " +
                "ORDER BY w.common DESC,w.jlpt IS NULL,w.jlpt,w.id LIMIT 24",
            arrayOf(code.toString()),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toWordSummary()) } }
        val linkedSentences = handle.rawQuery(
            "SELECT s.id,s.text,s.translation FROM kanji_sentence l JOIN sentences s ON s.id=l.sentence_id " +
                "WHERE l.kanji_code=? ORDER BY l.recommended DESC,s.id LIMIT 12",
            arrayOf(code.toString()),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(SentenceSummary(cursor.getLong(0), cursor.text(1), cursor.text(2))) } }
        val names = handle.rawQuery(
            "SELECT n.id,n.name,n.reading,n.type FROM kanji_name l JOIN names n ON n.id=l.name_id " +
                "WHERE l.kanji_code=? ORDER BY n.id LIMIT 20",
            arrayOf(code.toString()),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(NameSummary(cursor.getLong(0), cursor.text(1), cursor.text(2), cursor.text(3))) } }
        return main.second.copy(words = words, sentences = linkedSentences, names = names)
    }

    override suspend fun getKanjiSummaries(characters: Collection<String>): Map<String, KanjiSummary> =
        withContext(ioDispatcher) {
            val distinct = characters.filter(String::isNotBlank).distinct()
            if (distinct.isEmpty()) return@withContext emptyMap()
            val result = linkedMapOf<String, KanjiSummary>()
            distinct.chunked(500).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                handle.rawQuery(
                    "SELECT code,character,COALESCE(NULLIF(meaning,''),translation,''),stroke_count,jlpt,grade,sequence " +
                        "FROM kanji WHERE character IN ($placeholders)",
                    chunk.toTypedArray(),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val summary = cursor.toKanjiSummary()
                        result[summary.character] = summary
                    }
                }
            }
            result
        }

    override suspend fun getQuizCandidates(
        codes: Collection<Int>,
        includeWords: Boolean,
        includeSentences: Boolean,
    ): Map<Int, QuizCandidateInfo> = withContext(ioDispatcher) {
        val distinct = codes.distinct()
        if (distinct.isEmpty()) return@withContext emptyMap()

        val summaries = linkedMapOf<Int, KanjiSummary>()
        val readingsByCode = linkedMapOf<Int, List<String>>()
        val wordsByCode = linkedMapOf<Int, String>()
        val sentencesByCode = linkedMapOf<Int, String>()

        distinct.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val args = chunk.map(Int::toString).toTypedArray()

            handle.rawQuery(
                "SELECT code,character,COALESCE(NULLIF(meaning,''),translation,''),stroke_count,jlpt,grade,sequence,on_json,kun_json " +
                    "FROM kanji WHERE code IN ($placeholders)",
                args,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val summary = cursor.toKanjiSummary()
                    summaries[summary.code] = summary
                    readingsByCode[summary.code] = cursor.stringArray(7) + cursor.stringArray(8)
                }
            }

            if (includeWords) {
                handle.rawQuery(
                    "SELECT l.kanji_code,w.forms_json FROM kanji_word l JOIN words w ON w.id=l.word_id " +
                        "WHERE l.kanji_code IN ($placeholders) ORDER BY w.common DESC,w.jlpt IS NULL,w.jlpt,w.id",
                    args,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val kanjiCode = cursor.getInt(0)
                        if (kanjiCode in wordsByCode) continue
                        val targetChar = summaries[kanjiCode]?.character ?: continue
                        val matchingForm = cursor.stringArray(1).firstOrNull { it.contains(targetChar) }
                        if (!matchingForm.isNullOrBlank()) {
                            wordsByCode[kanjiCode] = matchingForm
                        }
                    }
                }
            }

            if (includeSentences) {
                handle.rawQuery(
                    "SELECT l.kanji_code,s.text FROM kanji_sentence l JOIN sentences s ON s.id=l.sentence_id " +
                        "WHERE l.kanji_code IN ($placeholders) ORDER BY l.recommended DESC,s.id",
                    args,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val kanjiCode = cursor.getInt(0)
                        if (kanjiCode in sentencesByCode) continue
                        val plain = formatFuriganaPlain(cursor.text(1)).trim()
                        if (plain.isNotBlank()) {
                            sentencesByCode[kanjiCode] = plain
                        }
                    }
                }
            }
        }

        summaries.mapValues { (code, summary) ->
            QuizCandidateInfo(
                summary = summary,
                readings = readingsByCode[code].orEmpty(),
                sampleWord = wordsByCode[code],
                sampleSentence = sentencesByCode[code],
            )
        }
    }

    override suspend fun searchWords(
        query: String,
        filters: WordFilter,
        offset: Int,
        limit: Int,
    ): PagedResult<WordSummary> = withContext(ioDispatcher) {
        val needle = normalize(query.trim())
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        val useFts = wordSearchUsesFts(needle)
        if (needle.isNotEmpty()) {
            if (useFts) {
                val queryGrams = grams(needle)
                if (queryGrams.isEmpty()) return@withContext PagedResult(emptyList(), false)
                // The default Android FTS4 parser supports implicit AND between terms.
                val match = queryGrams.joinToString(" ") { "\"$it\"" }
                clauses += "word_search_fts MATCH ?"
                args += match
            }
            clauses += "instr(w.search_text,?)>0"
            args += needle
        }
        filters.jlpt?.let { clauses += "w.jlpt=?"; args += it.toString() }
        if (filters.commonOnly) clauses += "w.common=1"
        filters.partOfSpeech?.let { clauses += posSqlClause(it) }
        val from = if (!useFts) "words w" else "word_search_fts JOIN words w ON w.id=word_search_fts.word_id"
        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val rows = handle.rawQuery(
            "SELECT w.id,w.forms_json,w.readings_json,w.romaji_json,w.glosses_json,w.tags_json,w.jlpt,w.common,w.translation,w.notes " +
                "FROM $from $where ORDER BY w.common DESC,w.jlpt IS NULL,w.jlpt,w.id LIMIT ${safeLimit + 1} OFFSET $safeOffset",
            args.toTypedArray(),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toWordSummary()) }
        }
        PagedResult(rows.take(safeLimit), rows.size > safeLimit)
    }

    override suspend fun getWord(id: Long): WordSummary? = withContext(ioDispatcher) {
        handle.rawQuery(
            "SELECT id,forms_json,readings_json,romaji_json,glosses_json,tags_json,jlpt,common,translation,notes FROM words WHERE id=?",
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toWordSummary() else null }
    }

    override suspend fun getWords(ids: Collection<Long>): Map<Long, WordSummary> =
        withContext(ioDispatcher) {
            val distinct = ids.distinct()
            if (distinct.isEmpty()) return@withContext emptyMap()
            val result = linkedMapOf<Long, WordSummary>()
            distinct.chunked(500).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                handle.rawQuery(
                    "SELECT id,forms_json,readings_json,romaji_json,glosses_json,tags_json,jlpt,common,translation,notes " +
                        "FROM words WHERE id IN ($placeholders)",
                    chunk.map(Long::toString).toTypedArray(),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val word = cursor.toWordSummary()
                        result[word.id] = word
                    }
                }
            }
            result
        }

    override suspend fun listKana(): List<KanaSummary> = withContext(ioDispatcher) {
        handle.rawQuery(
            "SELECT character,reading,example,meaning,translation,origin,katakana,archaic,diacritic,stroke_count,stroke_paths_json " +
                "FROM kana ORDER BY sequence,character",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        KanaSummary(
                            cursor.text(0), cursor.text(1), cursor.text(2), cursor.text(3), cursor.text(4), cursor.text(5),
                            cursor.getInt(6) != 0, cursor.getInt(7) != 0, cursor.getInt(8) != 0, cursor.nullableInt(9), cursor.stringArray(10),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun listAllRadicals(): List<RadicalSummary> = withContext(ioDispatcher) {
        handle.rawQuery(
            "SELECT code,character,meaning,reading,stroke_count,sequence,stroke_paths_json FROM radicals ORDER BY sequence,code",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        RadicalSummary(
                            cursor.getInt(0), cursor.text(1), cursor.text(2), cursor.text(3),
                            cursor.nullableInt(4), cursor.nullableInt(5), cursor.stringArray(6),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun searchOutlier(query: String, offset: Int, limit: Int): PagedResult<OutlierSummary> =
        withContext(ioDispatcher) {
            val needle = query.trim()
            val where = if (needle.isBlank()) "" else "WHERE name LIKE ? OR text LIKE ? OR ancient_form LIKE ?"
            val args = if (needle.isBlank()) null else arrayOf("%$needle%", "%$needle%", "%$needle%")
            val safeLimit = limit.coerceIn(1, 200)
            val safeOffset = offset.coerceAtLeast(0)
            val rows = handle.rawQuery(
                "SELECT id,name,text,key_concept,ancient_form FROM outlier_references $where ORDER BY key_concept DESC,id " +
                    "LIMIT ${safeLimit + 1} OFFSET $safeOffset",
                args,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(OutlierSummary(cursor.getLong(0), cursor.text(1), cursor.text(2), cursor.getInt(3) != 0, cursor.text(4)))
                    }
                }
            }
            PagedResult(rows.take(safeLimit), rows.size > safeLimit)
        }

    override suspend fun listAllOutlierCharacters(): List<OutlierCharacterSummary> = withContext(ioDispatcher) {
        handle.rawQuery(
            "SELECT id,name,view_box,paths_json FROM outlier_characters ORDER BY id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(OutlierCharacterSummary(cursor.getLong(0), cursor.text(1), cursor.text(2), cursor.stringArray(3)))
                }
            }
        }
    }

    override fun close() = handle.close()

    private fun Cursor.toKanjiSummary() = KanjiSummary(
        getInt(0), text(1), text(2), nullableInt(3), nullableInt(4), nullableInt(5), nullableInt(6),
    )

    private fun Cursor.toKanjiDetailBase(): Pair<KanjiSummary, KanjiDetail> {
        val summary = toKanjiSummary()
        val radicals = JSONArray(text(13)).let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        RadicalLink(
                            item.optInt("code"),
                            item.optString("character"),
                            item.optString("meaning"),
                            item.optInt("occurrences").takeIf { item.has("occurrences") },
                        ),
                    )
                }
            }
        }
        return summary to KanjiDetail(
            summary = summary, translation = text(7), on = stringArray(8), kun = stringArray(9), nanori = stringArray(10),
            romanizedReading = text(11), strokePaths = stringArray(12), radicals = radicals,
            decomposition = text(14), classicalRadical = nullableInt(15), notes = text(16),
            words = emptyList(), sentences = emptyList(), names = emptyList(),
        )
    }

    private fun Cursor.toWordSummary() = WordSummary(
        getLong(0), stringArray(1), stringArray(2), stringArray(3), stringArray(4), stringArray(5),
        nullableInt(6), getInt(7) != 0, text(8), text(9),
    )

    private fun Cursor.text(index: Int) = if (isNull(index)) "" else getString(index)
    private fun Cursor.nullableInt(index: Int) = if (isNull(index)) null else getInt(index)
    private fun Cursor.stringArray(index: Int): List<String> = runCatching {
        JSONArray(text(index)).let { array -> buildList { for (i in 0 until array.length()) add(array.optString(i)) } }
    }.getOrDefault(emptyList())

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    private fun grams(value: String): List<String> {
        val points = value.codePoints().toArray()
        val width = if (points.size == 1) 1 else 2
        return (0..points.size - width).map { start ->
            "g" + (start until start + width).joinToString("x") { points[it].toString(16).uppercase(Locale.ROOT).padStart(6, '0') }
        }.distinct().sorted()
    }

    companion object {
        fun open(path: String, ioDispatcher: CoroutineDispatcher = Dispatchers.IO): ReferenceCatalog {
            val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            return SqliteReferenceCatalog(db, ioDispatcher)
        }

        fun wordSearchUsesFts(normalizedNeedle: String): Boolean =
            normalizedNeedle.isNotEmpty() &&
                normalizedNeedle.codePointCount(0, normalizedNeedle.length) >= 2

        fun posSqlClause(pos: PartOfSpeech): String = when (pos) {
            PartOfSpeech.NOUN -> """w.tags_json LIKE '%"n"%'"""
            PartOfSpeech.VERB -> """(w.tags_json LIKE '%"v1"%' OR w.tags_json LIKE '%"v1s"%' OR w.tags_json LIKE '%"vs%' OR w.tags_json LIKE '%"vt"%' OR w.tags_json LIKE '%"vi"%' OR w.tags_json LIKE '%"vk"%' OR w.tags_json LIKE '%"vz"%' OR w.tags_json LIKE '%"vn"%' OR w.tags_json LIKE '%"vr"%' OR w.tags_json LIKE '%"vidg"%' OR w.tags_json LIKE '%"vunspec"%' OR w.tags_json LIKE '%"v5%' OR w.tags_json LIKE '%"v4%' OR w.tags_json LIKE '%"v2%')"""
            PartOfSpeech.ADJ_I -> """(w.tags_json LIKE '%"adji"%' OR w.tags_json LIKE '%"adjix"%')"""
            PartOfSpeech.ADJ_NA -> """w.tags_json LIKE '%"adjna"%'"""
            PartOfSpeech.ADVERB -> """(w.tags_json LIKE '%"adv"%' OR w.tags_json LIKE '%"advto"%')"""
        }
    }
}
