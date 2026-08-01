package com.ely.kian.ui.screens.onboarding

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.ui.components.ButtonType
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.components.LanguageSelector
import com.ely.kian.ui.theme.KianTheme

@Composable
fun OnboardingScreen(
    onSuccess: () -> Unit,
    viewModel: OnboardingViewModel,
    currentLanguage: String = "en",
    onLanguageChange: (String) -> Unit = {}
) {
    val kianColors = KianTheme.colors
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            LanguageSelector(
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.create_wallet),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = kianColors.ink
        )

        // 1. Private Key Card (Login with Private Key)
        OnboardingCard {
            Text(
                text = stringResource(R.string.login_private_key),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = kianColors.muted
            )
            Text(
                text = stringResource(R.string.private_key_login_desc),
                fontSize = 14.sp,
                color = kianColors.muted,
                lineHeight = 20.sp
            )
            KianInput(
                value = viewModel.privateKeyInput,
                onValueChange = { viewModel.privateKeyInput = it },
                placeholder = "nsec... or hex",
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && viewModel.privateKeyInput.isNotEmpty()) {
                            viewModel.clearStoredPrivateKey()
                        }
                    }
            )
            KianButton(
                text = if (viewModel.isSaving) stringResource(R.string.saving) else stringResource(R.string.login_private_key),
                onClick = { viewModel.handleRestoreFromPrivateKey() },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.privateKeyInput.isNotBlank() && !viewModel.isSaving
            )
        }

        // 2. Recovery Card (Login with Recovery Words)
        OnboardingCard {
            Text(
                text = stringResource(R.string.recovery),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = kianColors.muted
            )
            Text(
                text = stringResource(R.string.recovery_desc),
                fontSize = 14.sp,
                color = kianColors.muted,
                lineHeight = 20.sp
            )
            KianInput(
                value = viewModel.mnemonicInput,
                onValueChange = { viewModel.mnemonicInput = it },
                placeholder = stringResource(R.string.enter_mnemonic),
                singleLine = false,
                modifier = Modifier.heightIn(min = 88.dp)
            )
            KianButton(
                text = if (viewModel.isSaving) stringResource(R.string.saving) else stringResource(R.string.restore_wallet),
                onClick = { viewModel.handleRestore() },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.mnemonicInput.isNotBlank() && !viewModel.isSaving
            )
        }

        // 3. Login with New Account Button
        KianButton(
            text = if (viewModel.isSaving) stringResource(R.string.saving) else stringResource(R.string.generate_keypair),
            onClick = { viewModel.handleGenerate() },
            modifier = Modifier.fillMaxWidth(),
            type = ButtonType.Primary,
            enabled = !viewModel.isSaving
        )

        // Saved Keypair Found Card (Legacy, kept just in case but PK input handles most of it now)
        viewModel.savedKey?.let { _ ->
            OnboardingCard {
                Text(
                    text = stringResource(R.string.saved_keypair_found),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.muted
                )
                Text(
                    text = stringResource(R.string.saved_keypair_desc),
                    fontSize = 14.sp,
                    color = kianColors.muted,
                    lineHeight = 20.sp
                )
                KianButton(
                    text = if (viewModel.isSaving) stringResource(R.string.saving) else stringResource(R.string.log_back_in),
                    onClick = { viewModel.handleLogBackIn() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isSaving
                )
            }
        }

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
