package com.roshid.sara01

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = "SARA 0.1\n\nStarting Firebase..."
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(13, 18, 32))
        }

        setContentView(message)

        try {
            FirebaseApp.initializeApp(this)

            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )

            message.text =
                "SARA 0.1\n\nFirebase initialized.\nApp Check debug provider installed."

        } catch (e: Exception) {
            Log.e("SARA", "Firebase initialization failed", e)

            message.text =
                "Firebase initialization failed:\n" +
                "${e.javaClass.simpleName}\n${e.message}"
        }
    }
}
