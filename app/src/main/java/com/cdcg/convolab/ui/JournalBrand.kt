package com.cdcg.convolab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcg.convolab.R

object JournalBrand {
    val Green = Color(0xFF60B048)
    val Yellow = Color(0xFFFFF29C)
    val Blue = Color(0xFF356EDC)
    val Ink = Color(0xFF11335C)
    val Paper = Color(0xFFFFFDF5)
    val Anton = FontFamily(Font(R.font.anton))
    val Heading = FontFamily(Font(R.font.nunito))
    val Body = FontFamily(Font(R.font.inter))
    val Serif = FontFamily(Font(R.font.crimson_pro))
    val Handwriting = FontFamily(Font(R.font.klee_one))
}

@Composable
fun JournalCover(modifier: Modifier = Modifier) {
    Column(modifier.background(JournalBrand.Green).padding(horizontal = 8.dp, vertical = 24.dp)) {
        Text("CONVO-LAB", fontFamily = JournalBrand.Anton, fontSize = 40.sp, color = JournalBrand.Yellow)
        Text("コンボ・ラボ", fontFamily = FontFamily(Font(R.font.noto_sans_jp)),
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("JOURNAL", fontFamily = FontFamily(Font(R.font.barlow_condensed)), fontSize = 22.sp,
            letterSpacing = 5.sp, color = Color.White, modifier = Modifier.padding(top = 22.dp))
        Text("ジャーナル", color = Color.White, fontSize = 15.sp)
    }
}
