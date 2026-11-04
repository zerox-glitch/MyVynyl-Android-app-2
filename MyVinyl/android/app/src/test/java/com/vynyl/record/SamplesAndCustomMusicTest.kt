package com.vynyl.record

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vynyl.record.audio.CUSTOM_MUSIC
import com.vynyl.record.audio.MOODS
import com.vynyl.record.audio.SAMPLES
import com.vynyl.record.audio.SR
import com.vynyl.record.audio.STYLES
import com.vynyl.record.audio.makeCustomBed
import com.vynyl.record.audio.musicName
import com.vynyl.record.pro.Gate
import com.vynyl.record.pro.isFree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SamplesAndCustomMusicTest {

  @Test
  fun `every mood has exactly one sample with a bundled voice`() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(MOODS.map { it.id }.toSet(), SAMPLES.map { it.moodId }.toSet())
    assertEquals(MOODS.size, SAMPLES.size)
    SAMPLES.forEach { s ->
      assertTrue("style for ${s.moodId}", STYLES.any { it.id == s.styleId })
      assertTrue("script for ${s.moodId}", s.script.isNotBlank())
      ctx.assets.open("samples/${s.moodId}.m4a").use { assertTrue("voice for ${s.moodId}", it.available() > 10_000) }
    }
  }

  @Test
  fun `custom bed is the chosen part, faded and levelled like the built-in beds`() {
    val src = FloatArray(SR * 20) { i -> 0.8f * sin(i * 0.05f) }
    val bed = makeCustomBed(src, 5f, 15f)
    assertEquals(SR * 10, bed.size)
    assertEquals(0f, bed.first(), 1e-6f)
    assertEquals(0.26f, bed.maxOf { abs(it) }, 0.01f)
  }

  @Test
  fun `uploaded music is free and named`() {
    assertTrue(isFree(Gate.Music, CUSTOM_MUSIC))
    assertEquals("Your music · Our song", musicName(CUSTOM_MUSIC, "Our song"))
    assertEquals("No background", musicName("none"))
  }
}
