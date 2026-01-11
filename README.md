# Iris — Voice-First Android Personal Assistant

Iris is a **voice-first** Android personal assistant built with **Kotlin**.  
It is designed as an **agent-based system**: the LLM decides, and the app takes real actions (notes, reminders, calls).

> Current stage: **Stage 1 and 2 complete** ✅

---

## Goals
- Always available on Android (no laptop / local LLM dependency)
- Short, natural Turkish responses (voice-first UX)
- Real device actions via tools (not a “basic chatbot”)

---

## Roadmap (Stage-based)
- ✅ **Stage 1:** Project setup + secure Gemini API key storage (Android Keystore + AES-GCM)
- ✅ **Stage 2:** STT + TTS (button-to-talk voice loop)
- ⏳ **Stage 3:** Gemini client (first LLM response)
- ⏳ **Stage 4:** Tool parsing + `add_note` + `list_recent_notes`
- ⏳ **Stage 5:** Room DB + time-range queries
- ⏳ **Stage 6:** Foreground service + wake word flow
- ⏳ **Stage 7:** `call_contact` + permissions
- ⏳ **Stage 8:** Stability, edge cases, README polish

---

## Tech Stack
- Kotlin
- Jetpack Compose
- Android Keystore (AES-GCM encryption)
- Room (planned)
- Gemini API (planned)

---

## Security Notes (Stage 1)
- The Gemini API key is **not stored in the repository** and is **not hardcoded**.
- The key is stored locally using **Android Keystore + AES-GCM**.

---

## How to Run
1. Open the project in Android Studio
2. Sync Gradle
3. Run on emulator or physical device
