package com.roshid.sara01

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var scrollView: ScrollView
    private lateinit var memory: SharedPreferences

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var firebaseReady = false
    private var requestInProgress = false

    private val requestSpeech = 43
    private val requestAudio = 42

    private val appScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val model by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-2.5-flash")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        memory = getSharedPreferences(
            "sara_memory",
            Context.MODE_PRIVATE
        )

        buildInterface()

        tts = TextToSpeech(this, this)

        initializeFirebase()

        append(
            "Sara",
            "Hello! I'm Sara. Ask me a question or tap Voice."
        )
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 12)
        }

        val title = TextView(this).apply {
            text = "SARA0.1"
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 16)
        }

        root.addView(title)

        scrollView = ScrollView(this)

        transcript = TextView(this).apply {
            textSize = 16f
            setPadding(8, 8, 8, 8)
        }

        scrollView.addView(transcript)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        input = EditText(this).apply {
            hint = "Message Sara..."
            textSize = 16f
            maxLines = 4
        }

        root.addView(input)

        val messageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        sendButton = Button(this).apply {
            text = "Send"

            setOnClickListener {
                val message = input.text.toString().trim()

                if (message.isNotEmpty()) {
                    input.setText("")
                    handleCommand(message)
                }
            }
        }

        val voiceButton = Button(this).apply {
            text = "Voice"
            setOnClickListener {
                startSpeechInput()
            }
        }

        messageRow.addView(
            sendButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        messageRow.addView(
            voiceButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(messageRow)

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val diagnosticsButton = Button(this).apply {
            text = "Diagnostics"
            setOnClickListener {
                runDiagnostics()
            }
        }

        val memoryButton = Button(this).apply {
            text = "Memory"
            setOnClickListener {
                showMemory()
            }
        }

        utilityRow.addView(
            diagnosticsButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        utilityRow.addView(
            memoryButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(utilityRow)

        setContentView(root)
    }

    private fun initializeFirebase() {
        try {
            /*
             * Initialize Firebase before using Firebase services.
             */
            Firebase.initialize(context = this)

            /*
             * Development builds only.
             * Install the debug provider before requesting
             * App Check tokens or using Firebase AI Logic.
             */
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

            firebaseReady = true

            append(
                "Diagnostics",
                "Firebase initialization: PASS"
            )

            requestAppCheckToken()

        } catch (e: Exception) {
            firebaseReady = false

            append(
                "Diagnostics",
                "Firebase initialization failed.\n" +
                    "Type: ${e.javaClass.simpleName}\n" +
                    "Details: ${safeError(e)}"
            )
        }
    }

    private fun requestAppCheckToken() {
        try {
            FirebaseAppCheck.getInstance()
                .getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    append(
                        "App Check",
                        "App Check token request: SUCCESS\n" +
                            "A token was received.\n" +
                            "Token length: ${result.token.length}"
                    )
                }
                .addOnFailureListener { error ->
                    append(
                        "App Check",
                        "Token request failed.\n" +
                            "Type: ${error.javaClass.simpleName}\n" +
                            "Details: ${safeError(error)}"
                    )
                }
        } catch (e: Exception) {
            append(
                "App Check",
                "Token request error: ${safeError(e)}"
            )
        }
    }

    private fun handleCommand(command: String) {
        val message = command.trim()

        if (message.isEmpty()) return

        append("You", message)

        when {
            message.startsWith(
                "remember ",
                ignoreCase = true
            ) -> {
                val note = message.substringAfter("remember ").trim()

                if (note.isNotEmpty()) {
                    saveMemory(note)
                    append("Sara", "I've saved that note on this device.")
                }

                return
            }

            message.equals(
                "show memory",
                ignoreCase = true
            ) -> {
                showMemory()
                return
            }

            message.equals(
                "clear memory",
                ignoreCase = true
            ) -> {
                memory.edit().clear().apply()
                append("Sara", "Local memory cleared.")
                return
            }

            message.equals(
                "diagnostics",
                ignoreCase = true
            ) -> {
                runDiagnostics()
                return
            }

            message.equals(
                "show debug token",
                ignoreCase = true
            ) -> {
                requestAppCheckToken()
                return
            }
        }

        if (requestInProgress) {
            append("Sara", "Please wait for my current response.")
            return
        }

        if (!firebaseReady) {
            append(
                "Sara",
                "Firebase initialization failed. Tap Diagnostics."
            )
            return
        }

        requestInProgress = true
        sendButton.isEnabled = false

        append("Sara", "Thinking...")

        appScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    model.generateContent(message)
                }

                removeThinkingMessage()

                val answer = response.text?.trim()

                if (answer.isNullOrEmpty()) {
                    append(
                        "Diagnostics",
                        "Gemini returned no text."
                    )
                } else {
                    append("Sara", answer)
                    speak(answer)
                }

            } catch (e: Exception) {
                removeThinkingMessage()

                append(
                    "Gemini Diagnostics",
                    "Request failed.\n" +
                        "Type: ${e.javaClass.simpleName}\n" +
                        "Details: ${safeError(e)}"
                )
            } finally {
                requestInProgress = false
                sendButton.isEnabled = true
            }
        }
    }

    private fun runDiagnostics() {
        append(
            "SARA Diagnostics",
            """
            Firebase initialized: $firebaseReady
            Model: gemini-2.5-flash
            Text-to-speech ready: $ttsReady
            Gemini request in progress: $requestInProgress

            Type "show debug token" to request an App Check token.

            Send "Hello Sara" to test Gemini.
            """.trimIndent()
        )

        requestAppCheckToken()
    }

    private fun saveMemory(note: String) {
        val previous = memory.getString("notes", "") ?: ""

        val updated = if (previous.isBlank()) {
            note
        } else {
            "$previous\n$note"
        }

        memory.edit()
            .putString("notes", updated)
            .apply()
    }

    private fun showMemory() {
        val notes = memory.getString("notes", "")

        append(
            "Sara Memory",
            if (notes.isNullOrBlank()) {
                "No saved notes. Try: remember I like music"
            } else {
                notes
            }
        )
    }

    private fun append(speaker: String, message: String) {
        runOnUiThread {
            transcript.append("$speaker: $message\n\n")

            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun removeThinkingMessage() {
        runOnUiThread {
            transcript.text = transcript.text.toString().replace(
                "Sara: Thinking...\n\n",
                ""
            )
        }
    }

    private fun safeError(error: Throwable): String {
        return (error.message ?: "No additional details").take(700)
    }

    private fun startSpeechInput() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                requestAudio
            )
            return
        }

        launchSpeechRecognizer()
    }

    private fun launchSpeechRecognizer() {
        try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak to Sara"
                )
            }

            startActivityForResult(intent, requestSpeech)

        } catch (e: Exception) {
            append(
                "Sara",
                "Speech recognition unavailable: ${safeError(e)}"
            )
        }
    }

    @Deprecated("Legacy speech activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == requestSpeech &&
            resultCode == RESULT_OK
        ) {
            val results = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )

            val spokenText = results?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                handleCommand(spokenText)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == requestAudio) {
            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                launchSpeechRecognizer()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required for voice input.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val languageResult =
                tts?.setLanguage(Locale.getDefault())

            ttsReady =
                languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            ttsReady = false
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "SARA_RESPONSE"
            )
        }
    }

    override fun onDestroy() {
        appScope.cancel()

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }
}
