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
import com.ely.kian.data.local.entities.Product
import com.ely.kian.ui.components.util.setText
import com.ely.kian.crypto.KianKeys
import com.ely.kian.ui.screens.chat.components.ChatBubbleLayout
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
    
    var showProductPicker by remember { mutableStateOf(false) }
    var showTokenPicker by remember { mutableStateOf(false) }
    val myProducts by viewModel.getMyProducts().collectAsState(initial = emptyList())
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
                            viewModel.sendMessage(contactPubkey, textState)
                            textState = ""
                            replyingTo = null
                        }
                    },
                    onProductClick = { showProductPicker = true },
                    onTokenClick = { showTokenPicker = true },
                    colors = kianColors
                )
            }
        },
        containerColor = kianColors.canvas
    ) { padding ->
        if (showProductPicker) {
            ModalBottomSheet(
                onDismissRequest = { showProductPicker = false },
                sheetState = sheetState,
                containerColor = kianColors.canvas
            ) {
                ProductPickerContent(
                    products = myProducts,
                    colors = kianColors,
                    onProductSelected = { product, quantity ->
                        viewModel.sendProductAsToken(contactPubkey, product.id, quantity)
                        showProductPicker = false
                    }
                )
            }
        }

        if (showTokenPicker) {
            ModalBottomSheet(
                onDismissRequest = { showTokenPicker = false },
                sheetState = sheetState,
                containerColor = kianColors.canvas
            ) {
                TokenPickerContent(
                    balances = myBalances,
                    utxos = viewModel.tokenRepository.getUtxos().collectAsState(initial = emptyList()).value,
                    colors = kianColors,
                    onTokenSelected = { utxoId, amount ->
                        viewModel.sendToken(contactPubkey, utxoId, amount)
                        showTokenPicker = false
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
                    onLongClick = { showMenu = true }
                ) {
                    MessageContent(
                        message = message,
                        viewModel = viewModel,
                        colors = kianColors,
                        onActionClick = { 
                            // Handle custom actions like "Confirm Receipt"
                        }
                    )
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(kianColors.panel)
                    ) {
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

@Composable
fun ReplyPreview(message: ChatMessage, colors: com.ely.kian.ui.theme.KianColors, onCancel: () -> Unit) {
    Surface(
        color = colors.panel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(32.dp).background(colors.accent, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (message.isMine) stringResource(R.string.replying_to_yourself) else stringResource(R.string.replying_to, message.pubkey.take(8)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    maxLines = 1,
                    color = colors.ink.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cancel), tint = colors.ink.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun MessageContent(
    message: ChatMessage,
    viewModel: ChatViewModel,
    colors: com.ely.kian.ui.theme.KianColors,
    onActionClick: () -> Unit
) {
    val textColor = if (message.isMine) Color.White else colors.ink
    val metadata = remember(message.metadata) {
        try {
            message.metadata?.let { Json.parseToJsonElement(it).jsonObject }
        } catch (e: Exception) {
            null
        }
    }

    Column {
        if (metadata != null) {
            TokenMessageCard(message, metadata, viewModel, colors)
        } else {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }

        Row(
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimestamp(message.createdAt),
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.7f)
            )
            if (message.isMine) {
                Spacer(modifier = Modifier.width(6.dp))
                val icon = when (message.status) {
                    "read" -> Icons.Default.DoneAll
                    "delivered" -> Icons.Default.DoneAll
                    "sent" -> Icons.Default.Check
                    "received" -> Icons.Default.CheckCircle
                    else -> Icons.Default.Check
                }
                val tint = if (message.status == "read" || message.status == "received") Color(0xFF4ADE80) else textColor.copy(alpha = 0.6f)
                Icon(
                    imageVector = icon,
                    contentDescription = message.status,
                    modifier = Modifier.size(17.dp),
                    tint = tint
                )
            }
        }
    }
}

@Composable
fun TokenMessageCard(
    message: ChatMessage,
    metadata: JsonObject,
    viewModel: ChatViewModel,
    colors: com.ely.kian.ui.theme.KianColors
) {
    val type = metadata["type"]?.jsonPrimitive?.content ?: ""
    val assetName = metadata["asset_name"]?.jsonPrimitive?.content ?: "Token"
    val amount = metadata["amount"]?.jsonPrimitive?.content ?: ""
    val utxoId = metadata["utxo_id"]?.jsonPrimitive?.content
    
    val isMine = message.isMine
    val cardColor = if (isMine) colors.accent.copy(alpha = 0.2f) else colors.panel
    val borderColor = if (isMine) colors.accent.copy(alpha = 0.4f) else colors.line
    
    // Check for "Live Status"
    val myUtxos by viewModel.getUtxos().collectAsState(initial = emptyList())
    val isConfirmed = remember(myUtxos, utxoId) {
        utxoId != null && myUtxos.any { it.prevUtxoId == utxoId }
    }

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = colors.accent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when(type) {
                                "token_mint" -> Icons.Default.CardGiftcard
                                "token_redemption" -> Icons.Default.LocalShipping
                                else -> Icons.Default.Send
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = when(type) {
                            "token_mint" -> stringResource(R.string.genesis_mint)
                            "token_redemption" -> stringResource(R.string.product_redemption)
                            else -> stringResource(R.string.token_transfer)
                        },
                        fontSize = 12.sp,
                        color = colors.muted,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$amount $assetName",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMine) Color.White else colors.ink
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status Section
            val statusText = when {
                message.status == "received" -> stringResource(R.string.order_completed)
                isConfirmed || message.status == "delivered" -> stringResource(R.string.verified_by_producer)
                type == "token_mint" -> stringResource(R.string.issued)
                type == "token_redemption" -> if (isMine) stringResource(R.string.waiting_for_delivery) else stringResource(R.string.pending_delivery)
                else -> stringResource(R.string.authenticating)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isConfirmed && message.status != "delivered" && message.status != "received" && type != "token_mint") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isConfirmed || message.status == "delivered" || message.status == "received" || type == "token_mint") Color(0xFF4ADE80) else colors.muted
                )
            }

            // Action Buttons
            if (type == "token_redemption" && isMine && message.status != "received") {
                Spacer(modifier = Modifier.height(12.dp))
                var showConfirmDialog by remember { mutableStateOf(false) }

                Button(
                    onClick = { showConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.confirm_receipt), color = Color.White, fontSize = 14.sp)
                }

                if (showConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = { Text(stringResource(R.string.confirm_delivery)) },
                        text = { Text(stringResource(R.string.confirm_delivery_confirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                if (utxoId != null) {
                                    viewModel.confirmProductReceipt(message.id, message.contactPubkey, utxoId)
                                }
                                showConfirmDialog = false
                            }) {
                                Text(stringResource(R.string.confirm), color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDialog = false }) {
                                Text(stringResource(R.string.cancel), color = colors.muted)
                            }
                        },
                        containerColor = colors.panel,
                        titleContentColor = colors.ink,
                        textContentColor = colors.ink
                    )
                }
            }
        }
    }
}

