package com.example.myfood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.example.myfood.data.openfoodfacts.OFFProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow // WICHTIG: Import für asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val _foodItemsInternal = mutableStateListOf<FoodItem>()
    val foodItems: List<FoodItem> = _foodItemsInternal

    // Für das temporäre Halten des gescannten Produkts zur Bearbeitung
    private val _scannedProductForEditing = MutableStateFlow<OFFProduct?>(null)
    val scannedProductForEditing: StateFlow<OFFProduct?> = _scannedProductForEditing.asStateFlow() // Gute Praxis: .asStateFlow()

    // --- NEU: Für das temporäre Halten eines bestehenden FoodItem zur Bearbeitung ---
    private val _itemToEdit = MutableStateFlow<FoodItem?>(null)
    val itemToEdit: StateFlow<FoodItem?> = _itemToEdit.asStateFlow()
    // --------------------------------------------------------------------------------

    init {
        viewModelScope.launch {
            FoodStore.getFoodList(application).collectLatest { loadedItems ->
                _foodItemsInternal.clear()
                _foodItemsInternal.addAll(loadedItems)
            }
        }
    }

    // Diese Funktion wird vom Barcode-Scanner aufgerufen
    fun setScannedProductForEditing(product: OFFProduct?) {
        _scannedProductForEditing.value = product
        _itemToEdit.value = null // Stelle sicher, dass der andere Modus deaktiviert ist
    }

    // --- NEU: Diese Funktion wird aufgerufen, wenn ein bestehendes Item bearbeitet werden soll ---
    fun setItemToEdit(item: FoodItem?) {
        _itemToEdit.value = item
        _scannedProductForEditing.value = null // Stelle sicher, dass der andere Modus deaktiviert ist
    }
    // ------------------------------------------------------------------------------------

    // Wird vom EditItemScreen aufgerufen, um ein NEUES gescanntes Produkt final hinzuzufügen
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
        _foodItemsInternal.add(newItem)
        saveItems()
        _scannedProductForEditing.value = null // Wichtig: Zurücksetzen nach Gebrauch
    }

    // --- NEU: Wird vom EditItemScreen aufgerufen, um ein BESTEHENDES Produkt zu aktualisieren ---
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
                quantity = newQuantity.coerceAtLeast(1), // Mindestmenge 1 beim Bearbeiten
                expiryDate = newExpiryDate
            )
            saveItems()
        }
        _itemToEdit.value = null // Wichtig: Zurücksetzen nach Gebrauch
    }
    // --------------------------------------------------------------------------------------


    fun addManualItem(name: String, brand: String? = null, quantity: Int = 1, expiryDate: LocalDate? = null) {
        val newItem = FoodItem(
            name = name,
            brand = brand,
            quantity = quantity.coerceAtLeast(0), // Hier 0 ok, wenn man es so will
            expiryDate = expiryDate
        )
        _foodItemsInternal.add(newItem)
        saveItems()
    }

    /*
    fun addScannedProduct(product: OFFProduct) { // ... auskommentiert ... }
    */

    // private fun determineProductName(product: OFFProduct): String { // wird im EditScreen nicht mehr direkt vom VM benötigt
    // return product.getDisplayName()
    // }

    fun removeItem(index: Int) {
        if (index in _foodItemsInternal.indices) {
            val item = _foodItemsInternal.removeAt(index)
            // Wenn das gelöschte Item gerade bearbeitet wurde, Zustand zurücksetzen
            if (_itemToEdit.value?.id == item.id) {
                _itemToEdit.value = null
            }
            saveItems()
        }
    }

    // Die folgenden updateItemXYZ Funktionen werden vom EditItemScreen nicht mehr direkt verwendet,
    // könnten aber für andere Zwecke nützlich sein (z.B. schnelle Inline-Änderungen in der Liste).
    // Wenn EditItemScreen die einzige Methode zum Bearbeiten sein soll, könntest du sie entfernen.
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

    fun updateItemExpiryDate(index: Int, newExpiryDate: LocalDate?) {
        if (index in _foodItemsInternal.indices) {
            _foodItemsInternal[index] = _foodItemsInternal[index].copy(expiryDate = newExpiryDate)
            saveItems()
        }
    }

    private fun saveItems() {
        viewModelScope.launch {
            FoodStore.saveFoodList(getApplication(), _foodItemsInternal.toList())
        }
    }

    fun clearEditingStates() {
        _itemToEdit.value = null // Stelle sicher, dass _itemToEdit hier dein MutableStateFlow für das zu bearbeitende Item ist
        _scannedProductForEditing.value = null // Stelle sicher, dass _scannedProductForEditing dein MutableStateFlow für das gescannte Produkt ist
    }
}

// FoodViewModelFactory bleibt unverändert
class FoodViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FoodViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Extension function für OFFProduct, falls nicht schon vorhanden,
// damit EditItemScreen.kt `getDisplayName()` im Preview-Teil verwenden kann.
// Wenn deine OFFProduct-Klasse diese Methode schon hat, ist das hier nicht nötig.
fun OFFProduct.getDisplayName(): String = this.productName ?: this.genericName ?: "Produkt ohne Namen"

