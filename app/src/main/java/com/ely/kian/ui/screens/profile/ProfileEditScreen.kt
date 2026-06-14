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
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(
            title = "Edit profile",
            subtitle = if (viewModel.pubkey != null) "Update your Nostr profile metadata." else "Create keys first.",
            onBack = onBack
        )

        InitialAvatar(
            name = viewModel.displayName.ifBlank { viewModel.name },
            pictureUrl = viewModel.picture,
            size = 80.dp,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KianInput(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                placeholder = "Username (e.g. alice)",
                label = "Username"
            )

            KianInput(
                value = viewModel.displayName,
                onValueChange = { viewModel.displayName = it },
                placeholder = "Display Name",
                label = "Display Name"
            )

            KianInput(
                value = viewModel.about,
                onValueChange = { viewModel.about = it },
                placeholder = "About Me",
                label = "Bio",
                singleLine = false,
                modifier = Modifier.heightIn(min = 100.dp)
            )

            KianInput(
                value = viewModel.picture,
                onValueChange = { viewModel.picture = it },
                placeholder = "Avatar URL",
                label = "Picture URL"
            )

            KianInput(
                value = viewModel.banner,
                onValueChange = { viewModel.banner = it },
                placeholder = "Banner URL",
                label = "Banner URL"
            )

            KianInput(
                value = viewModel.website,
                onValueChange = { viewModel.website = it },
                placeholder = "https://example.com",
                label = "Website"
            )

            KianInput(
                value = viewModel.nip05,
                onValueChange = { viewModel.nip05 = it },
                placeholder = "user@domain.com",
                label = "Nostr Verification (NIP-05)"
            )

            KianInput(
                value = viewModel.location,
                onValueChange = { viewModel.location = it },
                placeholder = "City, Country",
                label = "Location"
            )

            KianInput(
                value = viewModel.geohash,
                onValueChange = { viewModel.geohash = it },
                placeholder = "Geohash string",
                label = "Geohash"
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    text = if (viewModel.isSaving) "Saving..." else "Save Profile",
                    onClick = { viewModel.saveProfile(onBack) },
                    enabled = viewModel.pubkey != null && !viewModel.isSaving,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
