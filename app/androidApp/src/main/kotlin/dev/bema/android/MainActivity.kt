package dev.bema.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.bema.shared.data.storage.PlatformKeyValueStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        PlatformKeyValueStore.install(applicationContext)
        super.onCreate(savedInstanceState)
        setContent { BemaMemosApp() }
    }
}
