package com.roshid.sara01

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
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

        statusText.text = "SARA 0.1\n\nInitializing Firebase..."

        try {
            // Initialize Firebase
            val firebaseApp = FirebaseApp.initializeApp(this)

            if (firebaseApp == null) {
                statusText.text =
                    "Firebase initialization failed.\n\n" +
                    "Check that google-services.json is in the app folder."

                Log.e(TAG, "FirebaseApp.initializeApp returned null")
                return
            }

            Log.i(TAG, "Firebase initialized successfully")

            // Install the App Check debug provider
            val appCheck = FirebaseAppCheck.getInstance(firebaseApp)

            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

            Log.i(TAG, "App Check debug provider installed")

            statusText.text =
                "SARA 0.1\n\n" +
                "Firebase initialized successfully.\n\n" +
                "App Check debug provider installed.\n\n" +
                "Requesting App Check token..."

            // Request an App Check token
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener { tokenResult ->

                    Log.i(TAG, "App Check token request succeeded")

                    // Never print the actual token into logs.
                    // The result confirms that a token was obtained.
                    Log.i(TAG, "Token received successfully")

                    statusText.text =
                        "SARA 0.1\n\n" +
                        "Firebase initialized successfully.\n\n" +
                        "App Check debug provider installed.\n\n" +
                        "App Check token received successfully.\n\n" +
                        "Next: Check Firebase Console."
                }
                .addOnFailureListener { error ->

                    Log.e(
                        TAG,
                        "App Check token request failed",
                        error
                    )

                    statusText.text =
                        "SARA 0.1\n\n" +
                        "Firebase initialized successfully.\n\n" +
                        "App Check debug provider installed.\n\n" +
                        "Token request failed.\n\n" +
                        "Error: ${error.localizedMessage ?: "Unknown error"}"
                }

        } catch (error: Exception) {

            Log.e(TAG, "Firebase setup failed", error)

            statusText.text =
                "SARA 0.1\n\n" +
                "Firebase setup failed.\n\n" +
                "Error: ${error.localizedMessage ?: "Unknown error"}"
        }
    }

    private fun buildScreen() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(18, 18, 30))
        }

        val title = TextView(this).apply {
            text = "SARA 0.1"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(150, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        layout.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        layout.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(layout)
    }
}
