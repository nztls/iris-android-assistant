package com.naz.iris.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class IrisTts(
    context: Context,
    private val onReady: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("tr", "TR"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    onError?.invoke("Türkçe TTS desteklenmiyor.")
                } else {
                    isReady = true
                    onReady?.invoke()
                }
            } else {
                onError?.invoke("TTS başlatılamadı.")
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) {
            onError?.invoke("TTS henüz hazır değil.")
            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "IRIS_TTS_UTTERANCE"
        )
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
