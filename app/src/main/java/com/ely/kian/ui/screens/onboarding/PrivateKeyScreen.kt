package com.ely.kian.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.theme.KianTheme

@Composable
fun PrivateKeyScreen(
    privateKey: String,
    onContinue: () -> Unit
) {
    val kianColors = KianTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kianColors.canvas)
            .padding(24.dp)
            .statusBarsPadding()
    ) {
        Text(
            text = "Save Your Key",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = kianColors.ink
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This is your private key. Never share it with anyone. If you lose it, you cannot recover your identity.",
            fontSize = 16.sp,
            color = kianColors.ink.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(kianColors.panel)
                .padding(24.dp)
        ) {
            Text(
                text = privateKey,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = kianColors.ink,
                lineHeight = 20.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        KianButton(
            text = "I've Saved It",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
