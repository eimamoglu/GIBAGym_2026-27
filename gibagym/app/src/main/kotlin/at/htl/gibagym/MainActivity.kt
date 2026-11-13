package at.htl.gibagym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import at.htl.gibagym.core.ui.theme.GibaGymTheme
import at.htl.gibagym.navigation.GibaGymApp

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as GibaGymApplication).container

        setContent {
            GibaGymTheme {
                // Die Fensterklasse wird hier einmal bestimmt und nach unten
                // gereicht. Sie ändert sich beim Drehen und beim Aufklappen
                // eines Foldables — beides muss ohne Neustart funktionieren.
                GibaGymApp(
                    container = container,
                    windowSizeClass = calculateWindowSizeClass(this),
                )
            }
        }
    }
}
