package com.an0obIs.pref

import com.an0obIs.pref.model.AppSettings
import com.an0obIs.pref.model.PrefStorage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class AppSettingsTest {

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-settings-test").toFile())
    }

    @Test
    fun poolLimitIsClampedToTenThroughHundred() {
        assertEquals(40, AppSettings().limit) // default untouched
        assertEquals(10, AppSettings.clampLimit(3))
        assertEquals(10, AppSettings.clampLimit(4))
        assertEquals(40, AppSettings.clampLimit(40))
        assertEquals(100, AppSettings.clampLimit(250))
        val s = AppSettings()
        s.limit = 3
        assertEquals(10, AppSettings().limit)
        s.limit = 500
        assertEquals(100, AppSettings().limit)
        s.limit = 40
        assertEquals(40, AppSettings().limit)
    }
}
