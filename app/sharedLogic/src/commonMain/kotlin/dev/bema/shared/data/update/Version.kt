package dev.bema.shared.data.update

/**
 * The `major.minor.patch` triple the release pipeline derives from `version.txt`.
 *
 * Compared field by field, not as text: a string comparison puts "0.10.0" before
 * "0.9.0", which would hide every update once the minor version reaches double
 * digits.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Parses the version forms this pipeline can produce: `0.0.2`, and
         * defensively a leading `v` or a `-rc.1` style suffix, since the update
         * host stores whatever the publisher sent. Returns null when the value
         * carries no comparable version, which callers treat as "no update".
         */
        fun parse(raw: String): AppVersion? {
            val core = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            val parts = core.split('.')
            if (parts.size != 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            if (numbers.any { it < 0 }) return null
            return AppVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
