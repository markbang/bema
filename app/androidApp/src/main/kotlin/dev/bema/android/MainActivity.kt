package dev.bema.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.bema.shared.data.storage.PlatformKeyValueStore
import dev.bema.shared.data.update.installAppRelease

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        PlatformKeyValueStore.install(applicationContext)
        installAppRelease(applicationContext)
        // Establish edge-to-edge before the first frame; the theme is applied from
        // the composition once the shared appearance preference is known.
        enableBemaEdgeToEdge(dark = null)
        super.onCreate(savedInstanceState)
        setContent { BemaMemosApp() }
    }
}

/**
 * Configures the system bars and edge-to-edge layout. `dark` is the resolved
 * theme; `null` auto-detects, which is only used for the pre-composition default.
 */
internal fun ComponentActivity.enableBemaEdgeToEdge(dark: Boolean?) {
    val transparent = Color.TRANSPARENT
    val style = when (dark) {
        true -> SystemBarStyle.dark(transparent)
        false -> SystemBarStyle.light(transparent, transparent)
        null -> SystemBarStyle.auto(transparent, transparent)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
