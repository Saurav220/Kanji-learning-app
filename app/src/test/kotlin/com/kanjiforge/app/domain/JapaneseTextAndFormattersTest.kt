package com.kanjiforge.app.domain

import com.kanjiforge.app.ui.components.StrokePlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class JapaneseTextAndFormattersTest {

    @Test
    fun parsesSingleAndMultiKanjiFuriganaCorrectly() {
        val single = parseFurigana("リンゴ　を　もう　{ひと}一つ　いかが　です　か。")
        assertEquals("リンゴをもう一ついかがですか。", formatFuriganaPlain("リンゴ　を　もう　{ひと}一つ　いかが　です　か。"))
        assertTrue(single.any { it.base == "一" && it.reading == "ひと" })
        assertTrue(single.any { it.base.startsWith("ついかが") && it.reading == null })

        val multi = parseFurigana("{2ことし}今年　は　{2うま}上手く　{2にほん}日本{じん}人")
        assertEquals("今年は上手く日本人", formatFuriganaPlain("{2ことし}今年　は　{2うま}上手く　{2にほん}日本{じん}人"))
        assertTrue(multi.contains(FuriganaSegment("今年", "ことし")))
        assertTrue(multi.contains(FuriganaSegment("上手", "うま")))
        assertTrue(multi.contains(FuriganaSegment("日本", "にほん")))
        assertTrue(multi.contains(FuriganaSegment("人", "じん")))
    }

    @Test
    fun formatsIntervalsDueStatusAndGradesHumanely() {
        assertEquals("10 min", formatInterval(10.0 / 1440.0))
        assertEquals("3 hr", formatInterval(3.0 / 24.0))
        assertEquals("1 day", formatInterval(1.0))
        assertEquals("7 days", formatInterval(7.0))
        assertEquals("1.5 mo", formatInterval(45.0))
        assertEquals("1 yr", formatInterval(365.0))

        assertEquals("Due now", formatDueStatus(1_000L, 2_000L, ZoneId.of("UTC")))
        assertEquals("Primary 1", gradeLabel(1))
        assertEquals("Secondary 2", gradeLabel(8))
        assertEquals("Advanced", gradeLabel(10))
    }

    @Test
    fun strokePlaybackStateTransitionsCleanly() {
        val initial = StrokePlaybackState(totalStrokes = 3)
        val playing = initial.togglePlay()
        assertTrue(playing.isPlaying)
        assertEquals(0, playing.visibleCount)

        val tick1 = playing.onTick()
        assertEquals(1, tick1.visibleCount)
        assertTrue(tick1.isPlaying)

        val tick3 = tick1.onTick().onTick()
        assertEquals(3, tick3.visibleCount)
        assertFalse(tick3.isPlaying)

        val stepped = initial.stepNext()
        assertFalse(stepped.showGuide)
        assertEquals(1, stepped.visibleCount)
    }
}
