package com.kanjiforge.app.data.reference

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream

sealed interface CatalogBootstrapState {
    data class Copying(val progress: Float?) : CatalogBootstrapState
    data object Verifying : CatalogBootstrapState
    data class Ready(val catalog: ReferenceCatalog) : CatalogBootstrapState
    data class Failed(val message: String) : CatalogBootstrapState
}

interface CatalogAssetSource {
    fun openManifest(): InputStream
    fun openCompressedCatalog(fileName: String): InputStream
}

interface CatalogOpener {
    fun quickCheck(file: File): Boolean
    fun open(path: String): ReferenceCatalog
    fun verify(file: File): String? =
        if (quickCheck(file)) null else "The bundled catalog is incomplete. Please retry setup."
}

class AndroidCatalogAssetSource(context: Context) : CatalogAssetSource {
    private val assets = context.applicationContext.assets

    override fun openManifest(): InputStream = assets.open("reference/manifest.json")

    override fun openCompressedCatalog(fileName: String): InputStream = assets.open("reference/$fileName")
}

object AndroidCatalogOpener : CatalogOpener {
    private const val TAG = "CatalogBootstrap"

    override fun verify(file: File): String? = try {
        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, flags)
        db.use {
            val queries = listOf(
                "SELECT 1 FROM kanji LIMIT 1",
                "SELECT 1 FROM words LIMIT 1",
                "SELECT 1 FROM word_search_fts LIMIT 1",
            )
            val allReadable = queries.all { query ->
                it.rawQuery(query, null).use { cursor -> cursor.moveToFirst() }
            }
            if (allReadable) null else "The bundled catalog is incomplete. Please retry setup."
        }
    } catch (error: Exception) {
        runCatching { Log.e(TAG, "Catalog verification failed for ${file.absolutePath}", error) }
        "The bundled catalog could not be opened (${error.javaClass.simpleName}: ${error.message ?: "unknown error"}). Please retry setup."
    }

    override fun quickCheck(file: File): Boolean = verify(file) == null

    override fun open(path: String): ReferenceCatalog = SqliteReferenceCatalog.open(path)
}

