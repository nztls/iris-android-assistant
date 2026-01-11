package com.naz.iris.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class IrisStt(
    private val context: Context,
    private val languageTag: String = "tr-TR",
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var recognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        if (isListening) return

        if (!isAvailable()) {
            onError("Bu cihazda SpeechRecognizer kullanılamıyor.")
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        onStatus("Hazır: konuşabilirsin 🎤")
                    }

                    override fun onBeginningOfSpeech() {
                        onStatus("Dinliyorum…")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // İstersen buradan VU meter yaparız (şimdilik boş)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onStatus("Konuşma bitti, işliyorum…")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val msg = mapError(error)
                        onError(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val text = extractBestResult(results)
                        if (text.isBlank()) {
                            onError("Sonuç boş geldi. Tekrar dener misin?")
                        } else {
                            onFinal(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = extractBestResult(partialResults)
                        if (text.isNotBlank()) onPartial(text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Çok kısa konuşmalarda “erken kesme” olmasın diye ufak tolerans:
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 700L)
        }

        isListening = true
        onStatus("Başlatıldı…")
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        recognizer?.stopListening()
        onStatus("Durduruldu.")
    }

    fun cancel() {
        isListening = false
        recognizer?.cancel()
        onStatus("İptal edildi.")
    }

    fun release() {
        isListening = false
        recognizer?.destroy()
        recognizer = null
        onStatus("STT kapatıldı.")
    }

    private fun extractBestResult(bundle: Bundle?): String {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull().orEmpty()
    }

    private fun mapError(code: Int): String {
        return when (code) {
            SpeechRecognizer.ERROR_AUDIO ->
                "Ses kaydı hatası (ERROR_AUDIO). Mikrofon/Emulator ayarını kontrol et."
            SpeechRecognizer.ERROR_CLIENT ->
                "İstemci hatası (ERROR_CLIENT). Genelde hızlı start/stop veya lifecycle kaynaklı."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Mikrofon izni yok (ERROR_INSUFFICIENT_PERMISSIONS)."
            SpeechRecognizer.ERROR_NETWORK ->
                "Ağ hatası (ERROR_NETWORK). Bazı cihazlarda STT servisi ağa ihtiyaç duyabilir."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Ağ zaman aşımı (ERROR_NETWORK_TIMEOUT)."
            SpeechRecognizer.ERROR_NO_MATCH ->
                "Anlayamadım (ERROR_NO_MATCH). Daha net ve mikrofona yakın konuş."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Tanıyıcı meşgul (ERROR_RECOGNIZER_BUSY). Önce stop/cancel sonra tekrar dene."
            SpeechRecognizer.ERROR_SERVER ->
                "Sunucu hatası (ERROR_SERVER)."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Konuşma algılanmadı (ERROR_SPEECH_TIMEOUT)."
            else ->
                "Bilinmeyen hata: $code"
        }
    }
}
