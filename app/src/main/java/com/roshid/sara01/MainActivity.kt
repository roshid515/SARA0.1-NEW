package com.roshid.sara01

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
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
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject

import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var root: LinearLayout
    private lateinit var chatLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var speakButton: Button
    private lateinit var preferences: SharedPreferences

    private var textToSpeech: TextToSpeech? = null
    private var speechReady = false
    private var isThinking = false

    private var lastReply = ""

    private val conversation = mutableListOf<JSONObject>()

    private val backgroundColor = Color.rgb(13, 18, 32)
    private val panelColor = Color.rgb(24, 32, 51)
    private val accentColor = Color.rgb(75, 180, 255)
    private val textColor = Color.WHITE

    private val speechLauncher =
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

    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startListening()
            } else {
                showToast("Microphone permission is required for voice input.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences(
            "sara_preferences",
            Context.MODE_PRIVATE
        )

        textToSpeech = TextToSpeech(this, this)

        buildInterface()
        loadConversation()

        if (conversation.isEmpty()) {
            addMessage(
                "assistant",
                "Hello! 💙 I'm Sara, your AI assistant. What would you like to talk about?"
            )
        }

        initializeFirebaseAI()
    }

    private fun initializeFirebaseAI() {
        try {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )

            showStatus("Sara is ready to chat.")
        } catch (e: Exception) {
            showStatus("Chat setup needs checking.")
            android.util.Log.e(
                "SARA",
                "Firebase App Check initialization failed",
                e
            )
        }
    }

    private fun buildInterface() {

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(16, 12, 16, 12)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
            background = roundedBackground(panelColor, 22f)
        }

        val title = TextView(this).apply {
            text = "SARA 0.1"
            textSize = 29f
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Your AI companion"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
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

        val status = TextView(this).apply {
            text = "● Ready"
            textSize = 12f
            setTextColor(Color.rgb(100, 220, 150))
            setPadding(8, 8, 8, 8)
        }

        statusView = status

        root.addView(status)

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 4, 0, 8)
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
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(16, 12, 16, 12)
            background = roundedBackground(panelColor, 18f)
            maxLines = 4
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

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        voiceButton = makeButton("🎤 Voice")
        sendButton = makeButton("Send ➤")
        speakButton = makeButton("🔊 Speak")

        voiceButton.setOnClickListener {
            requestMicrophone()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        speakButton.setOnClickListener {
            if (lastReply.isNotBlank()) {
                speak(lastReply)
            } else {
                showToast("Sara has not replied yet.")
            }
        }

        buttons.addView(
            voiceButton,
            LinearLayout.LayoutParams(0, 52, 1f).apply {
                rightMargin = 4
            }
        )

        buttons.addView(
            sendButton,
            LinearLayout.LayoutParams(0, 52, 1f).apply {
                leftMargin = 4
                rightMargin = 4
            }
        )

        buttons.addView(
            speakButton,
            LinearLayout.LayoutParams(0, 52, 1f).apply {
                leftMargin = 4
            }
        )

        root.addView(buttons)

        val bottomButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val clearButton = makeButton("Clear chat")

        clearButton.setOnClickListener {
            clearConversation()
        }

        bottomButtons.addView(
            clearButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                42
            ).apply {
                topMargin = 8
            }
        )

        root.addView(bottomButtons)

        setContentView(root)
    }

    private lateinit var statusView: TextView

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(
                Color.rgb(39, 105, 165),
                16f
            )
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun addMessage(
        role: String,
        message: String
    ) {

        val isUser = role == "user"

        val bubble = TextView(this).apply {
            text = (if (isUser) "You\n" else "Sara\n") + message
            textSize = 15f
            setTextColor(textColor)
            setPadding(16, 12, 16, 12)
            background = roundedBackground(
                if (isUser) {
                    Color.rgb(36, 89, 133)
                } else {
                    panelColor
                },
                18f
            )
        }

        val params = LinearLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.82f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.bottomMargin = 12

        params.gravity = if (isUser) {
            Gravity.END
        } else {
            Gravity.START
        }

        chatLayout.addView(bubble, params)

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendMessage() {

        if (isThinking) {
            showToast("Sara is still thinking. Please wait.")
            return
        }

        val message = inputBox.text.toString().trim()

        if (message.isEmpty()) {
            return
        }

        inputBox.setText("")

        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(
            inputBox.windowToken,
            0
        )

        addMessage("user", message)

        conversation.add(
            JSONObject()
                .put("role", "user")
                .put("text", message)
        )

        saveConversation()

        askGemini(message)
    }

    private fun askGemini(message: String) {

        isThinking = true
        sendButton.isEnabled = false
        showStatus("Sara is thinking...")

        lifecycleScope.launch {

            try {

                val history = conversation
                    .takeLast(16)
                    .joinToString("\n") { item ->

                        val role = item.optString("role")
                        val text = item.optString("text")

                        if (role == "user") {
                            "User: $text"
                        } else {
                            "Sara: $text"
                        }
                    }

                val prompt = """
                    You are Sara, a friendly and helpful AI assistant.
                    Respond clearly, naturally, and respectfully.
                    Do not claim to be human.
                    Use the conversation history for context.

                    Conversation:
                    $history

                    Respond to the latest user message:
                    $message
                """.trimIndent()

                val responseText = withContext(Dispatchers.IO) {

                    val model = Firebase.ai(
                        backend = GenerativeBackend.googleAI()
                    ).generativeModel(
                        modelName = "gemini-2.5-flash"
                    )

                    model.generateContent(prompt).text
                        ?: "I couldn't generate a response. Please try again."
                }

                lastReply = responseText

                addMessage("assistant", responseText)

                conversation.add(
                    JSONObject()
                        .put("role", "assistant")
                        .put("text", responseText)
                )

                saveConversation()
                showStatus("Sara is ready to chat.")

            } catch (e: Exception) {

                android.util.Log.e(
                    "SARA",
                    "Gemini request failed",
                    e
                )

                val errorMessage =
                    "I couldn't connect to Gemini. Please check your internet connection, Firebase AI Logic configuration, and App Check setup."

                addMessage("assistant", errorMessage)
                showStatus("Connection problem")

            } finally {

                isThinking = false
                sendButton.isEnabled = true
            }
        }
    }

    private fun requestMicrophone() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            audioPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startListening() {

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
                    "Speak to Sara..."
                )
            }

            speechLauncher.launch(intent)

        } catch (e: Exception) {
            showToast("Speech recognition is unavailable on this device.")
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
            showToast("Speech engine is not ready.")
        }
    }

    private fun showStatus(message: String) {

        if (!::statusView.isInitialized) return

        runOnUiThread {
            statusView.text = "● $message"
        }
    }

    private fun saveConversation() {

        val array = JSONArray()

        conversation.forEach { item ->
            array.put(item)
        }

        preferences.edit()
            .putString("conversation_history", array.toString())
            .apply()
    }

    private fun loadConversation() {

        try {

            val saved = preferences.getString(
                "conversation_history",
                "[]"
            ) ?: "[]"

            val array = JSONArray(saved)

            for (i in 0 until array.length()) {

                val item = array.getJSONObject(i)

                val role = item.optString("role")
                val text = item.optString("text")

                if (role.isNotBlank() && text.isNotBlank()) {

                    conversation.add(item)

                    addMessage(role, text)

                    if (role == "assistant") {
                        lastReply = text
                    }
                }
            }

        } catch (e: Exception) {

            android.util.Log.e(
                "SARA",
                "Could not load saved conversation",
                e
            )
        }
    }

    private fun clearConversation() {

        conversation.clear()
        chatLayout.removeAllViews()

        lastReply = ""

        preferences.edit()
            .remove("conversation_history")
            .apply()

        addMessage(
            "assistant",
            "Chat cleared! 💙 What would you like to talk about?"
        )

        conversation.add(
            JSONObject()
                .put("role", "assistant")
                .put(
                    "text",
                    "Chat cleared! 💙 What would you like to talk about?"
                )
        )

        saveConversation()

        showStatus("New conversation")
    }

    private fun showToast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
