package com.example.myfood

import android.app.Application
import android.util.Log // Für Logging hinzugefügt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.example.myfood.data.openfoodfacts.OFFProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class FoodViewModel(application: Application) : AndroidViewModel(application) {

    private val _foodItemsInternal = mutableStateListOf<FoodItem>()

    private val _foodItemsStateFlow = MutableStateFlow<List<FoodItem>>(emptyList())
    val foodItemsStateFlow: StateFlow<List<FoodItem>> = _foodItemsStateFlow.asStateFlow()

    // Für die UI, die keinen Flow direkt konsumiert (optional, aber kann nützlich sein)
    val foodItems: List<FoodItem>
        get() = _foodItemsStateFlow.value

    private val _scannedProductForEditing = MutableStateFlow<OFFProduct?>(null)
    val scannedProductForEditing: StateFlow<OFFProduct?> = _scannedProductForEditing.asStateFlow()

    private val _itemToEdit = MutableStateFlow<FoodItem?>(null)
    val itemToEdit: StateFlow<FoodItem?> = _itemToEdit.asStateFlow()

    init {
        viewModelScope.launch {
            FoodStore.getFoodList(application).collectLatest { loadedItems ->
                _foodItemsInternal.clear()
                _foodItemsInternal.addAll(loadedItems)
                _foodItemsStateFlow.value = _foodItemsInternal.toList() // StateFlow mit der geladenen Liste initialisieren
            }
        }
    }

    fun setScannedProductForEditing(product: OFFProduct?) {
        _scannedProductForEditing.value = product
        _itemToEdit.value = null // Stelle sicher, dass nur ein Bearbeitungsmodus aktiv ist
    }

    fun setItemToEdit(item: FoodItem?) {
        _itemToEdit.value = item
        _scannedProductForEditing.value = null // Stelle sicher, dass nur ein Bearbeitungsmodus aktiv ist
    }

    fun confirmAndAddEditedScannedItem(
        originalProductId: String, // Dies ist die ID des OFFProduct (Barcode)
        name: String,
        brand: String?,
        quantity: Int,
        expiryDate: LocalDate?
    ) {
        val newItem = FoodItem(
            name = name,
            brand = brand?.takeIf { it.isNotBlank() },
            quantity = quantity.coerceAtLeast(1), // Menge sollte mindestens 1 sein
            openFoodFactsId = originalProductId, // Speichere den Barcode
            expiryDate = expiryDate
        )
        _foodItemsInternal.add(newItem)
        _foodItemsStateFlow.value = _foodItemsInternal.toList()
        saveItems()
        clearEditingStates() // Bearbeitungsstatus nach Hinzufügen zurücksetzen
    }

    fun updateExistingFoodItem(
        itemId: String, // ID des FoodItem, das aktualisiert wird
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
                quantity = newQuantity.coerceAtLeast(0), // Menge kann 0 sein (z.B. "aufgebraucht")
                expiryDate = newExpiryDate
            )
            _foodItemsStateFlow.value = _foodItemsInternal.toList()
            saveItems()
        } else {
            Log.w("FoodViewModel", "updateExistingFoodItem: Item mit ID $itemId nicht gefunden.")
        }
        clearEditingStates() // Bearbeitungsstatus nach Update zurücksetzen
    }

    fun addManualItem(name: String, brand: String? = null, quantity: Int = 1, expiryDate: LocalDate? = null) {
        val newItem = FoodItem(
            name = name,
            brand = brand?.takeIf { it.isNotBlank() },
            quantity = quantity.coerceAtLeast(0),
            expiryDate = expiryDate
        )
        _foodItemsInternal.add(newItem)
        _foodItemsStateFlow.value = _foodItemsInternal.toList()
        saveItems()
    }

    fun removeItemById(itemId: String) {
        val itemIndex = _foodItemsInternal.indexOfFirst { it.id == itemId }
        if (itemIndex != -1) {
            val removedItem = _foodItemsInternal.removeAt(itemIndex)
            _foodItemsStateFlow.value = _foodItemsInternal.toList()

            // Wenn das gelöschte Item gerade bearbeitet wurde, den Bearbeitungsstatus zurücksetzen
            if (_itemToEdit.value?.id == removedItem.id) {
                _itemToEdit.value = null
            }
            saveItems()
        } else {
            Log.w("FoodViewModel", "removeItemById: Item mit ID $itemId nicht gefunden.")
        }
    }

    fun updateItemQuantityById(itemId: String, newQuantity: Int) {
        val itemIndex = _foodItemsInternal.indexOfFirst { it.id == itemId }
        if (itemIndex != -1) {
            // Menge sollte nicht negativ werden. Wenn 0 erreicht wird, könnte man es auch entfernen.
            val quantityToSet = newQuantity.coerceAtLeast(0)
            _foodItemsInternal[itemIndex] = _foodItemsInternal[itemIndex].copy(quantity = quantityToSet)
            _foodItemsStateFlow.value = _foodItemsInternal.toList()
            saveItems()
            // Optional: Wenn Menge 0 ist und das Item automatisch entfernt werden soll:
            // if (quantityToSet == 0) {
            //     removeItemById(itemId)
            // }
        } else {
            Log.w("FoodViewModel", "updateItemQuantityById: Item mit ID $itemId nicht gefunden.")
        }
    }

    // Index-basierte Update-Funktionen entfernt, da Bearbeitung über EditScreen und ID-basierte Updates bevorzugt wird.
    // fun updateItemName(index: Int, newName: String) { ... }
    // fun updateItemBrand(index: Int, newBrand: String?) { ... }
    // fun updateItemExpiryDate(index: Int, newExpiryDate: LocalDate?) { ... }


    private fun saveItems() {
        viewModelScope.launch {
            // Speichere den aktuellen Wert des StateFlows, um Konsistenz zu gewährleisten
            FoodStore.saveFoodList(getApplication(), _foodItemsStateFlow.value)
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
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// Diese Extension-Funktion gehört logisch eher zur OFFProduct-Definition oder einer Utility-Datei,
// aber kann hier bleiben, wenn es nur an dieser Stelle verwendet wird.
fun OFFProduct.getDisplayName(): String =
    this.productName // Bevorzuge den allgemeinen Produktnamen
        ?: this.productNameDE // Dann Deutsch
        ?: this.productNameEN // Dann Englisch
        ?: this.genericName // Dann den generischen Namen
        //?: this.name // Fallback auf ein einfaches 'name'-Feld, falls vorhanden
        ?: "Unbekanntes Produkt" // Letzter Fallback
