package com.naz.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naz.iris.core.tools.IrisToolDispatcher
import com.naz.iris.core.tools.IrisToolDispatcherImpl
import com.naz.iris.data.llm.GeminiClient
import com.naz.iris.data.notes.IrisDatabase
import com.naz.iris.data.notes.NotesRepository
import com.naz.iris.data.settings.ApiKeyRepository
import com.naz.iris.ui.screen.AgentVoiceScreen
import com.naz.iris.ui.screen.ApiKeyScreen
import com.naz.iris.ui.screen.VoiceTestScreen
import com.naz.iris.core.agent.ConversationMemory
import com.naz.iris.core.agent.IrisAgentOrchestrator
import com.naz.iris.core.reminder.ReminderNotificationHelper

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReminderNotificationHelper.ensureChannel(applicationContext)

        val appCtx = applicationContext
        val apiKeyRepo = ApiKeyRepository(appCtx)

        // Room + NotesRepo
        val db = IrisDatabase.getInstance(appCtx)
        val notesRepo = NotesRepository(db.notesDao())

        // Tool Dispatcher
        val toolDispatcher: IrisToolDispatcher = IrisToolDispatcherImpl(
            context = appCtx,
            notesRepository = notesRepo
        )

        // Gemini Client
        val geminiClient = GeminiClient(
            apiKeyProvider = { apiKeyRepo.loadApiKey() }
        )

        setContent {
            var screen by remember { mutableStateOf("home") }

            val loadedKey = apiKeyRepo.loadApiKey()
            val masked = loadedKey?.let { "••••••" + it.takeLast(4) }

            val memory = remember { ConversationMemory() }

            val orchestrator = remember {
                IrisAgentOrchestrator(
                    geminiClient = geminiClient,
                    toolDispatcher = toolDispatcher,
                    memory = memory
                )
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        "home" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Iris — Debug Home")

                                Button(onClick = { screen = "voice" }) {
                                    Text("Stage 2: Voice Test")
                                }

                                Button(onClick = { screen = "apikey" }) {
                                    Text("Stage 1: API Key")
                                }

                                Button(onClick = { screen = "agent" }) {
                                    Text("Stage 4: Agent + Memory 🚀")
                                }
                            }
                        }

                        "voice" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(onClick = { screen = "home" }) {
                                    Text("← Geri")
                                }

                                VoiceTestScreen(modifier = Modifier.fillMaxSize())
                            }
                        }

                        "apikey" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(onClick = { screen = "home" }) {
                                    Text("← Geri")
                                }

                                ApiKeyScreen(
                                    existingKeyMasked = masked,
                                    onSave = { apiKeyRepo.saveApiKey(it) },
                                    onClear = { apiKeyRepo.clearApiKey() }
                                )
                            }
                        }

                        "agent" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(onClick = { screen = "home" }) {
                                    Text("← Geri")
                                }

                                AgentVoiceScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    orchestrator = orchestrator
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}