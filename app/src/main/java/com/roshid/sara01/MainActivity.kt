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

            output.text = """
                SARA 0.1

                Firebase initialized successfully.

                App Check Debug Provider installed.

                Tap Check App Check Token first.
            """.trimIndent()

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

        checkButton = Button(this).apply {
            text = "Check App Check Token"
        }

        sendButton = Button(this).apply {
            text = "Send to Gemini"
        }

        root.addView(title)

        root.addView(
            scroll,
            Linear
