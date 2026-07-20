package com.boxmemo.app.update

/**
 * Pure semver comparison for the in-app updater — kept free of Android/network
 * types so the "is the GitHub release newer than what's installed?" decision is
 * JVM-testable. Tags like "v0.4.0" and plain "0.4.0" both parse. Pre-release
 * suffixes follow the semver rule that "0.5.0-beta" < "0.5.0" (so a beta
 * install isn't stranded when the bare release ships); build metadata ("+meta")
 * is ignored entirely.
 */
object UpdateVersions {

    /** Parse "v1.2.3" / "1.2.3" → [1, 2, 3]; null if any numeric part is missing. */
    fun parse(raw: String): List<Int>? {
        val core = raw.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (core.isEmpty()) return null
        val parts = core.split('.')
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /** The pre-release identifier ("beta.2" in "1.0.0-beta.2+meta"), or null if bare. */
    fun preRelease(raw: String): String? =
        raw.trim().substringBefore('+').substringAfter('-', "").ifEmpty { null }

    /**
     * True iff [latest] is a strictly newer version than [current]. Missing
     * trailing components are treated as 0 ("0.4" == "0.4.0"). When the
     * numeric cores are equal, a version *with* a pre-release suffix is lower
     * than the bare version (semver: "0.5.0-beta" < "0.5.0"); two pre-releases
     * of the same core are never ordered against each other. Anything that
     * fails to parse returns false — an unreadable tag never offers an update.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest) ?: return false
        val c = parse(current) ?: return false
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        // Equal cores: the bare release is newer than any pre-release of it.
        return preRelease(latest) == null && preRelease(current) != null
    }
}
