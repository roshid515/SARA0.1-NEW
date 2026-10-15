package com.roshid.sara01

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var transcript: TextView
    private lateinit var input: EditText

    private var tts: TextToSpeech? = null

    private val requestAudio = 42
    private val requestSpeech = 43

    private val prefs by lazy {
        getSharedPreferences("sara_memory", MODE_PRIVATE)
    }

    private val aiJob = SupervisorJob()

    private val aiScope = CoroutineScope(
        aiJob + Dispatchers.Main
    )

    private val aiModel by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-2.5-flash")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize App Check for development.
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

        tts = TextToSpeech(this, this)

        // Main screen
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 24)
            setBackgroundColor(0xFF101525.toInt())
        }

        val title = TextView(this).apply {
            text = "SARA0.1"
            textSize = 28f
            setTextColor(0xFFE8ECFF.toInt())
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Your Personal AI Assistant"
            textSize = 14f
            setTextColor(0xFFB9C4E8.toInt())
            gravity = Gravity.CENTER
        }

        // Chat area
        transcript = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFF4F6FF.toInt())
            text = "Sara: Hello! I'm SARA0.1. Ask me anything!"
            setPadding(12, 16, 12, 16)
        }

        val scroll = ScrollView(this).apply {
            addView(transcript)
        }

        // Message input
        input = EditText(this).apply {
            hint = "Type your message"
            setHintTextColor(0xFF9BA8CC.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setSingleLine(false)
        }

        // Send button
        val send = Button(this).apply {
            text = "Send"

            setOnClickListener {
                val message = input.text.toString().trim()

                if (message.isNotEmpty()) {
                    input.setText("")
                    handleCommand(message)
                }
            }
        }

        // Voice input button
        val speak = Button(this).apply {
            text = "🎙 Speak"

            setOnClickListener {
                startSpeechInput()
            }
        }

        // Save a memory
        val remember = Button(this).apply {
            text = "Remember this"

            setOnClickListener {
                val note = input.text.toString().trim()

                if (note.isNotEmpty()) {
                    saveNote(note)

                    append(
                        "Sara",
                        "I've saved that note on this phone."
                    )

                    input.setText("")
                }
            }
        }

        // Show saved memories
        val showMemory = Button(this).apply {
            text = "Show memory"

            setOnClickListener {
                val notes = prefs.getString("notes", "").orEmpty()

                if (notes.isBlank()) {
                    append("Sara", "Your local memory is empty.")
                } else {
                    append(
                        "Sara",
                        "Your saved notes:\n$notes"
                    )
                }
            }
        }

        // Add components to the screen
        root.addView(title)
        root.addView(subtitle)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(input)
        root.addView(send)
        root.addView(speak)
        root.addView(remember)
        root.addView(showMemory)

        setContentView(root)
    }

    // Handle user commands
    private fun handleCommand(command: String) {
        if (command.isBlank()) return

        append("You", command)

        when {
            command.equals("help", ignoreCase = true) -> {
                append(
                    "Sara",
                    "Ask a question, tap Speak, or save a note."
                )
            }

            command.startsWith(
                "remember ",
                ignoreCase = true
            ) -> {
                val note = command.substringAfter(" ").trim()

                if (note.isNotEmpty()) {
                    saveNote(note)
                    append("Sara", "Saved that note on this phone.")
                }
            }

            command.startsWith(
                "open ",
                ignoreCase = true
            ) -> {
                append(
                    "Sara",
                    "Phone-control actions aren't available yet."
                )
            }

            else -> {
                askGemini(command)
            }
        }
    }

    // Save notes locally on this phone
    private fun saveNote(note: String) {
        val oldNotes = prefs.getString("notes", "").orEmpty()

        val updatedNotes = if (oldNotes.isBlank()) {
            "• $note"
        } else {
            "$oldNotes\n• $note"
        }

        prefs.edit()
            .putString("notes", updatedNotes)
            .apply()
    }

    // Communicate with Gemini
    private fun askGemini(command: String) {
        append("Sara", "Thinking...")

        aiScope.launch {
            try {
                val result = aiModel.generateContent(command)

                val answer = result.text
                    ?: "Sorry, I couldn't generate a response."

                append("Sara", answer)

                tts?.speak(
                    answer,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "sara_reply"
                )

            } catch (e: Exception) {
                append(
                    "Sara",
                    "I couldn't connect to Gemini. Please check your internet connection, Firebase AI Logic configuration, and App Check setup."
                )
            }
        }
    }

    // Display messages
    private fun append(who: String, message: String) {
        transcript.append("\n\n$who: $message")
    }

    // Start speech recognition
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

        try {
            startActivityForResult(intent, requestSpeech)
        } catch (_: Exception) {
            append(
                "Sara",
                "Speech recognition isn't available on this device."
            )
        }
    }

    // Receive voice recognition results
    @Deprecated("Deprecated by Android, retained for compatibility.")
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
            val result = data
                ?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )
                ?.firstOrNull()

            if (!result.isNullOrBlank()) {
                handleCommand(result)
            }
        }
    }

    // Initialize text-to-speech
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    // Clean up resources
    override fun onDestroy() {
        aiJob.cancel()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
