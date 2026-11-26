package com.kanjiforge.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanjiforge.app.domain.StudyPreferences
import com.kanjiforge.app.ui.navigation.KanjiForgeApp
import com.kanjiforge.app.ui.theme.KanjiForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as KanjiForgeApplication).container
        setContent {
            val preferences by container.studyStore.preferences.collectAsStateWithLifecycle(
                initialValue = StudyPreferences(),
            )
            KanjiForgeTheme(amoledDark = preferences.amoledDark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KanjiForgeApp(container)
                }
            }
        }
    }
}
