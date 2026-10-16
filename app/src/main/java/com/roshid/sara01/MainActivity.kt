package com.roshid.sara01

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
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
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import kotlinx.coroutines.CancellationException
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
        private const val REQUEST_SPEECH = 43
        private const val REQUEST_MICROPHONE = 42
        private const val PREFS_NAME = "sara_memory"
        private const val MEMORY_KEY = "saved_notes"
    }

    private lateinit var chatLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var input: EditText
    private lateinit var memory: SharedPreferences

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var thinkingMessage: TextView? = null

    private val activityScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /*
     * Firebase Gemini model.
     * If this model is unavailable for your Firebase project,
     * the diagnostic message will help identify the problem.
     */
    private val model by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-2.5-flash")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        memory = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

        /*
         * Initialize Firebase and App Check.
         *
         * The debug provider is intended for development/testing.
         * A registered debug token may be required in Firebase Console.
         */
        try {
            Firebase.initialize(context = this)

            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

            Log.d(TAG, "Firebase initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed.", e)
        }

        tts = TextToSpeech(this, this)

        createInterface()

        addMessage(
            "Sara",
            "Hello! I'm Sara, your SARA0.1 assistant!\n" +
                "You can type messages, speak to me, and save " +
                "simple memories on this device."
        )
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        val title = TextView(this).apply {
            text = "SARA0.1"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFF202020.toInt())
            setPadding(8, 8, 8, 16)
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scrollView = ScrollView(this)

        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 12)
        }

        scrollView.addView(
            chatLayout,
            ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        input = EditText(this).apply {
            hint = "Message Sara..."
            textSize = 16f
            minLines = 1
            maxLines = 4
        }

        root.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener {
                sendTypedMessage()
            }
        }

        root.addView(
            sendButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val voiceButton = Button(this).apply {
            text = "🎤 Speak to Sara"
            setOnClickListener {
                startSpeechRecognition()
            }
        }

        root.addView(
            voiceButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val memoryButton = Button(this).apply {
            text = "🧠 View Memory"
            setOnClickListener {
                showMemory()
            }
        }

        root.addView(
            memoryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val clearMemoryButton = Button(this).apply {
            text = "Clear Memory"
            setOnClickListener {
                clearMemory()
            }
        }

        root.addView(
            clearMemoryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun sendTypedMessage() {
        val message = input.text.toString().trim()

        if (message.isEmpty()) {
            Toast.makeText(
                this,
                "Please type a message first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        input.text.clear()
        handleUserMessage(message)
    }

    private fun handleUserMessage(message: String) {
        addMessage("You", message)

        val lower = message.lowercase(Locale.ROOT).trim()

        when {
            lower.startsWith("remember ") -> {
                val note = message.substringAfter(
                    "remember ",
                    ""
                ).trim()

                if (note.isNotEmpty()) {
                    saveMemory(note)
                    addMessage(
                        "Sara",
                        "I'll save that in this device's local memory."
                    )
                } else {
                    addMessage(
                        "Sara",
                        "Tell me what you would like me to remember."
                    )
                }
            }

            lower == "show memory" ||
                lower == "view memory" ||
                lower == "what do you remember" -> {
                showMemory()
            }

            lower == "clear memory" -> {
                clearMemory()
            }

            else -> {
                askGemini(message)
            }
        }
    }

    private fun askGemini(userMessage: String) {
        addThinkingMessage()

        activityScope.launch {
            try {
                val savedNotes = memory.getString(
                    MEMORY_KEY,
                    ""
                ) ?: ""

                val prompt = """
                    You are Sara, the friendly AI assistant inside
                    the Android app SARA0.1.

                    Be helpful, respectful, clear, and honest.
                    Do not claim to have performed actions you
                    have not performed.

                    The following notes are saved locally by the
                    user. Use them only when relevant:
                    $savedNotes

                    User message:
                    $userMessage
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    model.generateContent(prompt)
                }

                val answer = response.text
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "I received a response, but it contained no text."

                removeThinkingMessage()
                addMessage("Sara", answer)
                speak(answer)

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                Log.e(TAG, "Gemini request failed.", e)

                removeThinkingMessage()

                val errorDetails =
                    e.message ?: e.javaClass.simpleName

                addMessage(
                    "Sara",
                    "Gemini connection error:\n" +
                        "${e.javaClass.simpleName}\n" +
                        errorDetails
                )

                Toast.makeText(
                    this@MainActivity,
                    "Gemini failed: $errorDetails",
                    Toast.LENGTH_LONG
                ).show()

            } finally {
                removeThinkingMessage()
            }
        }
    }

    private fun addMessage(
        speaker: String,
        message: String
    ) {
        val textView = TextView(this).apply {
            text = "$speaker: $message"
            textSize = 16f
            setTextColor(0xFF202020.toInt())
            setPadding(12, 10, 12, 10)
        }

        chatLayout.addView(
            textView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scrollToBottom()
    }

    private fun addThinkingMessage() {
        removeThinkingMessage()

        thinkingMessage = TextView(this).apply {
            text = "Sara is thinking..."
            textSize = 15f
            setPadding(12, 10, 12, 10)
        }

        chatLayout.addView(
            thinkingMessage,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scrollToBottom()
    }

    private fun removeThinkingMessage() {
        thinkingMessage?.let {
            chatLayout.removeView(it)
        }

        thinkingMessage = null
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun saveMemory(note: String) {
        val existing = memory.getString(
            MEMORY_KEY,
            ""
        ) ?: ""

        val updated = if (existing.isBlank()) {
            note
        } else {
            "$existing\n$note"
        }

        val saved = memory.edit()
            .putString(MEMORY_KEY, updated)
            .commit()

        if (saved) {
            addMessage(
                "Sara",
                "Memory saved on this device."
            )
        } else {
            addMessage(
                "Sara",
                "I couldn't save that memory."
            )
        }
    }

    private fun showMemory() {
        val savedNotes = memory.getString(
            MEMORY_KEY,
            ""
        ) ?: ""

        if (savedNotes.isBlank()) {
            addMessage(
                "Sara",
                "Your local memory is empty."
            )
        } else {
            addMessage(
                "Sara",
                "Here's what I remember:\n$savedNotes"
            )
        }
    }

    private fun clearMemory() {
        memory.edit()
            .remove(MEMORY_KEY)
            .apply()

        addMessage(
            "Sara",
            "Local memory cleared."
        )
    }

    private fun startSpeechRecognition() {
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
            return
        }

        launchSpeechRecognizer()
    }

    private fun launchSpeechRecognizer() {
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
            startActivityForResult(
                intent,
                REQUEST_SPEECH
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Speech recognition is unavailable on this device.",
                Toast.LENGTH_LONG
            ).show()

            Log.e(TAG, "Speech recognition failed.", e)
        }
    }

    @Deprecated("Uses Activity result API for compatibility")
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

            val spokenText = results
                ?.firstOrNull()
                ?.trim()

            if (!spokenText.isNullOrEmpty()) {
                input.setText(spokenText)
                input.setSelection(input.text.length)
                handleUserMessage(spokenText)
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

        if (requestCode == REQUEST_MICROPHONE) {
            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                launchSpeechRecognizer()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is needed for voice input.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())

            ttsReady =
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED

            if (!ttsReady) {
                Log.w(TAG, "Default TTS language is unavailable.")
            }
        } else {
            Log.e(TAG, "Text-to-speech initialization failed.")
        }
    }

    private fun speak(message: String) {
        if (ttsReady) {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "SARA_REPLY"
            )
        }
    }

    override fun onDestroy() {
        activityScope.cancel()

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }
}
