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
import com.ely.kian.ui.components.ScreenHeader
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
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(
            title = "Edit profile",
            subtitle = if (viewModel.pubkey != null) "Update your public profile metadata." else "Create keys first.",
            onBack = onBack
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
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.about,
            onValueChange = { viewModel.about = it },
            placeholder = "Bio",
            singleLine = false,
            modifier = Modifier
                .heightIn(min = 120.dp)
                .padding(horizontal = 20.dp).padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.picture,
            onValueChange = { viewModel.picture = it },
            placeholder = "Avatar URL",
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.nip05,
            onValueChange = { viewModel.nip05 = it },
            placeholder = "NIP-05",
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
        )

        KianInput(
            value = viewModel.geohash,
            onValueChange = { viewModel.geohash = it },
            placeholder = "Geohash",
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
