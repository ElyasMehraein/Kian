package com.ely.kian.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.theme.KianTheme

@Composable
fun PrivateKeyScreen(
    viewModel: PrivateKeyViewModel,
    onContinue: () -> Unit
) {
    val kianColors = KianTheme.colors
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kianColors.canvas)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Private Key Management",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = kianColors.ink
        )
        Text(
            text = "Reveal sensitive values only when needed and keep them off shared screens.",
            fontSize = 15.sp,
            color = kianColors.muted,
            lineHeight = 24.sp
        )

        // Public Key Field
        KeyFieldCard(label = "Public key", value = viewModel.pubkey)

        // Private Key Field
        SecretField(label = "Private key", value = viewModel.privateKey)

        // Mnemonic Field
        SecretField(label = "Mnemonic", value = viewModel.mnemonic)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        com.ely.kian.ui.components.KianButton(
            text = "I've Saved It",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun KeyFieldCard(label: String, value: String?) {
    val kianColors = KianTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(kianColors.panel)
            .border(1.dp, kianColors.line, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = kianColors.ink
        )
        SelectionContainer {
            Text(
                text = value ?: "Unavailable",
                fontSize = 14.sp,
                color = kianColors.ink,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun SecretField(label: String, value: String?) {
    val kianColors = KianTheme.colors
    var isVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(kianColors.panel)
            .border(1.dp, kianColors.line, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = kianColors.ink
            )
            
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(kianColors.ink)
                    .clickable { isVisible = !isVisible }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isVisible) "Hide" else "Reveal",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.canvas
                )
            }
        }
        
        SelectionContainer {
            Text(
                text = if (isVisible) (value ?: "Unavailable") else "Hidden for safety",
                fontSize = 14.sp,
                color = if (isVisible) kianColors.ink else kianColors.muted,
                lineHeight = 24.sp
            )
        }
    }
}
