package com.naz.iris.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    existingKeyMasked: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Iris • Aşama 1", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        if (existingKeyMasked != null) {
            Text("Kayıtlı key var: $existingKeyMasked")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onClear()
                status = "Key silindi."
            }) { Text("Key’i Sil") }
        } else {
            Text("Gemini API key yok. Girip kaydedelim.")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val trimmed = input.trim()
                if (trimmed.isEmpty()) {
                    status = "Boş key kaydetmem. 😄"
                } else {
                    onSave(trimmed)
                    input = ""
                    status = "Key kaydedildi. Iris hazır."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kaydet")
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Not: Bu aşamada API çağrısı yok. Sadece güvenli saklama var.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
