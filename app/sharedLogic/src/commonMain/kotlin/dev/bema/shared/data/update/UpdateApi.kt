package dev.bema.shared.data.update

import dev.bema.shared.data.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A published release this installed build cannot reach yet. */
data class AvailableUpdate(
    val version: String,
    val title: String?,
    val notes: String?,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseUrl: String?
)

@Serializable
private data class ApkCatalog(val apks: List<ApkEntry> = emptyList())

@Serializable
internal data class ApkEntry(
    val version: String = "",
    val arch: String = "",
    val size: Long = 0,
    val sha256: String = "",
    val url: String = "",
    val status: String = ""
)

@Serializable
private data class ReleaseFeed(val releases: List<ReleaseNote> = emptyList())

@Serializable
internal data class ReleaseNote(
    val version: String = "",
    val title: String? = null,
    val notes: String? = null,
    val releaseUrl: String? = null
)

/**
 * Reads the public APK catalog on the update host.
 *
 * Both endpoints are unauthenticated: `/api/apks` lists the per-ABI artifacts and
 * `/api/apk-releases` carries the release notes, joined on the version string.
 * Neither takes an `app_id`, deliberately — the deployed catalog defaults to the
 * `cohub-mobile` scope the release pipeline publishes into, and that default is
 * what keeps this readable without credentials. The publishing token in
 * `.github/workflows/release.yml` must never be shipped here: it can upload for
 * every application on the host.
 */
class UpdateApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    rawHttpClient: HttpClient = createPlatformHttpClient()
) {
    private val httpClient = rawHttpClient.config {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /** Null when no published APK is newer than [current] for [abi]. */
    suspend fun availableUpdate(
        current: AppVersion,
        abi: String,
        skipped: AppVersion? = null
    ): AvailableUpdate? {
        val catalog: ApkCatalog = httpClient.get("$baseUrl/api/apks").body()
        val feed: ReleaseFeed = httpClient.get("$baseUrl/api/apk-releases").body()
        return selectUpdate(current, abi, skipped, catalog.apks, feed.releases)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://mobile.talesofai.com"
    }
}

/**
 * Picks the newest entry in [apks] that is newer than [current] and built for
 * [abi], then attaches the matching [releases] entry as the notes.
 *
 * Split APKs carry no manifest ABI restriction, so a mismatched one installs and
 * then fails to load its native library — an ABI with no entry yields no update
 * rather than a fallback to another architecture.
 *
 * Skipping suppresses exactly the skipped version: the next release prompts again.
 */
internal fun selectUpdate(
    current: AppVersion,
    abi: String,
    skipped: AppVersion?,
    apks: List<ApkEntry>,
    releases: List<ReleaseNote>
): AvailableUpdate? {
    val (version, entry) = apks
        .asSequence()
        .filter { it.status == "Available" && it.arch == abi && it.url.isNotBlank() }
        .mapNotNull { candidate -> AppVersion.parse(candidate.version)?.let { it to candidate } }
        .filter { (version, _) -> version > current && version != skipped }
        .maxByOrNull { (version, _) -> version }
        ?: return null
    val note = releases.firstOrNull { it.version == entry.version }
    return AvailableUpdate(
        version = version.toString(),
        title = note?.title,
        notes = note?.notes,
        downloadUrl = entry.url,
        sha256 = entry.sha256,
        sizeBytes = entry.size,
        releaseUrl = note?.releaseUrl
    )
}
