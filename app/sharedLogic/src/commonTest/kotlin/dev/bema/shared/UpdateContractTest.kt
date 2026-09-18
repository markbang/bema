package dev.bema.shared

import dev.bema.shared.data.update.ApkEntry
import dev.bema.shared.data.update.AppVersion
import dev.bema.shared.data.update.ReleaseNote
import dev.bema.shared.data.update.selectUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateContractTest {

    private fun apk(
        version: String,
        arch: String = "arm64-v8a",
        status: String = "Available",
        url: String = "https://mobile.talesofai.com/apk/cohub-v$version-android-$arch.apk"
    ) = ApkEntry(version = version, arch = arch, size = 1024, sha256 = "a".repeat(64), url = url, status = status)

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
    fun picksTheNewestEntryForThisAbiAndJoinsItsNotes() {
        val update = selectUpdate(
            current = AppVersion.parse("0.0.2")!!,
            abi = "arm64-v8a",
            skipped = null,
            apks = listOf(apk("0.0.3"), apk("0.0.4", arch = "x86_64")),
            releases = listOf(ReleaseNote(version = "0.0.3", title = "March", notes = "- fixed things", releaseUrl = "https://example.test/v0.0.3"))
        )

        assertEquals("0.0.3", update?.version)
        assertEquals("March", update?.title)
        assertEquals("- fixed things", update?.notes)
        assertEquals("https://example.test/v0.0.3", update?.releaseUrl)
        assertEquals("https://mobile.talesofai.com/apk/cohub-v0.0.3-android-arm64-v8a.apk", update?.downloadUrl)
    }

    @Test
    fun returnsNothingWhenNoEntryIsNewer() {
        assertNull(
            selectUpdate(
                current = AppVersion.parse("0.0.2")!!,
                abi = "arm64-v8a",
                skipped = null,
                apks = listOf(apk("0.0.1"), apk("0.0.2")),
                releases = emptyList()
            )
        )
    }

    @Test
    fun aMissingAbiYieldsNoUpdateRatherThanAnotherArchitecture() {
        assertNull(
            selectUpdate(
                current = AppVersion.parse("0.0.2")!!,
                abi = "arm64-v8a",
                skipped = null,
                apks = listOf(apk("0.0.3", arch = "x86_64")),
                releases = emptyList()
            )
        )
    }

    @Test
    fun skippingSuppressesOnlyTheSkippedVersion() {
        val apks = listOf(apk("0.0.3"), apk("0.0.4"))

        assertEquals(
            "0.0.4",
            selectUpdate(AppVersion.parse("0.0.2")!!, "arm64-v8a", AppVersion.parse("0.0.3"), apks, emptyList())?.version
        )
        assertNull(
            selectUpdate(AppVersion.parse("0.0.2")!!, "arm64-v8a", AppVersion.parse("0.0.4"), listOf(apk("0.0.4")), emptyList())
        )
    }

    @Test
    fun ignoresEntriesThatAreNotDownloadable() {
        assertNull(
            selectUpdate(
                current = AppVersion.parse("0.0.2")!!,
                abi = "arm64-v8a",
                skipped = null,
                apks = listOf(apk("0.0.3", status = "Pending"), apk("0.0.4", url = "")),
                releases = emptyList()
            )
        )
    }

    @Test
    fun anUnpublishedReleaseStillPromptsForItsApk() {
        val update = selectUpdate(
            current = AppVersion.parse("0.0.2")!!,
            abi = "arm64-v8a",
            skipped = null,
            apks = listOf(apk("0.0.3")),
            releases = emptyList()
        )

        assertEquals("0.0.3", update?.version)
        assertNull(update?.notes)
    }
}
