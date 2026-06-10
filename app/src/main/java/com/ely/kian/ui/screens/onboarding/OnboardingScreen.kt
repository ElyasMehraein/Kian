package com.ely.kian.ui.screens.onboarding

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.ButtonType
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.theme.KianTheme

@Composable
fun OnboardingScreen(
    onSuccess: () -> Unit,
    viewModel: OnboardingViewModel
) {
    val kianColors = KianTheme.colors
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.Success -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                is OnboardingEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kianColors.canvas)
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Create your Kian wallet",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = kianColors.ink
        )
        
        Text(
            text = "Generate a wallet, back up the mnemonic, or restore from an existing one.",
            fontSize = 16.sp,
            color = kianColors.muted,
            lineHeight = 24.sp
        )

        // Saved Keypair Found Card
        viewModel.savedKey?.let { key ->
            OnboardingCard {
                Text(
                    text = "Saved keypair found",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.muted
                )
                Text(
                    text = "Your private key is still stored on this device, so you can log back in without re-entering anything.",
                    fontSize = 14.sp,
                    color = kianColors.muted,
                    lineHeight = 20.sp
                )
                KianButton(
                    text = if (viewModel.isSaving) "Saving..." else "Log back in",
                    onClick = { viewModel.handleLogBackIn() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isSaving
                )
            }
        }

        // Generate Keypair Button
        KianButton(
            text = "Generate keypair",
            onClick = { viewModel.handleGenerate() },
            modifier = Modifier.fillMaxWidth(),
            type = ButtonType.Primary
        )

        // Mnemonic Card
        OnboardingCard {
            Text(
                text = "Mnemonic",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = kianColors.muted
            )
            val mnemonic = viewModel.generatedKeys?.mnemonic ?: "Tap generate to create your wallet keys."
            Text(
                text = mnemonic,
                fontSize = 16.sp,
                color = kianColors.ink,
                lineHeight = 24.sp,
                modifier = Modifier.clickable(enabled = viewModel.generatedKeys != null) {
                    clipboardManager.setText(AnnotatedString(mnemonic))
                    Toast.makeText(context, "Mnemonic copied", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Recovery Card
        OnboardingCard {
            Text(
                text = "Recovery",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = kianColors.muted
            )
            Text(
                text = "Paste a previously backed up mnemonic to restore this wallet.",
                fontSize = 14.sp,
                color = kianColors.muted,
                lineHeight = 20.sp
            )
            KianInput(
                value = viewModel.mnemonicInput,
                onValueChange = { viewModel.mnemonicInput = it },
                placeholder = "Enter mnemonic phrase",
                singleLine = false,
                modifier = Modifier.heightIn(min = 88.dp)
            )
            KianButton(
                text = if (viewModel.isSaving) "Saving..." else "Restore wallet",
                onClick = { viewModel.handleRestore() },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.mnemonicInput.isNotBlank() && !viewModel.isSaving
            )
        }

        // Save Keys Button
        KianButton(
            text = if (viewModel.isSaving) "Saving..." else "Save keys",
            onClick = { viewModel.saveGeneratedKeys() },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.generatedKeys != null && !viewModel.isSaving
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun OnboardingCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val kianColors = KianTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(kianColors.panel)
            .border(1.dp, kianColors.line, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}
