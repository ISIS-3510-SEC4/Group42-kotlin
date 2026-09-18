package com.example.espoti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.espoti.navigation.EspotiNavHost
import com.example.espoti.ui.theme.EspotiTheme

// ============================================================================
// APP ENTRY POINT
// ----------------------------------------------------------------------------
// This just applies the app theme and hands off to EspotiNavHost, which
// decides which of the 4 screens (Bienvenida/Login/Registro/Inicio) is shown
// and how you move between them. There is normally nothing to edit here -
// go to:
//   - navigation/EspotiNavigation.kt to change routes/navigation logic
//   - ui/screens/*.kt to change what's ON each screen
//   - ui/theme/*.kt to change colors/fonts app-wide
// ============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            EspotiTheme {
                EspotiNavHost()
            }
        }
    }

    // Immersive mode reverts when the window regains focus
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
