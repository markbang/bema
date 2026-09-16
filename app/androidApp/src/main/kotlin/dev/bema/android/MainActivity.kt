package dev.bema.android

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import dev.bema.shared.Greeter

/**
 * Android entry point. Per the AGP 9 module split this module only holds the
 * Android app shell; all shared logic comes from `:app:sharedLogic`.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = TextView(this).apply {
            text = Greeter().greet("Android")
            textSize = 20f
            setPadding(64, 200, 64, 64)
        }
        setContentView(label)
    }
}
