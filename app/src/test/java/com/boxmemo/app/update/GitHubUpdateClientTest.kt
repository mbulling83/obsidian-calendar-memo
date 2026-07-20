package com.boxmemo.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateClientTest {

    @Test fun `accepts https GitHub release and CDN hosts`() {
        assertTrue(
            GitHubUpdateClient.isTrustedApkUrl(
                "https://github.com/mbulling83/obsidian-calendar-memo/releases/download/v0.5.0/app.apk",
            ),
        )
        assertTrue(GitHubUpdateClient.isTrustedApkUrl("https://objects.githubusercontent.com/some/path.apk"))
        assertTrue(GitHubUpdateClient.isTrustedApkUrl("https://release-assets.githubusercontent.com/some/path.apk"))
    }

    @Test fun `rejects plain http`() {
        assertFalse(GitHubUpdateClient.isTrustedApkUrl("http://github.com/owner/repo/releases/download/v1/app.apk"))
    }

    @Test fun `rejects non-GitHub hosts`() {
        assertFalse(GitHubUpdateClient.isTrustedApkUrl("https://evil.example.com/app.apk"))
        assertFalse(GitHubUpdateClient.isTrustedApkUrl("https://github.com.evil.example.com/app.apk"))
        assertFalse(GitHubUpdateClient.isTrustedApkUrl("https://notgithubusercontent.com/app.apk"))
    }

    @Test fun `rejects malformed urls`() {
        assertFalse(GitHubUpdateClient.isTrustedApkUrl("not a url"))
        assertFalse(GitHubUpdateClient.isTrustedApkUrl(""))
    }
}
