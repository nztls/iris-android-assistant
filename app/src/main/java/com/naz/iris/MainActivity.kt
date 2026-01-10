package com.naz.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.naz.iris.data.settings.ApiKeyRepository
import com.naz.iris.ui.screen.ApiKeyScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = ApiKeyRepository(applicationContext)

        setContent {
            val loadedKey = repo.loadApiKey()
            val masked = loadedKey?.let { "••••••" + it.takeLast(4) }

            MaterialTheme {
                Surface {
                    ApiKeyScreen(
                        existingKeyMasked = masked,
                        onSave = { repo.saveApiKey(it) },
                        onClear = { repo.clearApiKey() }
                    )
                }
            }
        }
    }
}
