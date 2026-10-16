package com.roshid.sara01

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUI()

        try {
            FirebaseApp.initializeApp(this)

            output.text = """
                SARA 0.1

                Firebase initialized successfully.

                Gemini AI test is ready.

                Type a message below and tap Send to Gemini.
            """.trimIndent()

        } catch (e: Exception) {
            output.text = "Firebase initialization failed:\n\n" +
                (e.message ?: e.javaClass.simpleName)
        }

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(18, 18, 30))
        }

        val title = TextView(this).apply {
            text = "SARA 0.1"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        output = TextView(this).apply {
            text = "Starting SARA..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 24)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        scroll.addView(output)

        input = EditText(this).apply {
            hint = "Ask Sara something..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(false)
            minLines = 2
            maxLines = 5
        }

        sendButton = Button(this).apply {
            text = "Send to Gemini"
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            sendButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()

        if (prompt.isEmpty()) {
            output.text = "Please type a message first."
            return
        }

        sendButton.isEnabled = false
        output.text = "Connecting to Gemini AI...\nPlease wait."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val ai = FirebaseAI.getInstance(
                    backend = GenerativeBackend.googleAI()
                )

                val model = ai.generativeModel(
                    modelName = "gemini-2.5-flash"
                )

                val response = model.generateContent(prompt)

                output.text = response.text
                    ?: "Gemini returned an empty response."

            } catch (e: Exception) {
                output.text = "Gemini request failed:\n\n" +
                    (e.message ?: e.javaClass.simpleName)

            } finally {
                sendButton.isEnabled = true
            }
        }
    }
}
