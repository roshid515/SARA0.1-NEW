package com.roshid.sara01

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : androidx.appcompat.app.AppCompatActivity(),
    android.speech.tts.TextToSpeech.OnInitListener {

    private lateinit var messagesLayout: LinearLayout
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var scrollView: ScrollView

    private var textToSpeech: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var isSending = false

    private val preferences by lazy {
        getSharedPreferences("sara_notes", MODE_PRIVATE)
    }

    private val notes = mutableListOf<String>()

    private val aiModel by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel(
            modelName = "gemini-2.5-flash"
        )
    }

    private val voiceLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

                if (!spokenText.isNullOrBlank()) {
                    inputMessage.setText(spokenText)
                    sendMessage()
                }
            }
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startVoiceInput()
            } else {
                showToast("Microphone permission denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech =
            android.speech.tts.TextToSpeech(this, this)

        loadNotes()
        buildInterface()

        addMessage(
            "Hi! I'm Sara. How can I help you today?",
            false
        )
    }

    private fun buildInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 12)
            setBackgroundColor(0xFFF5F7FB.toInt())
        }

        val title = TextView(this).apply {
            text = "SARA 0.1"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(0xFF3347A8.toInt())
            setPadding(8, 12, 8, 4)
        }

        val subtitle = TextView(this).apply {
            text = "Your AI Assistant"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 12)
        }

        messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }

        // FIXED: Explicitly use ViewGroup.LayoutParams.
        scrollView = ScrollView(this).apply {
            isFillViewport = true

            addView(
                messagesLayout,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        inputMessage = EditText(this).apply {
            hint = "Message Sara..."
            textSize = 16f
            maxLines = 4
            setPadding(12, 8, 12, 8)
        }

        sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener {
                sendMessage()
            }
        }

        val voiceButton = Button(this).apply {
            text = "🎤"
            contentDescription = "Voice input"

            setOnClickListener {
                requestVoiceInput()
            }
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                inputMessage,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(voiceButton)
            addView(sendButton)
        }

        val saveNoteButton = Button(this).apply {
            text = "Save Note"

            setOnClickListener {
                saveNote()
            }
        }

        root.addView(title)
        root.addView(subtitle)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(inputRow)
        root.addView(saveNoteButton)

        setContentView(root)
    }

    private fun addMessage(message: String, isUser: Boolean) {

        val messageView = TextView(this).apply {
            text = if (isUser) {
                "You: $message"
            } else {
                "Sara: $message"
            }

            textSize = 16f
            setTextColor(0xFF202124.toInt())
            setPadding(14, 12, 14, 12)

            setBackgroundColor(
                if (isUser) {
                    0xFFDCE6FF.toInt()
                } else {
                    0xFFFFFFFF.toInt()
                }
            )
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(0, 6, 0, 6)

        messagesLayout.addView(messageView, params)

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendMessage() {

        if (isSending) return

        val message = inputMessage.text.toString().trim()

        if (message.isEmpty()) return

        inputMessage.setText("")
        addMessage(message, true)

        if (message.startsWith("remember ", ignoreCase = true)) {
            val note = message.substringAfter(" ").trim()

            if (note.isNotEmpty()) {
                notes.add(note)
                saveNotesToStorage()

                addMessage("I've saved your note on this device.", false)
                speak("I've saved your note.")
                return
            }
        }

        isSending = true
        sendButton.isEnabled = false

        addMessage("Thinking...", false)

        lifecycleScope.launch {

            try {
                val response = withContext(Dispatchers.IO) {
                    aiModel.generateContent(message)
                }

                val answer = response.text
                    ?.takeIf { it.isNotBlank() }
                    ?: "Gemini returned an empty response. Please try again."

                removeLastMessage()
                addMessage(answer, false)
                speak(answer)

            } catch (e: Exception) {

                removeLastMessage()

                val details =
                    "${e.javaClass.simpleName}: ${e.message ?: "No details"}"

                addMessage(
                    "I couldn't connect to Gemini.\n\n" +
                    "Debug error: $details\n\n" +
                    "Check your internet, Firebase AI Logic setup, " +
                    "model availability, and App Check configuration.",
                    false
                )

                android.util.Log.e(
                    "SARA_GEMINI",
                    "Gemini request failed",
                    e
                )

            } finally {
                isSending = false
                sendButton.isEnabled = true
            }
        }
    }

    private fun removeLastMessage() {
        if (messagesLayout.childCount > 0) {
            messagesLayout.removeViewAt(
                messagesLayout.childCount - 1
            )
        }
    }

    private fun requestVoiceInput() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startVoiceInput() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showToast("Voice recognition is unavailable.")
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
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            showToast("Could not start voice input.")
            android.util.Log.e("SARA_VOICE", "Voice error", e)
        }
    }

    private fun saveNote() {

        val note = inputMessage.text.toString().trim()

        if (note.isEmpty()) {
            showToast("Type a note first.")
            return
        }

        notes.add(note)
        saveNotesToStorage()

        inputMessage.setText("")
        addMessage("Note saved on this device.", false)
        speak("Note saved.")
    }

    private fun saveNotesToStorage() {
        preferences.edit()
            .putStringSet("notes", notes.toSet())
            .apply()
    }

    private fun loadNotes() {
        notes.clear()

        preferences.getStringSet("notes", emptySet())
            ?.let { notes.addAll(it) }
    }

    private fun speak(message: String) {
        if (ttsReady) {
            textToSpeech?.speak(
                message,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                "sara_response"
            )
        }
    }

    override fun onInit(status: Int) {

        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())

            ttsReady =
                result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
    }
