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
import android.util.Log

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "SARA0.1"

        private const val REQUEST_MICROPHONE = 42
        private const val REQUEST_SPEECH = 43

        private const val MEMORY_FILE = "sara_memory"
        private const val MEMORY_KEY = "saved_memory"
    }

    // A coroutine scope owned by this Activity.
    private val activityScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private lateinit var chatText: TextView
    private lateinit var inputText: EditText
    private lateinit var sendButton: Button

    private lateinit var preferences: SharedPreferences

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var isThinking = false

    private val model by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-2.5-flash")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences(
            MEMORY_FILE,
            MODE_PRIVATE
        )

        initializeFirebase()

        textToSpeech = TextToSpeech(this, this)

        createInterface()

        addMessage(
            "Sara",
            "Hello! I'm Sara, your SARA0.1 assistant. 😊\n\n" +
                "You can type a message, use voice input, or ask me " +
                "to remember something on this device."
        )

        requestMicrophonePermissionIfNeeded()
    }

    private fun initializeFirebase() {
        try {
            Firebase.initialize(context = this)

            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

            Log.i(TAG, "Firebase initialized.")

        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed.", e)
        }
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
        }

        val title = TextView(this).apply {
            text = "SARA0.1 💙"
            textSize = 27f
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
            setPadding(10, 10, 10, 10)
            text = ""
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                chatText,
                ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
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
            hint = "Talk to Sara..."
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

        val voiceButton = Button(this).apply {
            text = "🎤 Speak"
            setOnClickListener {
                startVoiceInput()
            }
        }

        val memoryButton = Button(this).apply {
            text = "Show Memory"
            setOnClickListener {
                showMemory()
            }
        }

        val clearMemoryButton = Button(this).apply {
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
                "Type a message first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        inputText.setText("")

        addMessage("You", message)

        if (handleMemoryCommand(message)) {
            return
        }

        askGemini(message)
    }

    private fun askGemini(message: String) {
        setBusy(true)
        addMessage("Sara", "Thinking...")

        activityScope.launch {
            try {
                val savedMemory = preferences.getString(
                    MEMORY_KEY,
                    ""
                ).orEmpty()

                val prompt = """
                    You are Sara, the helpful AI assistant inside SARA0.1.

                    Be friendly, clear, respectful, and honest.
                    Give useful answers.
                    Do not claim to remember something unless it appears
                    in the saved memories or current conversation.

                    Saved memories from this device:
                    ${if (savedMemory.isBlank()) "None" else savedMemory}

                    User message:
                    $message
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    model.generateContent(prompt)
                }

                val reply = response.text
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "I couldn't generate a text response. Please try again."

                removeThinkingMessage()
                addMessage("Sara", reply)
                speak(reply)

            } catch (e: Exception) {
                Log.e(TAG, "Gemini request failed.", e)

                removeThinkingMessage()

                addMessage(
                    "Sara",
                    "I couldn't get a response from Gemini. Please check " +
                        "your internet connection, Firebase configuration, " +
                        "App Check registration, and model availability."
                )

            } finally {
                setBusy(false)
            }
        }
    }

    private fun removeThinkingMessage() {
        val current = chatText.text.toString()

        chatText.text = current.replace(
            "Sara: Thinking...\n\n",
            ""
        )
    }

    private fun handleMemoryCommand(message: String): Boolean {
        val trimmed = message.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        when {
            lower == "show memory" ||
                lower == "what do you remember" -> {

                showMemory()
                return true
            }

            lower.startsWith("remember ") -> {
                val newMemory = trimmed
                    .substringAfter("remember ")
                    .trim()

                if (newMemory.isBlank()) {
                    addMessage(
                        "Sara",
                        "Tell me what you'd like me to remember."
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
                    "I've saved that in this device's local memory. 💙"
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
            addMessage(
                "Sara",
                "Your local memory is empty."
            )
        } else {
            addMessage(
                "Sara",
                "Here's what is saved in local memory:\n$saved"
            )
        }
    }

    private fun clearMemory() {
        preferences.edit()
            .remove(MEMORY_KEY)
            .apply()

        addMessage(
            "Sara",
            "Local memory has been cleared."
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
            startActivityForResult(
                intent,
                REQUEST_SPEECH
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not start speech recognition.", e)

            Toast.makeText(
                this,
                "Couldn't start speech recognition.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Uses Android's speech recognition activity result API.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

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
                Log.w(
                    TAG,
                    "The default language may not be supported by TTS."
                )
            }
        } else {
            ttsReady = false
            Log.e(TAG, "Text-to-speech initialization failed.")
        }
    }

    private fun speak(message: String) {
        if (!ttsReady) return

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "SARA_REPLY"
        )
    }

    override fun onDestroy() {
        activityScope.cancel()

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
