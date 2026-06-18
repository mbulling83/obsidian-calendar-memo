package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class VaultSettingsTest {

    @Test
    fun `resolves expected absolute path for a configured vault root`() {
        val settings = VaultSettings(vaultRoot = "/vault")

        val resolved = settings.resolveDailyNotePath(LocalDate.of(2026, 6, 17))

        assertEquals(
            "/vault/Periodic Notes/Daily Notes/2026/06 - June/2026-06-17.md",
            resolved?.path,
        )
    }

    @Test
    fun `resolves into the correct year and month folder across a year boundary`() {
        val settings = VaultSettings(vaultRoot = "/vault")

        val resolved = settings.resolveDailyNotePath(LocalDate.of(2025, 12, 31))
        val nextDay = settings.resolveDailyNotePath(LocalDate.of(2026, 1, 1))

        assertEquals(
            "/vault/Periodic Notes/Daily Notes/2025/12 - December/2025-12-31.md",
            resolved?.path,
        )
        assertEquals(
            "/vault/Periodic Notes/Daily Notes/2026/01 - January/2026-01-01.md",
            nextDay?.path,
        )
    }

    @Test
    fun `returns null when vault root is not configured`() {
        val settings = VaultSettings(vaultRoot = null)

        val resolved = settings.resolveDailyNotePath(LocalDate.of(2026, 6, 17))

        assertNull(resolved)
    }

    @Test
    fun `returns null when vault root is blank`() {
        val settings = VaultSettings(vaultRoot = "   ")

        val resolved = settings.resolveDailyNotePath(LocalDate.of(2026, 6, 17))

        assertNull(resolved)
    }
}
