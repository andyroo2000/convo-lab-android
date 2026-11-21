package com.cdcg.convolab

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cdcg.convolab.ui.ConvoLabTheme
import com.cdcg.convolab.ui.JournalApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConvoLabTheme {
                JournalApp(openTypingLab = if (BuildConfig.DEBUG) ({
                    startActivity(Intent().setClassName(this, "com.cdcg.convolab.TypingLabActivity"))
                }) else null)
            }
        }
    }
}
