package com.roshid.sara01

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    companion object {
        private const val TAG = "SARA_APP_CHECK"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildScreen()
        showStatus("SARA 0.1\n\nInitializing Firebase...")

        try {
            // Initialize Firebase.
            val firebaseApp = FirebaseApp.initializeApp(this)

            if (firebaseApp == null) {
                showStatus(
                    "Firebase initialization failed.\n\n" +
                        "Check that google-services.json exists in the app folder."
                )
                Log.e(TAG, "Firebase initialization returned null")
                return
            }

            Log.i(TAG, "Firebase initialized successfully")

            // Get the App Check instance.
            val appCheck = FirebaseAppCheck.getInstance(firebaseApp)

            // Install the debug provider.
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

            Log.i(TAG, "App Check debug provider factory installed")

            showStatus(
                "SARA 0.1\n\n" +
                    "Firebase initialized successfully.\n\n" +
                    "App Check debug provider installed.\n\n" +
                    "Requesting token..."
            )

            // Request an App Check token.
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener { _ ->

                    Log.i(TAG, "App Check token request succeeded")

                    // Do not print the token or secret into logs.
                    showStatus(
                        "SARA 0.1\n\n" +
                            "Firebase initialized successfully.\n\n" +
                            "Debug provider installed.\n\n" +
                            "App Check token received successfully.\n\n" +
                            "Next: Verify Firebase Console."
                    )
                }
                .addOnFailureListener { error ->

                    Log.e(TAG, "App Check token request failed", error)

                    showStatus(
                        "SARA 0.1\n\n" +
                            "Firebase initialized successfully.\n\n" +
                            "Debug provider installed.\n\n" +
                            "TOKEN REQUEST FAILED\n\n" +
                            "Error:\n" +
                            (error.localizedMessage
                                ?: error.javaClass.simpleName) +
                            "\n\nCheck Firebase App Check configuration."
                    )
                }

        } catch (error: Exception) {

            Log.e(TAG, "Firebase/App Check setup failed", error)

            showStatus(
                "SARA 0.1\n\n" +
                    "SETUP FAILED\n\n" +
                    "Error:\n" +
                    (error.localizedMessage
                        ?: error.javaClass.simpleName)
            )
        }
    }

    private fun buildScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(18, 18, 30))
        }

        val title = TextView(this).apply {
            text = "SARA 0.1"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        statusText = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(150, 220, 255))
            setPadding(0, 28, 0, 0)
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        // Add the status text using the ScrollView's default layout parameters.
        scrollView.addView(statusText)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun showStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }
}
