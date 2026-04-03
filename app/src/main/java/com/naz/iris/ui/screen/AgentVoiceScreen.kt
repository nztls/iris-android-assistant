package com.naz.iris.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naz.iris.core.agent.IrisAgentOrchestrator
import com.naz.iris.core.net.NetworkStatus
import com.naz.iris.core.voice.IrisStt
import com.naz.iris.core.voice.IrisTts
import kotlinx.coroutines.launch

private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

private data class ChatMessageUi(
    val role: ChatRole,
    val text: String
)

private enum class ChatRole {
    USER,
    ASSISTANT
}

@Composable
fun AgentVoiceScreen(
    modifier: Modifier = Modifier,
    orchestrator: IrisAgentOrchestrator,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Hazır") }
    var lastFinal by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val conversation = remember { mutableStateListOf<ChatMessageUi>() }

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

    fun addUserMessage(text: String) {
        val clean = text.trim()
        if (clean.isNotBlank()) {
            conversation += ChatMessageUi(
                role = ChatRole.USER,
                text = clean
            )
        }
    }

    fun addAssistantMessage(text: String) {
        val clean = text.trim()
        if (clean.isNotBlank()) {
            conversation += ChatMessageUi(
                role = ChatRole.ASSISTANT,
                text = clean
            )
        }
    }

    fun offlineFallback(userText: String) {
        val fallback = "İnternet yok. Şimdilik sadece duyduğumu tekrar edebilirim: $userText"
        addAssistantMessage(fallback)
        speakFinal(fallback)
    }

    fun runAgent(userText: String) {
        Log.d("IRIS", "runAgent (Stage4 UI) INVOKED: $userText")

        scope.launch {
            isBusy = true
            status = "Düşünüyorum…"

            try {
                if (!NetworkStatus.isOnline(context)) {
                    status = "Offline mod"
                    offlineFallback(userText)
                    return@launch
                }

                val finalText = orchestrator.processUserInput(userText)

                status = "FINAL ✅"
                addAssistantMessage(finalText)
                speakFinal(finalText)

            } catch (e: Exception) {
                Log.e("IRIS", "Stage4 UI EXCEPTION: ${e.message}", e)
                status = "Hata: ${e.message}"

                val safeText = "Bir hata oluştu: ${e.message}"
                addAssistantMessage(safeText)
                speakFinal(safeText)
            } finally {
                isBusy = false
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
                    addUserMessage(finalText)
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
        Text(
            text = "Agent Voice (Stage 4)",
            style = MaterialTheme.typography.headlineSmall
        )

        StatusRow(
            isListening = isListening,
            isBusy = isBusy
        )

        Text("Durum: $status")
        Text("Transcript: ${transcript.ifBlank { "(henüz yok)" }}")
        Text("Son cevap: ${lastFinal.ifBlank { "(henüz yok)" }}")

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

            OutlinedButton(
                enabled = !isBusy && conversation.isNotEmpty(),
                onClick = {
                    conversation.clear()
                    transcript = ""
                    lastFinal = ""
                    status = "Konuşma temizlendi"
                    orchestrator.clearMemory()
                }
            ) {
                Text("Temizle")
            }
        }

        HorizontalDivider()

        Text(
            text = "Konuşma Geçmişi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (conversation.isEmpty()) {
            Text("Henüz konuşma yok.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversation) { item ->
                    MessageBubble(item)
                }

                if (isBusy) {
                    item {
                        MessageBubble(
                            ChatMessageUi(
                                role = ChatRole.ASSISTANT,
                                text = "Düşünüyorum…"
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    isListening: Boolean,
    isBusy: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusChip(
            label = if (isListening) "Listening" else "Idle"
        )
        StatusChip(
            label = if (isBusy) "Thinking" else "Ready"
        )
    }
}

@Composable
private fun StatusChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun MessageBubble(item: ChatMessageUi) {
    val isUser = item.role == ChatRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isUser) "Sen" else "Iris",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}