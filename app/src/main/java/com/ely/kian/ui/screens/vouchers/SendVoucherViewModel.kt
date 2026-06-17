package com.ely.kian.ui.screens.vouchers

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.entities.VoucherUtxo
import com.ely.kian.data.repository.VoucherRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoucherCardItem(
    val utxo: VoucherUtxo,
    val name: String,
    val description: String?,
    val images: List<String>,
    val categories: List<String>,
    val unit: String
)

class SendVoucherViewModel(
    private val voucherRepository: VoucherRepository
) : ViewModel() {

    companion object {
        fun provideFactory(
            voucherRepository: VoucherRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SendVoucherViewModel(voucherRepository) as T
            }
        }
    }

    var recipient by mutableStateOf("")
    val utxos: StateFlow<List<VoucherUtxo>> = voucherRepository.getUtxos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tokenItems = derivedStateOf {
        utxos.value.map { utxo ->
            VoucherCardItem(
                utxo = utxo,
                name = utxo.assetRef.split(":").lastOrNull() ?: utxo.utxoId.takeLast(8),
                description = "Havaleh ready to transfer.",
                images = emptyList(),
                categories = emptyList(),
                unit = "unit"
            )
        }
    }

    private val _quantities = mutableStateMapOf<String, Long>()
    val quantities: Map<String, Long> = _quantities

    var isSending by mutableStateOf(false)
        private set

    fun updateQuantity(utxoId: String, delta: Long, max: Long) {
        val current = _quantities[utxoId] ?: 0L
        val next = (current + delta).coerceIn(0L, max)
        if (next == 0L) {
            _quantities.remove(utxoId)
        } else {
            _quantities[utxoId] = next
        }
    }

    fun handleSend(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (recipient.isBlank()) {
            onError("Recipient required")
            return
        }
        if (_quantities.isEmpty()) {
            onError("Nothing selected")
            return
        }

        viewModelScope.launch {
            isSending = true
            try {
                for (entry in _quantities) {
                    voucherRepository.sendTokenTransfer(entry.key, entry.value, recipient)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to send")
            } finally {
                isSending = false
            }
        }
    }
}
