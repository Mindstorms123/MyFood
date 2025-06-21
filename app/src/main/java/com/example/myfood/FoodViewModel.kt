package com.example.myfood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val _foodItemsInternal = mutableStateListOf<FoodItem>()
    val foodItems: List<FoodItem> = _foodItemsInternal

    init {
        viewModelScope.launch {
            // FoodStore.getFoodList gibt jetzt Flow<List<FoodItem>> zurück
            FoodStore.getFoodList(application).collectLatest { loadedItems ->
                _foodItemsInternal.clear()
                _foodItemsInternal.addAll(loadedItems)
            }
        }
    }

    // Methode zum Hinzufügen eines manuell eingegebenen Items
    fun addManualItem(name: String, brand: String? = null, quantity: Int = 1) {
        val newItem = FoodItem(name = name, brand = brand, quantity = quantity.coerceAtLeast(0)) // Menge min. 0
        _foodItemsInternal.add(newItem)
        saveItems()
    }

    // Methode zum Hinzufügen eines Produkts, das von der API geholt wurde
    fun addScannedProduct(productData: ProductFromAPI) {
        val newItem = FoodItem(
            name = determineProductName(productData),
            brand = productData.brands?.takeIf { it.isNotBlank() }, // Nur nicht-leere Marken übernehmen
            quantity = 1 // Standardmenge für neue gescannte Artikel
        )
        _foodItemsInternal.add(newItem)
        saveItems()
    }

    // Hilfsfunktion zur Namensbestimmung
    private fun determineProductName(productData: ProductFromAPI): String {
        return productData.product_name?.takeIf { it.isNotBlank() }
            ?: run {
                val waterKeywords = productData._keywords?.filter {
                    it.contains("wasser", ignoreCase = true) || it.contains("mineral", ignoreCase = true)
                }
                val keywordName = waterKeywords?.firstOrNull()
                    ?: productData._keywords?.firstOrNull()
                    ?: "Unbekanntes Produkt"
                keywordName.replace("-", " ").replace("_", " ")
            }
    }

    fun removeItem(index: Int) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal.removeAt(index)
            saveItems()
        }
    }

    fun updateItemName(index: Int, newName: String) {
        if (index in _foodItemsInternal.indices && newName.isNotBlank()) { // Namen dürfen nicht leer sein
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(name = newName)
            saveItems()
        }
    }

    // Optional: Methode zum Aktualisieren der Marke
    fun updateItemBrand(index: Int, newBrand: String?) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(brand = newBrand?.takeIf { it.isNotBlank() })
            saveItems()
        }
    }

    fun updateItemQuantity(index: Int, newQuantity: Int) {
        if (index in _foodItemsInternal.indices) {
            // Stelle sicher, dass die Menge nicht negativ wird (oder was auch immer deine Regel ist)
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(quantity = newQuantity.coerceAtLeast(0))
            saveItems()
        }
    }

    private fun saveItems() {
        viewModelScope.launch {
            FoodStore.saveFoodList(getApplication(), _foodItemsInternal.toList())
        }
    }
}

// Die ViewModelFactory bleibt gleich
class FoodViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FoodViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}