package com.jpcottin.composespaceinvaders

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.composespaceinvaders.glasses.GlassesSession
import com.jpcottin.composespaceinvaders.theme.SpaceInvadersTheme
import com.jpcottin.composespaceinvaders.ui.GameScreen
import com.jpcottin.composespaceinvaders.ui.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The game is dark whatever the system setting; tell the system to
        // render dark scrims so status/nav bar icons are light and legible.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        GlassesSession.startMonitoring(applicationContext)
        setContent {
            val viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(applicationContext))
            SpaceInvadersTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GameScreen(viewModel)
                }
            }
        }
    }
}
