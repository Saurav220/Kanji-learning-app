package com.kanjiforge.app.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

interface SpeechSpeaker : AutoCloseable {
    val isReady: StateFlow<Boolean>
    fun speak(text: String): Boolean
}

object NoOpSpeechSpeaker : SpeechSpeaker {
    private val readyState = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = readyState.asStateFlow()
    override fun speak(text: String): Boolean = false
    override fun close() = Unit
}

val LocalSpeechSpeaker = staticCompositionLocalOf<SpeechSpeaker> { NoOpSpeechSpeaker }

class AndroidSpeechSpeaker(context: Context) : SpeechSpeaker {
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var engine: TextToSpeech? = null
    private var closed = false
    private var pendingInitStatus: Int? = null

    init {
        val created = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post { applyInitStatus(status) }
        }
        synchronized(lock) {
            if (closed) {
                runCatching {
                    created.stop()
                    created.shutdown()
                }
            } else {
                engine = created
                pendingInitStatus?.let(::applyInitStatus)
            }
        }
    }

    private fun applyInitStatus(status: Int) {
        synchronized(lock) {
            if (closed) return
            val current = engine
            if (current == null) {
                pendingInitStatus = status
                return
            }
            val language = runCatching { current.setLanguage(Locale.JAPAN) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            _isReady.value = status == TextToSpeech.SUCCESS &&
                language != TextToSpeech.LANG_MISSING_DATA &&
                language != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        synchronized(lock) {
            if (closed || !_isReady.value) return false
            val current = engine ?: return false
            return runCatching {
                current.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) == TextToSpeech.SUCCESS
            }.getOrDefault(false)
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            _isReady.value = false
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            engine = null
        }
    }
}

@Composable
fun JapaneseSpeechButton(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Play Japanese audio",
    speaker: SpeechSpeaker = LocalSpeechSpeaker.current,
) {
    val isReady by speaker.isReady.collectAsStateWithLifecycle()
    val haptics = rememberKanjiHaptics()
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = {
            if (isReady) {
                haptics(KanjiHaptic.TAP)
                speaker.speak(text)
            } else {
                haptics(KanjiHaptic.REJECT)
                scope.launch {
                    snackbarHostState?.showSnackbar("Japanese text-to-speech voice isn't installed on this device.")
                }
            }
        },
        enabled = text.isNotBlank(),
        modifier = modifier,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = contentDescription,
            tint = if (isReady) {
                LocalContentColor.current
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
        )
    }
}