@Composable
fun TokenPickerContent(
    balances: List<com.ely.kian.data.repository.BalanceItem>,
    utxos: List<com.ely.kian.data.local.entities.TokenUtxo>,
    colors: com.ely.kian.ui.theme.KianColors,
    onTokenSelected: (String, Long) -> Unit
) {
    var selectedBalance by remember { mutableStateOf<com.ely.kian.data.repository.BalanceItem?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(colors.line, RoundedCornerShape(2.dp))
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedBalance == null) {
            Text(stringResource(R.string.select_token_to_send), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            Text(stringResource(R.string.token_picker_desc), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
            
            if (balances.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_tokens_yet), color = colors.muted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    items(balances) { balance ->
                        Surface(
                            onClick = { selectedBalance = balance },
                            color = colors.panel,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(10.dp), color = colors.accent.copy(alpha = 0.1f), modifier = Modifier.size(52.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(balance.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 18.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(balance.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.ink)
                                    Text(stringResource(R.string.available, "${balance.amount} ${balance.unit}"), fontSize = 13.sp, color = colors.muted)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted)
                            }
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedBalance = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.ink)
                }
                Text(stringResource(R.string.set_amount), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Surface(color = colors.panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedBalance!!.name, fontWeight = FontWeight.Bold, color = colors.ink)
                        Text("Balance: ${selectedBalance!!.amount}", fontSize = 12.sp, color = colors.accent)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    val q = quantityText.toLongOrNull() ?: 1L
                    if (q > 1) quantityText = (q - 1).toString()
                }) {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = colors.ink)
                }
                
                TextField(
                    value = quantityText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) quantityText = it },
                    modifier = Modifier.width(120.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
                
                IconButton(onClick = { 
                    val q = quantityText.toLongOrNull() ?: 0L
                    if (q < selectedBalance!!.amount) quantityText = (q + 1).toString()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = colors.ink)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { 
                    val q = quantityText.toLongOrNull() ?: 1L
                    // Find a UTXO that has enough balance. 
                    // Simplified: just pick the first one that has ANY balance and let the repo handle splitting/merging?
                    // Actually, sendTokenTransfer currently takes a specific UTXO ID.
                    val utxo = utxos.filter { it.assetRef == selectedBalance!!.assetRef && !it.spent }.maxByOrNull { it.amount }
                    if (utxo != null) {
                        onTokenSelected(utxo.utxoId, q)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Text(stringResource(R.string.confirm_and_send), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}


@Composable
fun ProductPickerContent(
    products: List<Product>,
    colors: com.ely.kian.ui.theme.KianColors,
    onProductSelected: (Product, Long) -> Unit
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 40.dp)
    ) {
        // Handle Bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(colors.line, RoundedCornerShape(2.dp))
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedProduct == null) {
            Text(
                stringResource(R.string.select_product_to_send),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Text(
                stringResource(R.string.product_picker_desc),
                fontSize = 14.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )
            
            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_products_yet), 
                        color = colors.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(products) { product ->
                        Surface(
                            onClick = { selectedProduct = product },
                            color = colors.panel,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp), 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.accent.copy(alpha = 0.1f),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            product.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accent,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        product.name, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 16.sp,
                                        color = colors.ink
                                    )
                                    if (!product.description.isNullOrBlank()) {
                                        Text(
                                            product.description, 
                                            fontSize = 13.sp, 
                                            color = colors.muted, 
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Icon(
                                    Icons.Default.ChevronRight, 
                                    contentDescription = "Next", 
                                    tint = colors.muted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Quantity Picker for selected product
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedProduct = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.ink)
                }
                Text(
                    stringResource(R.string.set_quantity),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Surface(
                color = colors.panel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedProduct!!.name, fontWeight = FontWeight.Bold, color = colors.ink)
                        Text("Issuing Genesis Tokens", fontSize = 12.sp, color = colors.accent)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { 
                        val q = quantityText.toLongOrNull() ?: 1L
                        if (q > 1) quantityText = (q - 1).toString()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.panel, contentColor = colors.ink)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                
                TextField(
                    value = quantityText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) quantityText = it },
                    modifier = Modifier.width(100.dp).padding(horizontal = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = colors.accent,
                        unfocusedIndicatorColor = colors.line
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                
                FilledIconButton(
                    onClick = { 
                        val q = quantityText.toLongOrNull() ?: 0L
                        quantityText = (q + 1).toString()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.panel, contentColor = colors.ink)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { 
                    val q = quantityText.toLongOrNull() ?: 1L
                    onProductSelected(selectedProduct!!, q)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Text(stringResource(R.string.confirm_and_send), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onProductClick: () -> Unit,
    onTokenClick: () -> Unit,
    colors: com.ely.kian.ui.theme.KianColors
) {
    Surface(
        color = colors.canvas,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onProductClick) {
                Icon(Icons.Default.Inventory2, contentDescription = "Send Product", tint = colors.accent)
            }
            
            IconButton(onClick = onTokenClick) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = "Send Token", tint = colors.accent)
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.type_message), color = colors.ink.copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.panel,
                    unfocusedContainerColor = colors.panel,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.panel
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
