package ru.terentiy.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.terentiy.mobile.ui.navigation.TerentiyNavHost
import ru.terentiy.mobile.ui.theme.TerentiyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TerentiyTheme {
                TerentiyNavHost()
            }
        }
    }
}
