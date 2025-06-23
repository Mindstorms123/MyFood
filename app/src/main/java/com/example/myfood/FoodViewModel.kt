package com.example.myfood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.example.myfood.data.openfoodfacts.OFFProduct // Importiere OFFProduct
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val _foodItemsInternal = mutableStateListOf<FoodItem>()
    val foodItems: List<FoodItem> = _foodItemsInternal

    init {
        viewModelScope.launch {
            FoodStore.getFoodList(application).collectLatest { loadedItems ->
                _foodItemsInternal.clear()
                _foodItemsInternal.addAll(loadedItems)
            }
        }
    }

    fun addManualItem(name: String, brand: String? = null, quantity: Int = 1) {
        val newItem = FoodItem(name = name, brand = brand, quantity = quantity.coerceAtLeast(0))
        _foodItemsInternal.add(newItem)
        saveItems()
    }

    fun addScannedProduct(product: OFFProduct) {
        val newItem = FoodItem(
            name = determineProductName(product),
            brand = product.brands?.takeIf { it.isNotBlank() }, // Nutzt product.brands direkt
            quantity = 1,
            openFoodFactsId = product.id
        )
        _foodItemsInternal.add(newItem)
        saveItems()
    }

    // KORRIGIERTE VERSION - PASSE FELDNAMEN AN DEINE OFFProduct-KLASSE AN!
    private fun determineProductName(product: OFFProduct): String {
        // Die einfachste und sauberste Methode ist, die Hilfsfunktion aus OFFProduct zu verwenden:
        return product.getDisplayName()

        // ODER, wenn du die Logik explizit hier haben möchtest (stelle sicher,
        // dass die Feldnamen mit deiner OFFProduct-Definition übereinstimmen):
        /*
        return product.productNameDE?.takeIf { it.isNotBlank() }
            ?: product.productName?.takeIf { it.isNotBlank() }
            ?: product.genericNameDE?.takeIf { it.isNotBlank() } // Nutze korrekte Feldnamen
            ?: product.genericName?.takeIf { it.isNotBlank() }   // Nutze korrekte Feldnamen
            ?: run {
                val primaryKeyword = product.keywords?.firstOrNull { keyword: String -> keyword.isNotBlank() }
                primaryKeyword
                    ?.replace("-", " ")
                    ?.replace("_", " ")
                    ?: product.id // Fallback auf ID, wenn vorhanden
                    ?: "Unbekanntes Produkt"
            }
        */
    }

    fun removeItem(index: Int) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal.removeAt(index)
            saveItems()
        }
    }

    fun updateItemName(index: Int, newName: String) {
        if (index in _foodItemsInternal.indices && newName.isNotBlank()) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(name = newName)
            saveItems()
        }
    }

    fun updateItemBrand(index: Int, newBrand: String?) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(brand = newBrand?.takeIf { it.isNotBlank() })
            saveItems()
        }
    }

    fun updateItemQuantity(index: Int, newQuantity: Int) {
        if (index in _foodItemsInternal.indices) {
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

class FoodViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FoodViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}