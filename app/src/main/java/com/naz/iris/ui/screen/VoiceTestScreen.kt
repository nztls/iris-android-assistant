package com.naz.iris.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var ttsReady by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        status = if (granted) {
            "Mikrofon izni verildi ✅"
        } else {
            "Mikrofon izni reddedildi."
        }
    }

    val tts = remember {
        IrisTts(
            context = context,
            onReady = {
                ttsReady = true
                status = "TTS hazır 🔊"
                Log.d("IRIS_STAGE2", "TTS READY")
            },
            onError = { err ->
                ttsReady = false
                status = "TTS hatası: $err"
                Log.e("IRIS_STAGE2", "TTS ERROR: $err")
            }
        )
    }

    val stt = remember {
        IrisStt(
            context = context,
            languageTag = "tr-TR",
            onPartial = { partial ->
                transcript = partial
                Log.d("IRIS_STAGE2", "PARTIAL: $partial")
            },
            onFinal = { finalText ->
                transcript = finalText
                isListening = false
                status = "Final sonuç geldi ✅"
                Log.d("IRIS_STAGE2", "FINAL: $finalText")

                if (ttsReady) {
                    tts.speak("Duydum: $finalText")
                } else {
                    status = "STT çalıştı ama TTS henüz hazır değil."
                    Log.w("IRIS_STAGE2", "TTS not ready when trying to speak")
                }
            },
            onStatus = { s ->
                status = s
                Log.d("IRIS_STAGE2", "STT STATUS: $s")
            },
            onError = { e ->
                status = "STT hata: $e"
                isListening = false
                Log.e("IRIS_STAGE2", "STT ERROR: $e")
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
        Text("TTS hazır mı: ${if (ttsReady) "Evet" else "Hayır"}")
        Text("Transcript: ${if (transcript.isBlank()) "(henüz yok)" else transcript}")

        Button(
            onClick = {
                if (!hasMicPermission) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@Button
                }

                if (!stt.isAvailable()) {
                    status = "Bu cihaz/emulator SpeechRecognizer desteklemiyor."
                    return@Button
                }

                if (!isListening) {
                    transcript = ""
                    status = "Dinliyorum…"
                    isListening = true
                    stt.startListening()
                } else {
                    stt.stopListening()
                    isListening = false
                    status = "Durduruldu."
                }
            }
        ) {
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