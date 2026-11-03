package com.quantify.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.entity.WishlistItemEntity
import com.quantify.app.data.repository.WishlistItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistItemRepository
) : ViewModel() {

    val items = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<WishlistItemEntity>())

    fun add(id: String, name: String, targetAmount: Double) {
        viewModelScope.launch {
            repository.insert(WishlistItemEntity(id = id, name = name, targetAmount = targetAmount))
        }
    }

    fun delete(item: WishlistItemEntity) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }
}
