package dev.bema.shared

import dev.bema.shared.data.update.AppVersion
import dev.bema.shared.data.update.CatalogApk
import dev.bema.shared.data.update.CatalogRelease
import dev.bema.shared.data.update.selectUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateContractTest {

    private val current = AppVersion.parse("0.0.2")!!

    private fun apk(
        arch: String = "arm64-v8a",
        status: String = "Available",
        downloadUrl: String = "https://mobile.talesofai.com/apk/memos/id-$arch.apk",
        sizeBytes: Long? = 1024
    ) = CatalogApk(
        arch = arch,
        sizeBytes = sizeBytes,
        sha256 = "a".repeat(64),
        downloadUrl = downloadUrl,
        status = status
    )

    private fun release(
        version: String,
        apks: List<CatalogApk>,
        title: String? = "Release $version",
        notes: String? = "Notes for $version",
        releaseUrl: String? = "https://github.com/markbang/bema/releases/tag/v$version"
    ) = CatalogRelease(version = version, title = title, notes = notes, releaseUrl = releaseUrl, apks = apks)

    @Test
    fun parsesTheVersionFormsThePipelineCanProduce() {
        assertEquals(AppVersion(0, 0, 2), AppVersion.parse("0.0.2"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3-rc.1"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse(" 1.2.3 "))
    }

    @Test
    fun rejectsValuesThatCarryNoComparableVersion() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("1.2.3.4"))
        assertNull(AppVersion.parse("x.y.z"))
    }

    @Test
    fun ordersByFieldRatherThanAsText() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.0")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
    }

    @Test
    fun picksTheNewestReleaseWithThisArchitectureAndCarriesItsNotes() {
        val update = selectUpdate(
            current = current,
            abi = "arm64-v8a",
            skipped = null,
            releases = listOf(
                release("0.0.4", listOf(apk("x86_64"))),
                release("0.0.3", listOf(apk("arm64-v8a"), apk("x86")))
            )
        )

        assertEquals("0.0.3", update?.version)
        assertEquals("Release 0.0.3", update?.title)
        assertEquals("Notes for 0.0.3", update?.notes)
        assertEquals("https://github.com/markbang/bema/releases/tag/v0.0.3", update?.releaseUrl)
        assertEquals("https://mobile.talesofai.com/apk/memos/id-arm64-v8a.apk", update?.downloadUrl)
        assertEquals(1024, update?.sizeBytes)
    }

    @Test
    fun returnsNothingWhenNoVersionIsNewer() {
        assertNull(
            selectUpdate(current, "arm64-v8a", null, listOf(release("0.0.1", listOf(apk())), release("0.0.2", listOf(apk()))))
        )
    }

    @Test
    fun aReleaseWithoutThisArchitectureIsSkippedNotSubstituted() {
        assertNull(selectUpdate(current, "arm64-v8a", null, listOf(release("0.0.3", listOf(apk("x86_64"))))))
    }

    @Test
    fun skippingSuppressesOnlyTheSkippedVersion() {
        val releases = listOf(release("0.0.4", listOf(apk())), release("0.0.3", listOf(apk())))

        assertEquals("0.0.4", selectUpdate(current, "arm64-v8a", AppVersion.parse("0.0.3"), releases)?.version)
        assertEquals("0.0.3", selectUpdate(current, "arm64-v8a", AppVersion.parse("0.0.4"), releases)?.version)
        assertNull(
            selectUpdate(current, "arm64-v8a", AppVersion.parse("0.0.4"), listOf(release("0.0.4", listOf(apk()))))
        )
    }

    @Test
    fun ignoresArtifactsThatAreNotDownloadable() {
        assertNull(
            selectUpdate(
                current,
                "arm64-v8a",
                null,
                listOf(release("0.0.3", listOf(apk(status = "Pending"), apk(downloadUrl = ""))))
            )
        )
    }

    @Test
    fun anUnknownSizeDoesNotHideTheUpdate() {
        val update = selectUpdate(current, "arm64-v8a", null, listOf(release("0.0.3", listOf(apk(sizeBytes = null)))))

        assertEquals("0.0.3", update?.version)
        assertEquals(0, update?.sizeBytes)
    }

    @Test
    fun aReleaseWithoutNotesStillPromptsForItsApk() {
        val update = selectUpdate(
            current,
            "arm64-v8a",
            null,
            listOf(release("0.0.3", listOf(apk()), title = null, notes = null, releaseUrl = null))
        )

        assertEquals("0.0.3", update?.version)
        assertNull(update?.notes)
        assertNull(update?.releaseUrl)
    }

    @Test
    fun aReleaseWithNoArtifactsIsIgnored() {
        assertNull(selectUpdate(current, "arm64-v8a", null, listOf(release("0.0.3", emptyList()))))
    }
}
