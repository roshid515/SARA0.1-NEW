package com.roshid.sara01

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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

import com.google.firebase.FirebaseApp
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
    private lateinit var memory: android.content.SharedPreferences

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

        tts = TextToSpeech(this, this)

        buildInterface()
        initializeFirebaseAndAppCheck()

        append(
            "Sara",
            "Hello! I'm Sara. Ask me a question or use the Voice button."
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

        val sendRow = LinearLayout(this).apply {
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

        sendRow.addView(
            sendButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        sendRow.addView(
            voiceButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(sendRow)

        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val diagnosticButton = Button(this).apply {
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

        toolsRow.addView(
            diagnosticButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        toolsRow.addView(
            memoryButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(toolsRow)

        setContentView(root)
    }

    /*
     * DEVELOPMENT ONLY:
     * Install the Firebase App Check debug provider before
     * making Firebase AI Logic requests.
     */
    private fun initializeFirebaseAndAppCheck() {
        try {
            val firebaseApp = FirebaseApp.getInstance()

            val appCheck = FirebaseAppCheck.getInstance()

            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

            firebaseReady = firebaseApp.name.isNotEmpty()

            append(
                "Diagnostics",
                "Firebase initialized: $firebaseReady"
            )

            requestDebugToken()

        } catch (e: Exception) {
            firebaseReady = false

            append(
                "Diagnostics",
                "Firebase/App Check initialization failed.\n" +
                    "Error: ${safeError(e)}"
            )
        }
    }

    /*
     * Request an App Check token and display it in the app.
     * On a fresh installation, the debug provider may first
     * need to generate a token and log its registration value.
     */
    private fun requestDebugToken() {
        try {
            FirebaseAppCheck.getInstance()
                .getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    append(
                        "Firebase App Check",
                        "Token request succeeded.\n\n" +
                            "Token:\n${result.token}"
                    )
                }
                .addOnFailureListener { error ->
                    append(
                        "Firebase App Check",
                        "Token request failed.\n" +
                            "Error: ${safeError(error)}\n\n" +
                            "Check debug-token registration and App Check configuration."
                    )
                }
        } catch (e: Exception) {
            append(
                "Firebase App Check",
                "Could not request token: ${safeError(e)}"
            )
        }
    }

    private fun handleCommand(command: String) {
        val text = command.trim()

        if (text.isEmpty()) return

        append("You", text)

        when {
            text.startsWith("remember ", ignoreCase = true) -> {
                val note = text.substringAfter("remember ").trim()

                if (note.isNotEmpty()) {
                    saveMemory(note)
                    append("Sara", "I've saved that note on this device.")
                }

                return
            }

            text.equals("show memory", ignoreCase = true) -> {
                showMemory()
                return
            }

            text.equals("clear memory", ignoreCase = true) -> {
                memory.edit().clear().apply()
                append("Sara", "Local memory cleared.")
                return
            }

            text.equals("diagnostics", ignoreCase = true) -> {
                runDiagnostics()
                return
            }

            text.equals("show debug token", ignoreCase = true) -> {
                requestDebugToken()
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
                "Firebase has not initialized. Tap Diagnostics for details."
            )
            return
        }

        requestInProgress = true
        sendButton.isEnabled = false

        append("Sara", "Thinking...")

        appScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    model.generateContent(
                        """
                        You are Sara, a helpful Android voice assistant.
                        Answer naturally and clearly.

                        User message:
                        $text
                        """.trimIndent()
                    )
                }

                removeThinkingMessage()

                val answer = response.text?.trim()

                if (answer.isNullOrEmpty()) {
                    append(
                        "Sara Diagnostics",
                        "Gemini returned an empty response."
                    )
                } else {
                    append("Sara", answer)
                    speak(answer)
                }

            } catch (e: Exception) {
                removeThinkingMessage()

                append(
                    "Sara Diagnostics",
                    "Gemini request failed.\n" +
                        "Exception: ${e.javaClass.simpleName}\n" +
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
            Gemini model: gemini-2.5-flash
            Text-to-speech ready: $ttsReady
            Request in progress: $requestInProgress

            To request an App Check token, type:
            show debug token

            To test Gemini, send:
            Hello Sara
            """.trimIndent()
        )
    }

    private fun saveMemory(note: String) {
        val oldNotes = memory.getString("notes", "") ?: ""

        val updatedNotes = if (oldNotes.isBlank()) {
            note
        } else {
            "$oldNotes\n$note"
        }

        memory.edit()
            .putString("notes", updatedNotes)
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

    @Deprecated("Uses