class CatalogBootstrap(
    private val targetDir: File,
    private val assets: CatalogAssetSource,
    private val opener: CatalogOpener,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {

    @Serializable
    private data class ManifestRoot(
        val schemaVersion: Int = 1,
        val catalog: ManifestCatalog,
    )

    @Serializable
    private data class ManifestCatalog(
        val file: String,
        val sha256: String,
        val uncompressedSizeBytes: Long,
        val uncompressedSha256: String,
    )

    @Serializable
    private data class VerifiedCatalogStamp(
        val schemaVersion: Int,
        val uncompressedSizeBytes: Long,
        val uncompressedSha256: String,
        val lastModified: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<CatalogBootstrapState>(CatalogBootstrapState.Copying(0f))
    val state: StateFlow<CatalogBootstrapState> = _state.asStateFlow()

    private val lock = Any()
    private var installJob: Job? = null
    private var activeCatalog: ReferenceCatalog? = null

    fun start() {
        synchronized(lock) {
            if (installJob?.isActive == true || _state.value is CatalogBootstrapState.Ready) return
            launchInstallLocked(forceVerify = false)
        }
    }

    fun retry() {
        synchronized(lock) {
            installJob?.cancel()
            activeCatalog?.let { runCatching { it.close() } }
            activeCatalog = null
            File(targetDir, STAMP_FILE).delete()
            File(targetDir, TEMP_FILE).delete()
            _state.value = CatalogBootstrapState.Copying(0f)
            launchInstallLocked(forceVerify = true)
        }
    }

    override fun close() {
        synchronized(lock) {
            installJob?.cancel()
            installJob = null
            activeCatalog?.let { runCatching { it.close() } }
            activeCatalog = null
        }
    }

    private fun launchInstallLocked(forceVerify: Boolean) {
        installJob = scope.launch(ioDispatcher) {
            try {
                val manifest = assets.openManifest().bufferedReader(Charsets.UTF_8).use { reader ->
                    json.decodeFromString<ManifestRoot>(reader.readText())
                }
                val catalogMeta = manifest.catalog
                targetDir.mkdirs()
                val file = File(targetDir, CATALOG_FILE)
                val stampFile = File(targetDir, STAMP_FILE)

                if (!forceVerify &&
                    file.isFile &&
                    file.length() == catalogMeta.uncompressedSizeBytes &&
                    hasSQLiteHeader(file) &&
                    stampMatches(stampFile, file, manifest)
                ) {
                    val opened = opener.open(file.absolutePath)
                    publishReady(opened)
                    return@launch
                }

                if (!forceVerify &&
                    file.isFile &&
                    file.length() == catalogMeta.uncompressedSizeBytes &&
                    hasSQLiteHeader(file)
                ) {
                    _state.value = CatalogBootstrapState.Verifying
                    if (sha256(file) == catalogMeta.uncompressedSha256 && opener.verify(file) == null) {
                        writeStamp(stampFile, file, manifest)
                        val opened = opener.open(file.absolutePath)
                        publishReady(opened)
                        return@launch
                    }
                }

                val temporary = File(targetDir, TEMP_FILE)
                if (temporary.exists()) temporary.delete()
                val compressedDigest = MessageDigest.getInstance("SHA-256")
                val databaseDigest = MessageDigest.getInstance("SHA-256")
                val expectedSize = catalogMeta.uncompressedSizeBytes
                var totalBytes = 0L
                var lastReportedBytes = 0L

                _state.value = CatalogBootstrapState.Copying(if (expectedSize > 0L) 0f else null)
                assets.openCompressedCatalog(catalogMeta.file).use { asset ->
                    val compressed = DigestInputStream(BufferedInputStream(asset), compressedDigest)
                    GZIPInputStream(compressed).use { gzip ->
                        DigestInputStream(gzip, databaseDigest).use { database ->
                            BufferedOutputStream(temporary.outputStream()).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = database.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    totalBytes += count
                                    if (expectedSize > 0L && totalBytes - lastReportedBytes >= PROGRESS_STEP_BYTES) {
                                        lastReportedBytes = totalBytes
                                        _state.value = CatalogBootstrapState.Copying(
                                            (totalBytes.toFloat() / expectedSize).coerceIn(0f, 1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                _state.value = CatalogBootstrapState.Verifying
                val compressedHash = compressedDigest.digest().hex()
                val databaseHash = databaseDigest.digest().hex()
                if (compressedHash != catalogMeta.sha256 ||
                    databaseHash != catalogMeta.uncompressedSha256 ||
                    temporary.length() != expectedSize
                ) {
                    temporary.delete()
                    throw IllegalStateException("The bundled catalog did not match its checksum. Please retry setup.")
                }
                if (!hasSQLiteHeader(temporary)) {
                    temporary.delete()
                    throw IllegalStateException("The bundled catalog is incomplete. Please retry setup.")
                }
                val verificationError = opener.verify(temporary)
                if (verificationError != null) {
                    temporary.delete()
                    throw IllegalStateException(verificationError)
                }
                if (!replaceFile(temporary, file)) {
                    temporary.delete()
                    throw IllegalStateException("Could not install the local catalog. Please retry setup.")
                }
                writeStamp(stampFile, file, manifest)
                val opened = opener.open(file.absolutePath)
                publishReady(opened)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                File(targetDir, TEMP_FILE).delete()
                _state.value = CatalogBootstrapState.Failed(
                    error.message ?: "Catalog setup failed. Please retry.",
                )
            }
        }
    }

    private fun publishReady(catalog: ReferenceCatalog) {
        synchronized(lock) {
            activeCatalog?.let { previous ->
                if (previous !== catalog) runCatching { previous.close() }
            }
            activeCatalog = catalog
            _state.value = CatalogBootstrapState.Ready(catalog)
        }
    }

    private fun stampMatches(stampFile: File, catalogFile: File, manifest: ManifestRoot): Boolean {
        if (!stampFile.isFile) return false
        val stamp = runCatching {
            json.decodeFromString<VerifiedCatalogStamp>(stampFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: return false
        return stamp.schemaVersion == manifest.schemaVersion &&
            stamp.uncompressedSizeBytes == manifest.catalog.uncompressedSizeBytes &&
            stamp.uncompressedSha256 == manifest.catalog.uncompressedSha256 &&
            stamp.lastModified == catalogFile.lastModified()
    }

    private fun writeStamp(stampFile: File, catalogFile: File, manifest: ManifestRoot) {
        val stamp = VerifiedCatalogStamp(
            schemaVersion = manifest.schemaVersion,
            uncompressedSizeBytes = manifest.catalog.uncompressedSizeBytes,
            uncompressedSha256 = manifest.catalog.uncompressedSha256,
            lastModified = catalogFile.lastModified(),
        )
        stampFile.writeText(json.encodeToString(stamp), Charsets.UTF_8)
    }

    private fun replaceFile(source: File, target: File): Boolean {
        val osRenamed = runCatching { Os.rename(source.absolutePath, target.absolutePath) }.isSuccess
        if (osRenamed) return true
        if (target.exists() && !target.delete()) return false
        return source.renameTo(target)
    }

    private fun hasSQLiteHeader(file: File): Boolean = runCatching {
        DataInputStream(FileInputStream(file)).use { input ->
            val header = ByteArray(16)
            input.readFully(header)
            String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
        }
    }.getOrDefault(false)

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(Locale.ROOT, it) }

    companion object {
        private const val CATALOG_FILE = "catalog.sqlite"
        private const val TEMP_FILE = "catalog.sqlite.part"
        private const val STAMP_FILE = "catalog.verified.json"
        private const val PROGRESS_STEP_BYTES = 512 * 1024L
    }
}
