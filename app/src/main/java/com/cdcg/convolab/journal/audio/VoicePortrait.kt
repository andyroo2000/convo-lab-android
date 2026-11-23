package com.cdcg.convolab.journal.audio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.cdcg.convolab.R

/** Exact bundled server artwork; unknown future voices retain their accessible name. */
@Composable
fun VoicePortrait(voice: NarrationVoice, modifier: Modifier = Modifier) {
    val image = when (voice.avatarUrl?.substringAfterLast('/')) {
        "male-01-v2" -> R.drawable.voice_male_01_v2
        "male-02-v2" -> R.drawable.voice_male_02_v2
        "male-03-v2" -> R.drawable.voice_male_03_v2
        "female-01-v2" -> R.drawable.voice_female_01_v2
        "female-02-v2" -> R.drawable.voice_female_02_v2
        "female-03-v2" -> R.drawable.voice_female_03_v2
        else -> null
    }
    if (image != null) Image(painterResource(image), null, modifier.background(Color.White, CircleShape).clip(CircleShape))
}
