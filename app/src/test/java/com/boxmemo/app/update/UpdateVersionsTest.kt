package com.boxmemo.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionsTest {

    @Test fun `parses plain and v-prefixed versions`() {
        assertEquals(listOf(0, 4, 0), UpdateVersions.parse("0.4.0"))
        assertEquals(listOf(0, 4, 0), UpdateVersions.parse("v0.4.0"))
        assertEquals(listOf(1, 2, 3), UpdateVersions.parse(" V1.2.3 "))
    }

    @Test fun `ignores pre-release and build suffixes`() {
        assertEquals(listOf(1, 0, 0), UpdateVersions.parse("1.0.0-beta.2"))
        assertEquals(listOf(1, 0, 0), UpdateVersions.parse("1.0.0+build7"))
    }

    @Test fun `returns null for unparseable tags`() {
        assertNull(UpdateVersions.parse("nightly"))
        assertNull(UpdateVersions.parse("1.x.0"))
        assertNull(UpdateVersions.parse("v"))
    }

    @Test fun `newer release is detected`() {
        assertTrue(UpdateVersions.isNewer("0.4.0", "0.3.0"))
        assertTrue(UpdateVersions.isNewer("v0.3.1", "0.3.0"))
        assertTrue(UpdateVersions.isNewer("1.0.0", "0.9.9"))
        assertTrue(UpdateVersions.isNewer("0.10.0", "0.9.0")) // not string-compared
    }

    @Test fun `same or older release is not an update`() {
        assertFalse(UpdateVersions.isNewer("0.3.0", "0.3.0"))
        assertFalse(UpdateVersions.isNewer("0.2.9", "0.3.0"))
        assertFalse(UpdateVersions.isNewer("v0.3.0", "0.3.0"))
    }

    @Test fun `missing trailing component treated as zero`() {
        assertFalse(UpdateVersions.isNewer("0.4", "0.4.0"))
        assertTrue(UpdateVersions.isNewer("0.4.1", "0.4"))
    }

    @Test fun `bare release is newer than its own pre-release`() {
        // Semver: 0.5.0-beta < 0.5.0 — a beta install must see the bare release.
        assertTrue(UpdateVersions.isNewer("0.5.0", "0.5.0-beta"))
        assertTrue(UpdateVersions.isNewer("v0.5.0", "0.5.0-rc.1"))
        assertFalse(UpdateVersions.isNewer("0.5.0-beta", "0.5.0"))
    }

    @Test fun `pre-releases of the same core are not ordered`() {
        assertFalse(UpdateVersions.isNewer("0.5.0-beta", "0.5.0-alpha"))
        assertFalse(UpdateVersions.isNewer("0.5.0-alpha", "0.5.0-beta"))
    }

    @Test fun `numeric core still wins over pre-release status`() {
        assertTrue(UpdateVersions.isNewer("0.5.1-beta", "0.5.0"))
        assertFalse(UpdateVersions.isNewer("0.5.0", "0.5.1-beta"))
    }

    @Test fun `build metadata does not make a version a pre-release`() {
        assertFalse(UpdateVersions.isNewer("0.5.0+build7", "0.5.0"))
        assertTrue(UpdateVersions.isNewer("0.5.0+build7", "0.5.0-beta"))
    }

    @Test fun `extracts the pre-release identifier`() {
        assertEquals("beta.2", UpdateVersions.preRelease("1.0.0-beta.2+meta"))
        assertNull(UpdateVersions.preRelease("1.0.0"))
        assertNull(UpdateVersions.preRelease("1.0.0+meta"))
    }

    @Test fun `unparseable inputs never offer an update`() {
        assertFalse(UpdateVersions.isNewer("garbage", "0.3.0"))
        assertFalse(UpdateVersions.isNewer("0.4.0", "garbage"))
    }
}
