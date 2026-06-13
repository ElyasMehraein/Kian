package com.ely.kian.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ely.kian.KianApp
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.theme.KianTheme
import androidx.compose.ui.Alignment

@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = viewModel(
        factory = ProfileEditViewModel.provideFactory(
            (LocalContext.current.applicationContext as KianApp).container.keyDao,
            (LocalContext.current.applicationContext as KianApp).container.userProfileDao,
            (LocalContext.current.applicationContext as KianApp).container.nostrSyncManager,
            (LocalContext.current.applicationContext as KianApp).container.secureStorage
        )
    )
) {
    val kianColors = KianTheme.colors
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Edit profile",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = kianColors.ink
        )
        Text(
            text = if (viewModel.pubkey != null) "Update your public profile metadata." else "Create keys first.",
            fontSize = 15.sp,
            color = kianColors.ink.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )

        InitialAvatar(
            name = viewModel.displayName,
            pictureUrl = viewModel.picture,
            size = 80.dp,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        KianInput(
            value = viewModel.displayName,
            onValueChange = { viewModel.displayName = it },
            placeholder = "Display name",
            modifier = Modifier.padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.about,
            onValueChange = { viewModel.about = it },
            placeholder = "Bio",
            singleLine = false,
            modifier = Modifier
                .heightIn(min = 120.dp)
                .padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.picture,
            onValueChange = { viewModel.picture = it },
            placeholder = "Avatar URL",
            modifier = Modifier.padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.nip05,
            onValueChange = { viewModel.nip05 = it },
            placeholder = "NIP-05",
            modifier = Modifier.padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.geohash,
            onValueChange = { viewModel.geohash = it },
            placeholder = "Geohash",
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KianButton(
                text = "Cancel",
                onClick = onBack,
                type = com.ely.kian.ui.components.ButtonType.Secondary,
                modifier = Modifier.weight(1f)
            )
            KianButton(
                text = if (viewModel.isSaving) "Saving..." else "Save profile",
                onClick = { viewModel.saveProfile(onBack) },
                enabled = viewModel.pubkey != null && !viewModel.isSaving,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
