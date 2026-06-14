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
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
import com.ely.kian.KianApp
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.components.LocationPicker
import com.ely.kian.ui.components.ScreenHeader
import com.ely.kian.ui.theme.KianTheme
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear

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
            title = stringResource(R.string.edit_profile),
            subtitle = if (viewModel.pubkey != null) stringResource(R.string.edit_profile_desc) else stringResource(R.string.create_keys_first),
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
                placeholder = "alice",
                label = stringResource(R.string.username)
            )

            KianInput(
                value = viewModel.displayName,
                onValueChange = { viewModel.displayName = it },
                placeholder = stringResource(R.string.display_name),
                label = stringResource(R.string.display_name)
            )

            KianInput(
                value = viewModel.about,
                onValueChange = { viewModel.about = it },
                placeholder = stringResource(R.string.about_me),
                label = stringResource(R.string.bio),
                singleLine = false,
                modifier = Modifier.heightIn(min = 100.dp)
            )

            KianInput(
                value = viewModel.picture,
                onValueChange = { viewModel.picture = it },
                placeholder = "https://...",
                label = stringResource(R.string.avatar_url)
            )

            KianInput(
                value = viewModel.banner,
                onValueChange = { viewModel.banner = it },
                placeholder = "https://...",
                label = stringResource(R.string.banner_url)
            )

            KianInput(
                value = viewModel.website,
                onValueChange = { viewModel.website = it },
                placeholder = "https://example.com",
                label = stringResource(R.string.website_label)
            )

            KianInput(
                value = viewModel.nip05,
                onValueChange = { viewModel.nip05 = it },
                placeholder = "user@domain.com",
                label = stringResource(R.string.nip05_label)
            )

            KianInput(
                value = viewModel.location,
                onValueChange = { viewModel.location = it },
                placeholder = "City, Country",
                label = stringResource(R.string.location)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.map_location),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (viewModel.geohash.isNotBlank()) {
                    TextButton(
                        onClick = {
                            viewModel.geohash = ""
                            viewModel.latitude = null
                            viewModel.longitude = null
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.clear), fontSize = 12.sp)
                    }
                }
            }

            LocationPicker(
                initialLat = viewModel.latitude,
                initialLon = viewModel.longitude,
                onLocationSelected = { lat, lon ->
                    viewModel.updateLocation(lat, lon)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )

            KianInput(
                value = viewModel.geohash,
                onValueChange = { viewModel.geohash = it },
                placeholder = "Geohash string",
                label = stringResource(R.string.geohash_label)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KianButton(
                    text = stringResource(R.string.cancel),
                    onClick = onBack,
                    type = com.ely.kian.ui.components.ButtonType.Secondary,
                    modifier = Modifier.weight(1f)
                )
                KianButton(
                    text = if (viewModel.isSaving) stringResource(R.string.saving) else stringResource(R.string.save_profile),
                    onClick = { viewModel.saveProfile(onBack) },
                    enabled = viewModel.pubkey != null && !viewModel.isSaving,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
