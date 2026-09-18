package dev.bema.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.bema.shared.data.storage.PlatformKeyValueStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        PlatformKeyValueStore.install(applicationContext)
        enableBemaEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { BemaMemosApp() }
    }
}

internal fun ComponentActivity.enableBemaEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
}
