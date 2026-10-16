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
                "Firebase initialized successfully.\n\n" +
                "App Check Debug Provider installed.\n\n" +
                "Tap Check App Check Token to test it."

        } catch (e: Exception) {
            output.text =
                "INITIALIZATION FAILED\n\n" +
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
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
            addView(output)
        }

        input = EditText(this).apply {
            hint = "Ask Sara something..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            minLines = 2
            maxLines = 5
        }

        checkButton = Button(this).apply {
            text = "Check App Check Token"
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
                    "APP CHECK TOKEN REQUEST SUCCEEDED\n\n" +
                    "The SDK returned a token.\n\n" +
                    "Token length: ${tokenResult.token.length}\n\n" +
                    "The token has not been displayed.\n\n" +
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
