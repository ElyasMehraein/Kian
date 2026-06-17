package com.ely.kian.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.VoucherUtxo
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.theme.KianColors

@Composable
fun TokenPickerContent(
    balances: List<BalanceItem>,
    utxos: List<VoucherUtxo>,
    colors: KianColors,
    onTokenSelected: (String, Long) -> Unit
) {
    var selectedBalance by remember { mutableStateOf<BalanceItem?>(null) }
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
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 24.sp, fontWeight = FontWeight.Bold),
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
