package dev.bema.shared.data.update

import dev.bema.shared.data.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
private data class ApkCatalog(val releases: List<CatalogRelease> = emptyList())

@Serializable
internal data class CatalogRelease(
    val version: String = "",
    val title: String? = null,
    val notes: String? = null,
    val releaseUrl: String? = null,
    val publishedAt: String? = null,
    val apks: List<CatalogApk> = emptyList()
)

@Serializable
internal data class CatalogApk(
    val arch: String = "",
    val sizeBytes: Long? = null,
    val sha256: String = "",
    val downloadUrl: String = "",
    val status: String = ""
)

/**
 * Reads the app-scoped APK catalog on the update host.
 *
 * One request returns every published release for [APP_ID], each with its notes
 * and its per-ABI artifacts. The catalog is public; the publishing token in
 * `.github/workflows/release.yml` must never be shipped here, because it can
 * upload for every application on the host.
 *
 * `app_id` is required by the host — it answers 400 without it.
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
        val catalog: ApkCatalog = httpClient.get("$baseUrl/api/apk/catalog") {
            parameter("app_id", APP_ID)
        }.body()
        return selectUpdate(current, abi, skipped, catalog.releases)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://mobile.talesofai.com"

        /** Must match the `app_id` the release workflow publishes under. */
        const val APP_ID = "memos"
    }
}

/**
 * Picks the newest release in [releases] that is newer than [current] and ships an
 * artifact for [abi], then carries its notes into the result.
 *
 * A version whose artifacts cover only other architectures is skipped rather than
 * offering a mismatched APK: a split APK carries no manifest ABI restriction, so a
 * foreign one installs and then fails to load its native library.
 *
 * Skipping suppresses exactly the skipped version: the next release prompts again.
 */
internal fun selectUpdate(
    current: AppVersion,
    abi: String,
    skipped: AppVersion?,
    releases: List<CatalogRelease>
): AvailableUpdate? {
    val (version, release, apk) = releases
        .mapNotNull { release -> AppVersion.parse(release.version)?.let { it to release } }
        .filter { (version, _) -> version > current && version != skipped }
        .sortedByDescending { (version, _) -> version }
        .firstNotNullOfOrNull { (version, release) ->
            release.apks
                .firstOrNull { it.status == "Available" && it.arch == abi && it.downloadUrl.isNotBlank() }
                ?.let { Triple(version, release, it) }
        }
        ?: return null

    return AvailableUpdate(
        version = version.toString(),
        title = release.title,
        notes = release.notes,
        downloadUrl = apk.downloadUrl,
        sha256 = apk.sha256,
        sizeBytes = apk.sizeBytes ?: 0,
        releaseUrl = release.releaseUrl
    )
}
