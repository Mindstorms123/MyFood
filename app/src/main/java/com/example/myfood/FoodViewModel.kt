package com.example.myfood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateListOf // Beibehalten für interne Logik falls nötig
import androidx.lifecycle.viewModelScope
import com.example.myfood.data.openfoodfacts.OFFProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    // Interner MutableStateList, wenn du ihn weiterhin für andere Zwecke (z.B. direkte Compose-Beobachtung) nutzt
    private val _foodItemsInternal = mutableStateListOf<FoodItem>()
    // Öffentliche, unveränderliche Liste (bleibt, um bestehende Nutzung nicht zu brechen, wird aber vom StateFlow abgeleitet)
    // Diese wird weniger relevant, wenn alles über StateFlow läuft.
    val foodItems: List<FoodItem>
        get() = _foodItemsStateFlow.value


    // Neuer StateFlow für die UI-Beobachtung (z.B. in RecipeDetailScreen)
    private val _foodItemsStateFlow = MutableStateFlow<List<FoodItem>>(emptyList())
    val foodItemsStateFlow: StateFlow<List<FoodItem>> = _foodItemsStateFlow.asStateFlow()

    // Für das temporäre Halten des gescannten Produkts zur Bearbeitung
    private val _scannedProductForEditing = MutableStateFlow<OFFProduct?>(null)
    val scannedProductForEditing: StateFlow<OFFProduct?> = _scannedProductForEditing.asStateFlow()

    // Für das temporäre Halten eines bestehenden FoodItem zur Bearbeitung
    private val _itemToEdit = MutableStateFlow<FoodItem?>(null)
    val itemToEdit: StateFlow<FoodItem?> = _itemToEdit.asStateFlow()

    init {
        viewModelScope.launch {
            FoodStore.getFoodList(application).collectLatest { loadedItems ->
                _foodItemsInternal.clear()
                _foodItemsInternal.addAll(loadedItems)
                _foodItemsStateFlow.value = loadedItems // StateFlow aktualisieren
            }
        }
    }

    fun setScannedProductForEditing(product: OFFProduct?) {
        _scannedProductForEditing.value = product
        _itemToEdit.value = null
    }

    fun setItemToEdit(item: FoodItem?) {
        _itemToEdit.value = item
        _scannedProductForEditing.value = null
    }

    fun confirmAndAddEditedScannedItem(
        originalProductId: String,
        name: String,
        brand: String?,
        quantity: Int,
        expiryDate: LocalDate?
    ) {
        val newItem = FoodItem(
            name = name,
            brand = brand?.takeIf { it.isNotBlank() },
            quantity = quantity.coerceAtLeast(1),
            openFoodFactsId = originalProductId,
            expiryDate = expiryDate
        )
        _foodItemsInternal.add(newItem) // _foodItemsInternal aktuell halten
        _foodItemsStateFlow.value = _foodItemsInternal.toList() // StateFlow mit neuer Liste aktualisieren
        saveItems()
        _scannedProductForEditing.value = null
    }

    fun updateExistingFoodItem(
        itemId: String,
        newName: String,
        newBrand: String?,
        newQuantity: Int,
        newExpiryDate: LocalDate?
    ) {
        val itemIndex = _foodItemsInternal.indexOfFirst { it.id == itemId }
        if (itemIndex != -1) {
            _foodItemsInternal[itemIndex] = _foodItemsInternal[itemIndex].copy(
                name = newName,
                brand = newBrand?.takeIf { it.isNotBlank() },
                quantity = newQuantity.coerceAtLeast(1),
                expiryDate = newExpiryDate
            )
            _foodItemsStateFlow.value = _foodItemsInternal.toList() // StateFlow aktualisieren
            saveItems()
        }
        _itemToEdit.value = null
    }

    fun addManualItem(name: String, brand: String? = null, quantity: Int = 1, expiryDate: LocalDate? = null) {
        val newItem = FoodItem(
            name = name,
            brand = brand,
            quantity = quantity.coerceAtLeast(0),
            expiryDate = expiryDate
        )
        _foodItemsInternal.add(newItem) // _foodItemsInternal aktuell halten
        _foodItemsStateFlow.value = _foodItemsInternal.toList() // StateFlow aktualisieren
        saveItems()
    }

    fun removeItem(index: Int) { // Index bezieht sich hier auf _foodItemsInternal
        if (index in _foodItemsInternal.indices) {
            val item = _foodItemsInternal.removeAt(index) // _foodItemsInternal aktuell halten
            _foodItemsStateFlow.value = _foodItemsInternal.toList() // StateFlow aktualisieren

            if (_itemToEdit.value?.id == item.id) {
                _itemToEdit.value = null
            }
            saveItems()
        }
    }

    // Die updateItemXYZ Funktionen sollten auch den StateFlow aktualisieren.
    // Sie beziehen sich auf den Index in _foodItemsInternal.
    fun updateItemName(index: Int, newName: String) {
        if (index in _foodItemsInternal.indices && newName.isNotBlank()) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(name = newName)
            _foodItemsStateFlow.value = _foodItemsInternal.toList()
            saveItems()
        }
    }

    fun updateItemBrand(index: Int, newBrand: String?) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(brand = newBrand?.takeIf { it.isNotBlank() })
            _foodItemsStateFlow.value = _foodItemsInternal.toList()
            saveItems()
        }
    }

    fun updateItemQuantity(index: Int, newQuantity: Int) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(quantity = newQuantity.coerceAtLeast(0))
            _foodItemsStateFlow.value = _foodItemsInternal.toList()
            saveItems()
        }
    }

    fun updateItemExpiryDate(index: Int, newExpiryDate: LocalDate?) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(expiryDate = newExpiryDate)
            _foodItemsStateFlow.value = _foodItemsInternal.toList()
            saveItems()
        }
    }

    private fun saveItems() {
        viewModelScope.launch {
            // Es ist sicherer, die Liste vom StateFlow oder von _foodItemsInternal.toList() zu speichern,
            // um sicherzustellen, dass die gespeicherte Liste konsistent ist.
            FoodStore.saveFoodList(getApplication(), _foodItemsInternal.toList())
        }
    }

    fun clearEditingStates() {
        _itemToEdit.value = null
        _scannedProductForEditing.value = null
    }
}

class FoodViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FoodViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

fun OFFProduct.getDisplayName(): String = this.productName ?: this.genericName ?: "Produkt ohne Namen"

