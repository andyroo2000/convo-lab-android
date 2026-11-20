package com.cdcg.convolab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cdcg.convolab.typing.TypingPlayground
import com.cdcg.convolab.ui.ConvoLabTheme

class TypingLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ConvoLabTheme { TypingPlayground() } }
    }
}
