package com.kanjiforge.app

import android.content.Context
import com.kanjiforge.app.data.backup.BackupManager
import com.kanjiforge.app.data.reference.AndroidCatalogAssetSource
import com.kanjiforge.app.data.reference.AndroidCatalogOpener
import com.kanjiforge.app.data.reference.CatalogBootstrap
import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.user.AppDatabase
import com.kanjiforge.app.data.user.StudyRepository
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.domain.StudyStore
import com.kanjiforge.app.ui.components.AndroidSpeechSpeaker
import com.kanjiforge.app.ui.components.SpeechSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock

class AppContainer(appContext: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.systemDefaultZone()

    val catalogBootstrap: CatalogBootstrap = CatalogBootstrap(
        targetDir = File(appContext.filesDir, "reference"),
        assets = AndroidCatalogAssetSource(appContext),
        opener = AndroidCatalogOpener,
        scope = applicationScope,
    ).also { it.start() }

    val studyStore: StudyStore = StudyRepository(
        database = AppDatabase.get(appContext),
        context = appContext,
        clock = clock,
    )

    val backupManager: BackupManager = BackupManager(studyStore)

    val speechSpeaker: SpeechSpeaker = AndroidSpeechSpeaker(appContext)

    private var cachedPair: Pair<ReferenceCatalog, StudyCatalog>? = null

    @Synchronized
    fun studyCatalogFor(reference: ReferenceCatalog): StudyCatalog {
        val current = cachedPair
        if (current != null && current.first === reference) return current.second
        val created = StudyCatalog(reference = reference, store = studyStore, clock = clock)
        cachedPair = reference to created
        return created
    }
}
