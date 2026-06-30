package com.boxmemo.app.update

/**
 * Pure semver comparison for the in-app updater — kept free of Android/network
 * types so the "is the GitHub release newer than what's installed?" decision is
 * JVM-testable. Tags like "v0.4.0" and plain "0.4.0" both parse; pre-release /
 * build suffixes ("-beta", "+meta") are ignored for the comparison.
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

    /**
     * True iff [latest] is a strictly newer version than [current]. Missing
     * trailing components are treated as 0 ("0.4" == "0.4.0"). Anything that
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
        return false
    }
}
