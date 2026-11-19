package com.example.util

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Abstraction for playing lightweight runner audio alerts so ViewModels remain unit-testable.
 */
interface SoundNotifier {
    fun playAlertTone(isHit: Boolean)
    fun release()
}

/**
 * Thread-safe, leak-free audio alert player reusing a single [ToneGenerator] instance.
 */
class SoundAlertPlayer : SoundNotifier {

    private val lock = Any()
    private var toneGenerator: ToneGenerator? = null

    override fun playAlertTone(isHit: Boolean) {
        runCatching {
            synchronized(lock) {
                val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).also {
                    toneGenerator = it
                }
                val tone = if (isHit) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP2
                generator.startTone(tone, if (isHit) 140 else 220)
            }
        }
    }

    override fun release() {
        runCatching {
            synchronized(lock) {
                toneGenerator?.release()
                toneGenerator = null
            }
        }
    }
}
