package com.ely.kian.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.ButtonType
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.theme.Ink

@Composable
fun OnboardingScreen(
    onCreateNew: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Kian",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = Ink
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your private, offline-first marketplace and chat.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = Ink.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(48.dp))
        KianButton(
            text = "Create New Identity",
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        KianButton(
            text = "Import Existing Key",
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            type = ButtonType.Secondary
        )
    }
}
