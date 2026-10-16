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
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var checkButton: Button
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUI()

        try {
            FirebaseApp.initializeApp(this)

            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )

            output.text =
                "SARA 0.1\n\n" +
                "Firebase initialized.\n" +
                "App Check Debug Provider installed.\n\n" +
                "Tap Check App Check Token first."

        } catch (e: Exception) {
            output.text = "Initialization failed:\n\n" +
                (e.message ?: e.javaClass.simpleName)
        }

        checkButton.setOnClickListener {
            checkAppCheckToken()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 24, 24, 24)
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Color.rgb(18, 18, 30))

        val title = TextView(this)
        title.text = "SARA 0.1"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        output = TextView(this)
        output.text = "Starting SARA..."
        output.textSize = 16f
        output.setTextColor(Color.WHITE)
        output.setPadding(0, 24, 0, 24)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(output)

        input = EditText(this)
        input.hint = "Ask Sara something..."
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.LTGRAY)
        input.setSingleLine(false)
        input.minLines = 2
        input.maxLines = 5

        checkButton = Button(this)
        checkButton.text = "Check App Check Token"

        sendButton = Button(this)
        sendButton.text = "Send to Gemini"

        root.addView(title)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(input)
        root.addView(checkButton)
        root.addView(sendButton)

        setContentView(root)
    }

    private fun checkAppCheckToken() {
        checkButton.isEnabled = false
        output.text = "Requesting App Check token...\nPlease wait."

        FirebaseAppCheck.getInstance()
            .getAppCheckToken(false)
            .addOnSuccessListener { tokenResult ->
                output.text =
                    "APP CHECK TOKEN SUCCESS\n\n" +
                    "Token retrieved successfully.\n\n" +
                    "Token length: ${tokenResult.token.length}\n\n" +
                    "The secret token is not displayed.\n\n" +
                    "Now test Gemini."
                checkButton.isEnabled = true
            }
            .addOnFailureListener { error ->
                output.text =
                    "APP CHECK TOKEN REQUEST FAILED\n\n" +
                    (error.message ?: error.javaClass.simpleName)
                checkButton.isEnabled = true
            }
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
                output.text =
                    "GEMINI REQUEST FAILED\n\n" +
                    (e.message ?: e.javaClass.simpleName)

            } finally {
                sendButton.isEnabled = true
            }
        }
    }
}
