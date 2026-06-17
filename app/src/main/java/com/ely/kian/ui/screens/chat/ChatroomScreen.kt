package com.ely.kian.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.ui.components.util.setText
import com.ely.kian.crypto.KianKeys
import com.ely.kian.ui.screens.chat.components.*
import com.ely.kian.ui.theme.KianTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatroomScreen(
    contactPubkey: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val messages by viewModel.getMessages(contactPubkey).collectAsState()
    var textState by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    
    var contactName by remember { mutableStateOf(contactPubkey.take(8) + "...") }
    
    var showVoucherPicker by remember { mutableStateOf(false) }
    val myBalances by viewModel.getBalances().collectAsState()
    
    val kianColors = KianTheme.colors
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val contactNpub = remember(contactPubkey) {
        try {
            KianKeys.toNpub(KianKeys.hexToBytes(contactPubkey))
        } catch (e: Exception) {
            contactPubkey
        }
    }

    LaunchedEffect(contactPubkey) {
        val profile = viewModel.getProfile(contactPubkey)
        if (profile != null) {
            contactName = profile.displayName ?: profile.name ?: contactName
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            viewModel.markAsRead(contactPubkey)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column(
                        modifier = Modifier.clickable { onProfileClick(contactPubkey) }
                    ) {
                        Text(contactName, color = kianColors.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(contactNpub.take(16) + "...", color = kianColors.muted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = kianColors.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kianColors.canvas)
            )
        },
        bottomBar = {
            Column {
                if (replyingTo != null) {
                    ReplyPreview(replyingTo!!, kianColors) { replyingTo = null }
                }
                ChatInput(
                    text = textState,
                    onTextChange = { textState = it },
                    onSend = {
                        if (textState.isNotBlank()) {
                            viewModel.sendMessage(contactPubkey, textState, replyingTo?.id)
                            textState = ""
                            replyingTo = null
                        }
                    },
                    onActionClick = { showVoucherPicker = true },
                    colors = kianColors
                )
            }
        },
        containerColor = kianColors.canvas
    ) { padding ->
        if (showVoucherPicker) {
            ModalBottomSheet(
                onDismissRequest = { showVoucherPicker = false },
                sheetState = sheetState,
                containerColor = kianColors.canvas
            ) {
                TokenPickerContent(
                    balances = myBalances,
                    utxos = viewModel.voucherRepository.getUtxos().collectAsState(initial = emptyList()).value,
                    colors = kianColors,
                    onTokenSelected = { utxoId, amount ->
                        viewModel.sendToken(contactPubkey, utxoId, amount)
                        showVoucherPicker = false
                    }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                var showMenu by remember { mutableStateOf(false) }
                val clipboard = androidx.compose.ui.platform.LocalClipboard.current

                ChatBubbleLayout(
                    isMine = message.isMine,
                    colors = kianColors,
                    onLongClick = { showMenu = true },
                    onDoubleClick = { viewModel.toggleReaction(message.id, contactPubkey, "❤️") },
                    reactions = {
                        if (message.reactions != null) {
                            MessageReactions(message.reactions!!, kianColors)
                        }
                    }
                ) {
                    MessageContent(
                        message = message,
                        viewModel = viewModel,
                        colors = kianColors,
                        onActionClick = { 
                        }
                    )
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(kianColors.panel)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            listOf("❤️", "😂", "😮", "😢", "👍", "🔥", "🙏").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clickable {
                                            viewModel.toggleReaction(message.id, contactPubkey, emoji)
                                            showMenu = false
                                        },
                                    fontSize = 24.sp
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = kianColors.line)

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reply), color = kianColors.ink) },
                            onClick = {
                                replyingTo = message
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null, tint = kianColors.ink) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy), color = kianColors.ink) },
                            onClick = {
                                scope.launch { clipboard.setText(message.content) }
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = kianColors.ink) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = Color.Red) },
                            onClick = {
                                viewModel.deleteMessage(message.id)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }
        }
    }
}
