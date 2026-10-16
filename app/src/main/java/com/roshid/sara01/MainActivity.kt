package com.roshid.sara01

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        private const val REQUEST_MICROPHONE = 42
        private const val REQUEST_SPEECH = 43
        private const val MEMORY_FILE = "sara_memory"
        private const val MEMORY_KEY = "saved_memory"
    }

    private lateinit var chatText: TextView
    private lateinit var inputText: EditText
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var memoryButton: Button
    private lateinit var clearMemoryButton: Button

    private lateinit var preferences: SharedPreferences

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var isThinking = false

    private val model by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-3.8-flash")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences(
            MEMORY_FILE,
            MODE_PRIVATE
        )

        // Initialize Firebase.
        try {
            Firebase.initialize(context = this)

            // Debug App Check is for development/testing.
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "SARA0.1",
                "Firebase initialization error",
                e
            )
        }

        textToSpeech = TextToSpeech(this, this)

        createInterface()

        addMessage(
            "SARA0.1",
            "Hello! I'm Sara. You can type or speak to me. " +
                "I can answer using Gemini AI and save simple memories on this device."
        )

        requestMicrophonePermissionIfNeeded()
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val title = TextView(this).apply {
            text = "SARA0.1"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 16)
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        chatText = TextView(this).apply {
            textSize = 16f
            setPadding(8, 8, 8, 8)
            text = ""
        }

        val scrollView = ScrollView(this).apply {
            addView(chatText)
        }

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        inputText = EditText(this).apply {
            hint = "Type a message..."
            textSize = 16f
            minLines = 1
            maxLines = 4
        }

        root.addView(
            inputText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener {
                sendMessage()
            }
        }

        voiceButton = Button(this).apply {
            text = "Speak"
            setOnClickListener {
                startVoiceInput()
            }
        }

        memoryButton = Button(this).apply {
            text = "Show Memory"
            setOnClickListener {
                showMemory()
            }
        }

        clearMemoryButton = Button(this).apply {
            text = "Clear Memory"
            setOnClickListener {
                clearMemory()
            }
        }

        root.addView(sendButton)
        root.addView(voiceButton)
        root.addView(memoryButton)
        root.addView(clearMemoryButton)

        setContentView(root)
    }

    private fun addMessage(sender: String, message: String) {
        runOnUiThread {
            chatText.append("$sender: $message\n\n")
        }
    }

    private fun setBusy(busy: Boolean) {
        isThinking = busy

        runOnUiThread {
            sendButton.isEnabled = !busy
            sendButton.text = if (busy) "Thinking..." else "Send"
        }
    }

    private fun sendMessage() {
        if (isThinking) return

        val message = inputText.text.toString().trim()

        if (message.isEmpty()) {
            Toast.makeText(
                this,
                "Please enter a message.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        inputText.setText("")

        addMessage("You", message)

        // Handle local memory commands without calling Gemini.
        if (handleMemoryCommand(message)) {
            return
        }

        askGemini(message)
    }

    private fun askGemini(message: String) {
        setBusy(true)
        addMessage("Sara", "Thinking...")

        lifecycleScope.launch {
            try {
                val memory = preferences.getString(
                    MEMORY_KEY,
                    ""
                ).orEmpty()

                val prompt = """
                    You are Sara, the helpful AI assistant inside SARA0.1,
                    an Android app.

                    Be friendly, clear, and honest.
                    Do not claim to remember information that is not provided.

                    Saved user memories:
                    ${if (memory.isBlank()) "No saved memories." else memory}

                    User message:
                    $message
                """.trimIndent()

                val answer = withContext(Dispatchers.IO) {
                    model.generateContent(prompt).text
                }

                val reply = answer?.trim().takeUnless {
                    it.isNullOrBlank()
                } ?: "I couldn't produce a text response. Please try again."

                removeThinkingMessage()
                addMessage("Sara", reply)
                speak(reply)

            } catch (e: Exception) {
                android.util.Log.e(
                    "SARA0.1",
                    "Gemini request failed",
                    e
                )

                removeThinkingMessage()

                addMessage(
                    "Sara",
                    "I couldn't connect to Gemini. Check your internet " +
                        "connection, Firebase setup, App Check registration, " +
                        "and the model configuration. Please try again."
                )

            } finally {
                setBusy(false)
            }
        }
    }

    private fun removeThinkingMessage() {
        runOnUiThread {
            val current = chatText.text.toString()
            val updated = current.replace(
                "Sara: Thinking...\n\n",
                ""
            )
            chatText.text = updated
        }
    }

    private fun handleMemoryCommand(message: String): Boolean {
        val normalized = message.trim()
        val lower = normalized.lowercase(Locale.ROOT)

        when {
            lower == "show memory" ||
                lower == "what do you remember" -> {
                showMemory()
                return true
            }

            lower.startsWith("remember ") -> {
                val newMemory = normalized.substringAfter(
                    "remember ",
                    ""
                ).trim()

                if (newMemory.isBlank()) {
                    addMessage(
                        "Sara",
                        "Please write what you want me to remember."
                    )
                    return true
                }

                val existing = preferences.getString(
                    MEMORY_KEY,
                    ""
                ).orEmpty()

                val updated = if (existing.isBlank()) {
                    newMemory
                } else {
                    "$existing\n$newMemory"
                }

                preferences.edit()
                    .putString(MEMORY_KEY, updated)
                    .apply()

                addMessage(
                    "Sara",
                    "Saved to this device's local memory."
                )

                return true
            }

            lower == "clear memory" -> {
                clearMemory()
                return true
            }
        }

        return false
    }

    private fun showMemory() {
        val saved = preferences.getString(
            MEMORY_KEY,
            ""
        ).orEmpty()

        if (saved.isBlank()) {
            addMessage("Sara", "Your local memory is empty.")
        } else {
            addMessage(
                "Sara",
                "Memories saved on this device:\n$saved"
            )
        }
    }

    private fun clearMemory() {
        preferences.edit()
            .remove(MEMORY_KEY)
            .apply()

        addMessage(
            "Sara",
            "Local memory cleared."
        )
    }

    private fun requestMicrophonePermissionIfNeeded() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE
            )
        }
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this,
                "Speech recognition is unavailable on this device.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophonePermissionIfNeeded()
            return
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            )

            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak to Sara"
            )
        }

        try {
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not start speech recognition.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Uses the Android speech recognition activity result API.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == REQUEST_SPEECH &&
            resultCode == RESULT_OK
        ) {
            val results = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )

            val spokenText = results?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                inputText.setText(spokenText)
                inputText.setSelection(inputText.text.length)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(
                Locale.getDefault()
            )

            ttsReady =
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED

            if (!ttsReady) {
                android.util.Log.w(
                    "SARA0.1",
                    "Default device language is not supported by TTS."
                )
            }
        } else {
            ttsReady = false

            android.util.Log.e(
                "SARA0.1",
                "Text-to-speech initialization failed."
            )
        }
    }

    private fun speak(message: String) {
        if (!ttsReady) return

        runOnUiThread {
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "SARA_REPLY"
            )
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
