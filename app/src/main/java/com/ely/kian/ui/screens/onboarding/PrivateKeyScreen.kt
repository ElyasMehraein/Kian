package com.ely.kian.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.theme.Ink
import com.ely.kian.ui.theme.Line
import com.ely.kian.ui.theme.Panel

@Composable
fun PrivateKeyScreen(
    privateKey: String,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
    ) {
        Text(
            text = "Save Your Key",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This is your private key. Never share it with anyone. If you lose it, you cannot recover your identity.",
            fontSize = 16.sp,
            color = Ink.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                text = privateKey,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Ink,
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
