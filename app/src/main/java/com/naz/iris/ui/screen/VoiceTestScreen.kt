package com.naz.iris.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naz.iris.core.voice.IrisStt
import com.naz.iris.core.voice.IrisTts

private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun VoiceTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var hasMicPermission by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Hazır") }
    var isListening by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        status = if (granted) "Mikrofon izni verildi ✅" else "Mikrofon izni reddedildi."
    }

    // ✅ TTS önce: STT callback içinde kullanacağız
    val tts = remember {
        IrisTts(
            context = context,
            onReady = { status = "TTS hazır 🔊" },
            onError = { err -> status = "TTS Hatası: $err" }
        )
    }

    val stt = remember {
        IrisStt(
            context = context,
            languageTag = "tr-TR",
            onPartial = { partial -> transcript = partial },
            onFinal = { finalText ->
                transcript = finalText
                status = "Final sonuç geldi ✅"
                isListening = false

                // ✅ Sonra TTS ile konuş
                tts.speak("Duydum: $finalText")
            },
            onStatus = { s -> status = s },
            onError = { e ->
                status = "Hata: $e"
                isListening = false
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            stt.release()
            tts.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Voice Test (Stage 2)")
        Text("Durum: $status")
        Text("Transcript: ${if (transcript.isBlank()) "(henüz yok)" else transcript}")

        Button(onClick = {
            if (!hasMicPermission) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@Button
            }

            if (!stt.isAvailable()) {
                status = "Bu cihaz/emulator SpeechRecognizer desteklemiyor."
                return@Button
            }

            if (!isListening) {
                // ✅ yeni oturum: ekranı temizle
                transcript = ""
                status = "Dinliyorum…"

                isListening = true
                stt.startListening()
            } else {
                // ✅ kullanıcı durdurdu
                stt.stopListening()
                isListening = false
                status = "Durduruldu."
            }

        }) {
            Text(
                when {
                    !hasMicPermission -> "Mikrofon İzni Ver"
                    isListening -> "Durdur"
                    else -> "Konuş"
                }
            )
        }
    }
}
