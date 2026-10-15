package com.roshid.sara01

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject

import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "SARA"
        private const val PREFS = "sara_chat_preferences"
        private const val HISTORY_KEY = "conversation_history"
        private const val MODEL_NAME = "gemini-2.5-flash"
    }

    private lateinit var root: LinearLayout
    private lateinit var chatLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var statusView: TextView

    private val history = mutableListOf<JSONObject>()

    private var textToSpeech: TextToSpeech? = null
    private var speechReady = false
    private var isThinking = false
    private var lastReply = ""

    private val backgroundColor = Color.rgb(13, 18, 32)
    private val panelColor = Color.rgb(26, 35, 54)
    private val accentColor = Color.rgb(90, 190, 255)

    private val preferences by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val voiceLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

                if (!spokenText.isNullOrBlank()) {
                    inputBox.setText(spokenText)
                    inputBox.setSelection(inputBox.text.length)
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
                toast("Microphone permission was not granted.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            textToSpeech = TextToSpeech(this, this)
            buildInterface()
            restoreHistory()

            if (history.isEmpty()) {
                addMessage(
                    "assistant",
                    "Hello! 💙 I'm Sara, your AI assistant. How can I help you today?"
                )
            }

            updateStatus("Ready")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize the interface", e)
            showFatalError()
        }
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 15, 12, 15)
            background = rounded(panelColor, 20)
        }

        val title = TextView(this).apply {
            text = "SARA 0.1"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "Your AI assistant"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }

        header.addView(title)
        header.addView(subtitle)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        statusView = TextView(this).apply {
            text = "● Ready"
            textSize = 12f
            setTextColor(Color.rgb(110, 220, 155))
            setPadding(6, 8, 6, 8)
        }

        root.addView(statusView)

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 8, 2, 8)
        }

        scrollView.addView(chatLayout)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        inputBox = EditText(this).apply {
            hint = "Message Sara..."
            textSize = 16f
            setSingleLine(false)
            maxLines = 4
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(14, 12, 14, 12)
            background = rounded(panelColor, 16)
        }

        root.addView(
            inputBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
        )

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        voiceButton = makeButton("🎤 Voice")
        sendButton = makeButton("Send ➤")
        val speakButton = makeButton("🔊 Speak")

        voiceButton.setOnClickListener {
            requestMicrophonePermission()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        speakButton.setOnClickListener {
            if (lastReply.isNotBlank()) {
                speak(lastReply)
            } else {
                toast("Sara hasn't replied yet.")
            }
        }

        buttonRow.addView(
            voiceButton,
            LinearLayout.LayoutParams(0, 50, 1f).apply {
                rightMargin = 4
            }
        )

        buttonRow.addView(
            sendButton,
            LinearLayout.LayoutParams(0, 50, 1f).apply {
                leftMargin = 4
                rightMargin = 4
            }
        )

        buttonRow.addView(
            speakButton,
            LinearLayout.LayoutParams(0, 50, 1f).apply {
                leftMargin = 4
            }
        )

        root.addView(buttonRow)

        val clearButton = makeButton("Clear conversation")

        clearButton.setOnClickListener {
            clearHistory()
        }

        root.addView(
            clearButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                44
            ).apply {
                topMargin = 6
            }
        )

        setContentView(root)
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(
                Color.rgb(38, 100, 160),
                15
            )
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun addMessage(role: String, message: String) {
        val isUser = role == "user"

        val bubble = TextView(this).apply {
            text = (if (isUser) "You\n" else "Sara\n") + message
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(14, 12, 14, 12)
            background = rounded(
                if (isUser) {
                    Color.rgb(36, 83, 130)
                } else {
                    panelColor
                },
                16
            )
        }

        val params = LinearLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.80f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.bottomMargin = 10
        params.gravity = if (isUser) Gravity.END else Gravity.START

        chatLayout.addView(bubble, params)

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendMessage() {
        if (isThinking) {
            toast("Please wait for Sara's current response.")
            return
        }

        val message = inputBox.text.toString().trim()

        if (message.isEmpty()) return

        inputBox.setText("")

        val inputManager =
            getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            inputBox.windowToken,
            0
        )

        addMessage("user", message)
        addHistory("user", message)

        requestGemini(message)
    }

    private fun requestGemini(message: String) {
        isThinking = true
        sendButton.isEnabled = false
        updateStatus("Thinking...")

        lifecycleScope.launch {
            try {
                val recentHistory = history
                    .takeLast(16)
                    .joinToString("\n") { item ->
                        val role = item.optString("role")
                        val content = item.optString("text")

                        if (role == "user") {
                            "User: $content"
                        } else {
                            "Sara: $content"
                        }
                    }

                val prompt = """
                    You are Sara, a friendly, helpful AI assistant.
                    Answer the user's latest message clearly and naturally.
                    Do not claim to be human.

                    Conversation history:
                    $recentHistory

                    Latest user message:
                    $message
                """.trimIndent()

                val answer = withContext(Dispatchers.IO) {
                    val model = Firebase.ai(
                        backend = GenerativeBackend.googleAI()
                    ).generativeModel(
                        modelName = MODEL_NAME
                    )

                    model.generateContent(prompt).text
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "I couldn't generate a response. Please try again."
                }

                lastReply = answer

                addMessage("assistant", answer)
                addHistory("assistant", answer)
                updateStatus("Ready")

            } catch (e: Exception) {
                Log.e(TAG, "Gemini request failed", e)

                addMessage(
                    "assistant",
                    "I couldn't get a response from Gemini. Please check your internet connection and Firebase AI Logic configuration."
                )

                updateStatus("Connection error")

            } finally {
                isThinking = false
                sendButton.isEnabled = true
            }
        }
    }

    private fun addHistory(role: String, message: String) {
        history.add(
            JSONObject()
                .put("role", role)
                .put("text", message)
        )

        while (history.size > 100) {
            history.removeAt(0)
        }

        saveHistory()
    }

    private fun saveHistory() {
        try {
            val array = JSONArray()

            history.forEach { item ->
                array.put(item)
            }

            preferences.edit()
                .putString(HISTORY_KEY, array.toString())
                .apply()

        } catch (e: Exception) {
            Log.e(TAG, "Could not save conversation", e)
        }
    }

    private fun restoreHistory() {
        try {
            val saved = preferences.getString(
                HISTORY_KEY,
                "[]"
            ) ?: "[]"

            val array = JSONArray(saved)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val role = item.optString("role")
                val message = item.optString("text")

                if (
                    (role == "user" || role == "assistant") &&
                    message.isNotBlank()
                ) {
                    history.add(item)
                    addMessage(role, message)

                    if (role == "assistant") {
                        lastReply = message
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Could not restore conversation", e)
            history.clear()
        }
    }

    private fun clearHistory() {
        history.clear()
        lastReply = ""

        preferences.edit()
            .remove(HISTORY_KEY)
            .apply()

        chatLayout.removeAllViews()

        val welcome =
            "Conversation cleared! 💙 What can I help you with?"

        addMessage("assistant", welcome)
        addHistory("assistant", welcome)

        updateStatus("New conversation")
    }

    private fun requestMicrophonePermission() {
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

            voiceLauncher.launch(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Voice input failed", e)
            toast("Speech recognition isn't available on this device.")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(
                Locale.getDefault()
            )

            speechReady =
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun speak(message: String) {
        if (speechReady) {
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "SARA_REPLY"
            )
        } else {
            toast("The speech engine isn't ready yet.")
        }
    }

    private fun updateStatus(message: String) {
        if (!::statusView.isInitialized) return

        runOnUiThread {
            statusView.text = "● $message"
        }
    }

    private fun showFatalError() {
        val error = TextView(this).apply {
            text = "SARA0.1 couldn't start. Please check the app configuration."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
        }

        setContentView(error)
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
