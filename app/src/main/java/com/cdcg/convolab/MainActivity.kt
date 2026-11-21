package com.cdcg.convolab

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.cdcg.convolab.journal.appearance.AppearanceWorkspace
import com.cdcg.convolab.ui.ConvoLabTheme
import com.cdcg.convolab.ui.JournalApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK))
        setContent {
            ConvoLabTheme {
                AppearanceWorkspace { JournalApp(openTypingLab = if (BuildConfig.DEBUG) ({
                    startActivity(Intent().setClassName(this, "com.cdcg.convolab.TypingLabActivity"))
                }) else null) }
            }
        }
    }
}
