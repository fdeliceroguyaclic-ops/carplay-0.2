package fr.polo.cockpit

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fr.polo.cockpit.ui.CockpitRoot
import fr.polo.cockpit.ui.CockpitViewModel
import fr.polo.cockpit.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: CockpitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            // En mode écran d'accueil, « Retour » ramène à l'accueil au lieu de quitter.
            BackHandler { vm.go(Screen.HOME) }
            CockpitRoot(vm)
        }
    }
}
