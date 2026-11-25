package com.example.durtlesreborn.ui

import com.example.durtlesreborn.update.AvailableUpdate
import com.example.durtlesreborn.update.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDialogTest {
    @Test
    fun releaseNotesLoseMarkdownAndTheInstallSection() {
        val notes = "Bug-fix update.\r\n\r\n## Fixes\r\n\r\n- **Reader:** Back works.\r\n- Uses `TextFieldValue`.\r\n\r\n\r\n## Install\r\n\r\nDownload the APK.\r\n"
        assertEquals("Bug-fix update.\n\nFixes\n\n• Reader: Back works.\n• Uses TextFieldValue.", plainReleaseNotes(notes))
    }

    @Test
    fun emptyNotesStayEmpty() {
        assertEquals("", plainReleaseNotes(""))
    }

    @Test
    fun statusLines() {
        val update = AvailableUpdate("1.0.2", 3, "", 0)
        assertEquals("Installed version 1.0.1. Up to date (latest release 1.0.1).", updateStatusLine(UpdateStatus.UpToDate("1.0.1"), "1.0.1"))
        assertEquals("Version 1.0.2 is available. You have 1.0.1.", updateStatusLine(UpdateStatus.Available(update), "1.0.1"))
    }
}
