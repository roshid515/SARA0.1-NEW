package com.roshid.sara01

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(13, 18, 32))
        }

        val title = TextView(this).apply {
            text = "SARA 0.1"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        val message = TextView(this).apply {
            text = "Diagnostic test successful! 💙"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.CYAN)
        }

        layout.addView(title)
        layout.addView(message)

        setContentView(layout)
    }
}
