package com.roshid.sara01

import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.appCheck
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
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private val requestAudio = 42
    private val requestSpeech = 43
    private val prefs by lazy { getSharedPreferences("sara_memory", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    com.google.firebase.FirebaseApp.initializeApp(this)

    com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(
            com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
        )

    tts = TextToSpeech(this, this)

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
            text = "Your personal AI assistant • Prototype 0.1"
            textSize = 14f
            setTextColor(0xFFB9C4E8.toInt())
            gravity = Gravity.CENTER
        }
        transcript = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFF4F6FF.toInt())
            text = "Sara: Hi Roshid! I'm SARA0.1.\n\nThis starter app has local notes and voice input. Online AI and wake-word support will be added after setup."
            setPadding(12, 16, 12, 16)
        }
        val scroll = ScrollView(this).apply { addView(transcript) }
        input = EditText(this).apply {
            hint = "Type a message or command"
            setHintTextColor(0xFF9BA8CC.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setSingleLine(false)
        }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { handleCommand(input.text.toString().trim()); input.setText("") }
        }
        val speak = Button(this).apply {
            text = "🎙 Speak"
            setOnClickListener { startSpeechInput() }
        }
        val remember = Button(this).apply {
            text = "Remember this"
            setOnClickListener {
                val note = input.text.toString().trim()
                if (note.isNotEmpty()) {
                    val old = prefs.getString("notes", "") ?: ""
                    prefs.edit().putString("notes", (old + "\n• " + note).trim()).apply()
                    append("Sara", "I'll save that note on this phone.")
                    input.setText("")
                }
            }
        }
        val showMemory = Button(this).apply {
            text = "Show memory"
            setOnClickListener {
                val notes = prefs.getString("notes", "").orEmpty()
                append("Sara", if (notes.isBlank()) "Your local memory is empty." else "Things saved on this phone:\n$notes")
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(input)
        root.addView(send)
        root.addView(speak)
        root.addView(remember)
        root.addView(showMemory)
        setContentView(root)
    }

    private fun handleCommand(command: String) {
        if (command.isBlank()) return
        append("You", command)
        val lower = command.lowercase(Locale.ROOT)
        val response = when {
            lower == "help" -> "Try typing a message, tap Speak, or use Remember this to save a note locally."
            lower.startsWith("open ") -> "Phone-control actions will be added with explicit Android permissions in a later version."
            lower.startsWith("remember ") -> {
                val note = command.substringAfter("remember ").trim()
                val old = prefs.getString("notes", "") ?: ""
                prefs.edit().putString("notes", (old + "\n• " + note).trim()).apply()
                "Saved that note locally on this phone."
            }
            else -> "I'm the SARA0.1 starter prototype. Online AI is not connected yet, so I can't answer this intelligently until we configure an AI provider."
        }
        append("Sara", response)
        tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "sara_reply")
    }

    private fun append(who: String, message: String) {
        transcript.append("\n\n$who: $message")
    }

    private fun startSpeechInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Sara")
        }
        try {
            startActivityForResult(intent, requestSpeech)
        } catch (_: Exception) {
            append("Sara", "Speech recognition isn't available on this device right now.")
        }
    }

    @Deprecated("Deprecated by Android, retained here for broad compatibility in this starter.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestSpeech && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!result.isNullOrBlank()) handleCommand(result)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
