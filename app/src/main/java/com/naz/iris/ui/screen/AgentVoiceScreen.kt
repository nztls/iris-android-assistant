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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naz.iris.core.agent.IrisParsed
import com.naz.iris.core.agent.IrisPrompts
import com.naz.iris.core.agent.IrisResponseParser
import com.naz.iris.core.net.NetworkStatus
import com.naz.iris.core.tools.IrisToolDispatcher
import com.naz.iris.core.tools.ToolExecutionResult
import com.naz.iris.core.voice.IrisStt
import com.naz.iris.core.voice.IrisTts
import com.naz.iris.data.llm.GeminiClient
import com.naz.iris.data.llm.GeminiResult
import kotlinx.coroutines.launch

private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun AgentVoiceScreen(
    modifier: Modifier = Modifier,
    geminiClient: GeminiClient,
    toolDispatcher: IrisToolDispatcher,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Hazır") }
    var lastFinal by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        status = if (granted) "Mikrofon izni verildi ✅" else "Mikrofon izni reddedildi."
    }

    val tts = remember {
        IrisTts(
            context = context,
            onReady = { status = "TTS hazır 🔊" },
            onError = { err -> status = "TTS hatası: $err" }
        )
    }

    fun speakFinal(text: String) {
        lastFinal = text
        tts.speak(text)
    }

    fun offlineFallback(userText: String) {
        speakFinal("İnternet yok. Şimdilik sadece duyduğumu tekrar edebilirim: $userText")
    }

    fun runAgent(userText: String) {
        Log.d("IRIS", "runAgent INVOKED: $userText")

        scope.launch {
            Log.d("IRIS", "runAgent START")
            isBusy = true
            status = "LLM çağrısı başlıyor…"

            try {
                if (!NetworkStatus.isOnline(context)) {
                    status = "Offline mod"
                    offlineFallback(userText)
                    Log.d("IRIS", "OFFLINE fallback")
                    return@launch
                }

                // 1) İlk tur: sadece karar verici
                val r1 = geminiClient.generateRaw(
                    IrisPrompts.SYSTEM_PROMPT,
                    userText
                )

                val raw1 = when (r1) {
                    is GeminiResult.Success -> {
                        Log.d("IRIS", "LLM RAW1: ${r1.rawText}")
                        status = "LLM cevap geldi, parse ediyorum…"
                        r1.rawText
                    }

                    is GeminiResult.Error -> {
                        Log.e("IRIS", "LLM ERROR: ${r1.kind} ${r1.message}")
                        status = "LLM hata: ${r1.kind}"
                        speakFinal("LLM hata: ${r1.kind}. ${r1.message.take(120)}")
                        return@launch
                    }
                }

                val p1 = IrisResponseParser.parse(raw1)

                when (p1) {
                    is IrisParsed.Final -> {
                        status = "FINAL ✅"
                        speakFinal(p1.text)
                        Log.d("IRIS", "FINAL1: ${p1.text}")
                    }

                    is IrisParsed.Invalid -> {
                        status = "Format bozuk → fallback"
                        speakFinal(p1.safeText)
                        Log.w("IRIS", "INVALID1: ${p1.reason}")
                    }

                    is IrisParsed.CallTool -> {
                        status = "TOOL MODE: ${p1.name}"
                        Log.d("IRIS", "CALL_TOOL: ${p1.name} args=${p1.args}")

                        val toolResult = try {
                            // p1.args is JsonElement, dispatcher expects String
                            toolDispatcher.dispatch(p1.name, p1.args.toString())
                        } catch (e: Exception) {
                            Log.e("IRIS", "TOOL EXCEPTION: ${p1.name} ${e.message}", e)
                            status = "Tool hata: ${p1.name}"
                            speakFinal("Araç çalışırken bir hata oldu: ${e.message}")
                            return@launch
                        }

                        when (toolResult) {
                            is ToolExecutionResult.WithLocalFinal -> {
                                status = "LOCAL FINAL ✅"
                                Log.d(
                                    "IRIS",
                                    "LOCAL FINAL tool=${toolResult.toolName} raw=${toolResult.rawJson}"
                                )
                                speakFinal(toolResult.localFinalText)
                            }

                            is ToolExecutionResult.RequiresSecondTurn -> {
                                status = "2. tur LLM…"
                                Log.d(
                                    "IRIS",
                                    "SECOND TURN tool=${toolResult.toolName} raw=${toolResult.rawJson}"
                                )

                                val toolMsg =
                                    "<<TOOL_RESULT>> ${toolResult.rawJson} <<END_TOOL_RESULT>>"

                                val r2 = geminiClient.generateRaw(
                                    IrisPrompts.SYSTEM_PROMPT,
                                    toolMsg
                                )

                                val raw2 = when (r2) {
                                    is GeminiResult.Success -> {
                                        Log.d("IRIS", "LLM RAW2: ${r2.rawText}")
                                        r2.rawText
                                    }

                                    is GeminiResult.Error -> {
                                        Log.e(
                                            "IRIS",
                                            "LLM2 ERROR: ${r2.kind} ${r2.message}"
                                        )
                                        status = "LLM 2. tur hata: ${r2.kind}"
                                        speakFinal("Araç çalıştı ama son cevabı üretemedim.")
                                        return@launch
                                    }
                                }

                                val p2 = IrisResponseParser.parse(raw2)

                                val finalText = when (p2) {
                                    is IrisParsed.Final -> p2.text
                                    is IrisParsed.Invalid -> p2.safeText
                                    is IrisParsed.CallTool -> "Araç sonucu geldi ama son cevabı toparlayamadım."
                                }

                                status = "FINAL ✅"
                                speakFinal(finalText)
                                Log.d("IRIS", "FINAL2: $finalText")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IRIS", "runAgent EXCEPTION: ${e.message}", e)
                status = "Hata: ${e.message}"
                speakFinal("Bir hata oldu: ${e.message}")
            } finally {
                isBusy = false
                Log.d("IRIS", "runAgent END")
            }
        }
    }

    val stt = remember {
        IrisStt(
            context = context,
            languageTag = "tr-TR",
            onPartial = { partial ->
                transcript = partial
            },
            onFinal = { finalText ->
                transcript = finalText
                isListening = false

                Log.d("IRIS", "STT FINAL: $finalText")
                status = "STT final geldi ✅ LLM başlatıyorum…"

                if (!isBusy) {
                    runAgent(finalText)
                }
            },
            onStatus = { s ->
                status = s
            },
            onError = { e ->
                status = "STT Hata: $e"
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
        Text("Agent Voice (Stage 3)")
        Text("Durum: $status")
        Text("Transcript: ${transcript.ifBlank { "(henüz yok)" }}")
        Text("Son cevap: ${lastFinal.ifBlank { "(henüz yok)" }}")

        Button(
            enabled = !isBusy,
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
                    status = "Dinleme durduruldu."
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
