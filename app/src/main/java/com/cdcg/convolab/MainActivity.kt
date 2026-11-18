package com.cdcg.convolab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cdcg.convolab.ui.ConvoLabTheme
import com.cdcg.convolab.typing.TypingPlayground
import com.cdcg.convolab.ui.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConvoLabTheme {
                if (BuildConfig.DEBUG) TypingPlayground() else WelcomeScreen()
            }
        }
    }
}
