package com.vynyl.record.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays the needle-drop clip (see [needleDropPcm]) on every stylus landing; mirrors web playNeedleDrop(). */
object NeedleDrop {
    private val pcm by lazy { needleDropPcm() }
    private var track: AudioTrack? = null

    fun play() {
        val t = track ?: AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            .also { it.write(pcm, 0, pcm.size); track = it }
        runCatching { t.stop(); t.reloadStaticData(); t.play() }
    }
}
