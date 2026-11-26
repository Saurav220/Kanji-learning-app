package com.kanjiforge.app.data.reference

import com.kanjiforge.app.FakeReferenceCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogBootstrapTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun firstInstallExtractsVerifiesAndReusesStampWithoutRehashing() = runTest {
        val rawDb = ("SQLite format 3\u0000" + "payload-data-block-".repeat(200)).toByteArray(Charsets.US_ASCII)
        val gzBytes = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write(rawDb) }
            baos.toByteArray()
        }
        val manifestJson = """
            {
              "schemaVersion": 2,
              "catalog": {
                "file": "catalog.sqlite.gzbin",
                "sha256": "${sha256(gzBytes)}",
                "uncompressedSizeBytes": ${rawDb.size},
                "uncompressedSha256": "${sha256(rawDb)}"
              }
            }
        """.trimIndent()

        var compressedOpens = 0
        var quickChecks = 0
        val assets = object : CatalogAssetSource {
            override fun openManifest(): InputStream = ByteArrayInputStream(manifestJson.toByteArray(Charsets.UTF_8))
            override fun openCompressedCatalog(fileName: String): InputStream {
                compressedOpens += 1
                return ByteArrayInputStream(gzBytes)
            }
        }
        val opener = object : CatalogOpener {
            override fun quickCheck(file: File): Boolean {
                quickChecks += 1
                return true
            }
            override fun open(path: String): ReferenceCatalog = FakeReferenceCatalog()
        }

        val targetDir = tempFolder.newFolder("reference")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = CatalogBootstrap(targetDir, assets, opener, this, dispatcher)
        first.start()
        advanceUntilIdle()

        assertTrue("Expected Ready, got ${first.state.value}", first.state.value is CatalogBootstrapState.Ready)
        assertEquals(1, compressedOpens)
        assertEquals(1, quickChecks)
        assertTrue(File(targetDir, "catalog.sqlite").isFile)
        assertTrue(File(targetDir, "catalog.verified.json").isFile)
        first.close()

        val second = CatalogBootstrap(targetDir, assets, opener, this, dispatcher)
        second.start()
        advanceUntilIdle()

        assertTrue("Expected Ready, got ${second.state.value}", second.state.value is CatalogBootstrapState.Ready)
        assertEquals("Fast path should not re-open compressed asset", 1, compressedOpens)
        assertEquals("Fast path should not re-run PRAGMA quick_check", 1, quickChecks)
        second.close()
    }

    @Test
    fun corruptChecksumFailsCleansTemporaryAndRecoversOnRetry() = runTest {
        val rawDb = "SQLite format 3\u0000valid-sqlite-bytes".toByteArray(Charsets.US_ASCII)
        val validGz = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write(rawDb) }
            baos.toByteArray()
        }
        val corruptGz = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write("corrupt-bytes".toByteArray()) }
            baos.toByteArray()
        }
        val manifestJson = """
            {
              "schemaVersion": 2,
              "catalog": {
                "file": "catalog.sqlite.gzbin",
                "sha256": "${sha256(validGz)}",
                "uncompressedSizeBytes": ${rawDb.size},
                "uncompressedSha256": "${sha256(rawDb)}"
              }
            }
        """.trimIndent()

        var serveCorrupt = true
        val assets = object : CatalogAssetSource {
            override fun openManifest(): InputStream = ByteArrayInputStream(manifestJson.toByteArray(Charsets.UTF_8))
            override fun openCompressedCatalog(fileName: String): InputStream =
                ByteArrayInputStream(if (serveCorrupt) corruptGz else validGz)
        }
        val opener = object : CatalogOpener {
            override fun quickCheck(file: File): Boolean = true
            override fun open(path: String): ReferenceCatalog = FakeReferenceCatalog()
        }

        val targetDir = tempFolder.newFolder("reference-retry")
        val bootstrap = CatalogBootstrap(targetDir, assets, opener, this, StandardTestDispatcher(testScheduler))
        bootstrap.start()
        advanceUntilIdle()

        assertTrue("Expected Failed, got ${bootstrap.state.value}", bootstrap.state.value is CatalogBootstrapState.Failed)
        assertFalse(File(targetDir, "catalog.sqlite.part").exists())

        serveCorrupt = false
        bootstrap.retry()
        advanceUntilIdle()

        assertTrue("Expected Ready after retry, got ${bootstrap.state.value}", bootstrap.state.value is CatalogBootstrapState.Ready)
        bootstrap.close()
    }

    @Test
    fun quickCheckFailureCleansTemporarySurfacesErrorAndRecoversOnRetry() = runTest {
        val rawDb = "SQLite format 3\u0000valid-sqlite-bytes".toByteArray(Charsets.US_ASCII)
        val validGz = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write(rawDb) }
            baos.toByteArray()
        }
        val manifestJson = """
            {
              "schemaVersion": 2,
              "catalog": {
                "file": "catalog.sqlite.gzbin",
                "sha256": "${sha256(validGz)}",
                "uncompressedSizeBytes": ${rawDb.size},
                "uncompressedSha256": "${sha256(rawDb)}"
              }
            }
        """.trimIndent()

        var allowVerify = false
        val assets = object : CatalogAssetSource {
            override fun openManifest(): InputStream = ByteArrayInputStream(manifestJson.toByteArray(Charsets.UTF_8))
            override fun openCompressedCatalog(fileName: String): InputStream = ByteArrayInputStream(validGz)
        }
        val opener = object : CatalogOpener {
            override fun quickCheck(file: File): Boolean = allowVerify
            override fun open(path: String): ReferenceCatalog = FakeReferenceCatalog()
        }

        val targetDir = tempFolder.newFolder("reference-quickcheck-retry")
        val bootstrap = CatalogBootstrap(targetDir, assets, opener, this, StandardTestDispatcher(testScheduler))
        bootstrap.start()
        advanceUntilIdle()

        val failedState = bootstrap.state.value as? CatalogBootstrapState.Failed
        assertTrue("Expected Failed, got ${bootstrap.state.value}", failedState != null)
        assertFalse(File(targetDir, "catalog.sqlite.part").exists())

        allowVerify = true
        bootstrap.retry()
        advanceUntilIdle()

        assertTrue("Expected Ready after retry, got ${bootstrap.state.value}", bootstrap.state.value is CatalogBootstrapState.Ready)
        assertTrue(File(targetDir, "catalog.sqlite").isFile)
        assertTrue(File(targetDir, "catalog.verified.json").isFile)
        bootstrap.close()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
}
