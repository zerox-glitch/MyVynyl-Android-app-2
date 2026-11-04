package com.vynyl.record

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Vynyl Record", appName)
  }

  @Test
  fun `free user can save up to two voices and pro can save unlimited`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.vynyl.record.data.RecordStore.init(context)

    // Clear any previous voices in test run
    val current = com.vynyl.record.data.RecordStore.vaultVoices.value
    current.forEach { com.vynyl.record.data.RecordStore.deleteVaultVoice(it.id) }

    val dummyAudio = FloatArray(44100) { 0.1f }

    // Voice 1 for free user
    val v1 = com.vynyl.record.data.RecordStore.saveVaultVoice("v1", "Voice 1", dummyAudio, 1.0f, isPro = false)
    org.junit.Assert.assertNotNull(v1)
    org.junit.Assert.assertEquals(1, com.vynyl.record.data.RecordStore.vaultVoices.value.size)

    // Voice 2 for free user
    val v2 = com.vynyl.record.data.RecordStore.saveVaultVoice("v2", "Voice 2", dummyAudio, 1.0f, isPro = false)
    org.junit.Assert.assertNotNull(v2)
    org.junit.Assert.assertEquals(2, com.vynyl.record.data.RecordStore.vaultVoices.value.size)

    // Voice 3 for free user should fail / return null (capped at Free.MAX_VOICES = 2)
    val v3Free = com.vynyl.record.data.RecordStore.saveVaultVoice("v3", "Voice 3", dummyAudio, 1.0f, isPro = false)
    org.junit.Assert.assertNull(v3Free)
    org.junit.Assert.assertEquals(2, com.vynyl.record.data.RecordStore.vaultVoices.value.size)

    // Voice 3 for pro user should succeed
    val v3Pro = com.vynyl.record.data.RecordStore.saveVaultVoice("v3", "Voice 3", dummyAudio, 1.0f, isPro = true)
    org.junit.Assert.assertNotNull(v3Pro)
    org.junit.Assert.assertEquals(3, com.vynyl.record.data.RecordStore.vaultVoices.value.size)

    // Loading voice 1 audio data
    val loaded = com.vynyl.record.data.RecordStore.loadVaultVoice("v1")
    org.junit.Assert.assertNotNull(loaded)
    org.junit.Assert.assertEquals(dummyAudio.size, loaded?.size)

    // Deleting voice 1
    com.vynyl.record.data.RecordStore.deleteVaultVoice("v1")
    org.junit.Assert.assertEquals(2, com.vynyl.record.data.RecordStore.vaultVoices.value.size)
    org.junit.Assert.assertNull(com.vynyl.record.data.RecordStore.loadVaultVoice("v1"))
  }
}
