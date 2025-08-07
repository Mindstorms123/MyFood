package com.example.myfood.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.FoodItem // Stelle sicher, dass FoodItem auch 'brand: String?' hat und @Serializable ist
import com.example.myfood.data.openfoodfacts.OFFProduct
import com.example.myfood.data.openfoodfacts.OpenFoodFactsApiService
import com.example.myfood.data.pantry.PantryRepository
import com.example.myfood.data.shopping.ShoppingListItem
import com.example.myfood.data.shopping.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID // Für FoodItem ID Generierung
import javax.inject.Inject

data class ShoppingListUiState(
    val items: List<ShoppingListItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<OFFProduct> = emptyList(),
    val isLoadingSearch: Boolean = false,
    val errorSearching: String? = null,
    val showAddItemDialog: Boolean = false,
    val itemToEdit: ShoppingListItem? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
    private val pantryRepository: PantryRepository,
    private val openFoodFactsApiService: OpenFoodFactsApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadShoppingListItems()
        observeSearchQuery()
    }

    private fun loadShoppingListItems() {
        viewModelScope.launch {
            shoppingListRepository.getAllItems()
                .distinctUntilChanged()
                .collect { items ->
                    // Alle Items laden und nach Zeitstempel sortieren
                    _uiState.update { it.copy(items = items.sortedByDescending { item -> item.timestamp }) }
                }
        }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            _uiState
                .map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(500L)
                .collectLatest { query ->
                    if (query.length > 2) {
                        performSearch(query)
                    } else {
                        _uiState.update {
                            it.copy(
                                searchResults = emptyList(),
                                isLoadingSearch = false,
                                errorSearching = null
                            )
                        }
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isLoadingSearch = query.length > 2) }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(isLoadingSearch = true, errorSearching = null) }
        searchJob = viewModelScope.launch {
            try {
                val result = openFoodFactsApiService.searchProducts(searchTerm = query)
                result.fold(
                    onSuccess = { productsList ->
                        _uiState.update {
                            it.copy(searchResults = productsList, isLoadingSearch = false)
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(searchResults = emptyList(), isLoadingSearch = false, errorSearching = "Suche fehlgeschlagen: ${error.localizedMessage}")
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(searchResults = emptyList(), isLoadingSearch = false, errorSearching = "Fehler: ${e.localizedMessage}")
                }
            }
        }
    }

    fun clearSearchResultsAndQuery() {
        _uiState.update {
            it.copy(
                searchResults = emptyList(),
                searchQuery = "",
                isLoadingSearch = false,
                errorSearching = null
            )
        }
    }

    fun addItemFromOffProduct(product: OFFProduct) {
        saveShoppingListItem(
            id = 0,
            name = product.getDisplayName(),
            brand = product.brands?.split(",")?.firstOrNull()?.trim(),
            quantity = "1",
            unit = "Produkt",
            openFoodFactsProductId = product.id
        )
        clearSearchResultsAndQuery()
    }

    fun saveShoppingListItem(id: Int, name: String, brand: String?, quantity: String, unit: String, openFoodFactsProductId: String? = null) {
        viewModelScope.launch {
            val itemToSave: ShoppingListItem
            if (id != 0) { // Edit mode
                val existingItem = shoppingListRepository.getItemById(id)
                itemToSave = existingItem?.copy(
                    name = name,
                    brand = brand,
                    quantity = quantity,
                    unit = unit
                    // openFoodFactsProductId bleibt erhalten, wenn schon gesetzt
                ) ?: ShoppingListItem(id = 0, name = name, brand = brand, quantity = quantity, unit = unit, openFoodFactsProductId = openFoodFactsProductId) // Fallback
                if (existingItem != null) {
                    shoppingListRepository.updateItem(itemToSave)
                } else {
                    shoppingListRepository.insertItem(itemToSave.copy(id = 0)) // Als neues Item, falls Edit fehlschlägt
                }
            } else { // Add mode
                itemToSave = ShoppingListItem(
                    name = name,
                    brand = brand,
                    quantity = quantity,
                    unit = unit,
                    openFoodFactsProductId = openFoodFactsProductId
                )
                shoppingListRepository.insertItem(itemToSave)
            }
            onShowAddItemDialogChanged(false, null)
        }
    }

    fun deleteItem(itemId: Int) {
        viewModelScope.launch {
            shoppingListRepository.deleteItemById(itemId)
        }
    }

    // --- WIEDERHERGESTELLTE FUNKTION ---
    fun toggleItemChecked(itemId: Int) {
        viewModelScope.launch {
            println("toggleItemChecked: Funktion gestartet für itemId: $itemId")
            val item = shoppingListRepository.getItemById(itemId)
            if (item == null) {
                println("toggleItemChecked: Item mit ID $itemId nicht gefunden.")
                return@launch
            }
            val updatedItem = item.copy(isChecked = !item.isChecked)
            println("toggleItemChecked: Aktualisiere Item '${updatedItem.name}' zu isChecked=${updatedItem.isChecked}")
            shoppingListRepository.updateItem(updatedItem)
            println("toggleItemChecked: Item erfolgreich aktualisiert.")
        }
    }

    // --- FUNKTION FÜR DAS ÜBERTRAGEN ALLER MARKIERTER ITEMS ---
    fun transferCheckedItemsToPantryAndClear() {
        viewModelScope.launch {
            println("transferCheckedItemsToPantryAndClear: Funktion gestartet.")

            // Hole die aktuell als 'isChecked' markierten Items direkt aus der DB/Repository,
            // da _uiState.value.items den isChecked-Status evtl. noch nicht reflektiert, wenn
            // toggleItemChecked gerade erst aufgerufen wurde und der Flow noch nicht aktualisiert hat.
            // Sicherer ist es, die Datenquelle direkt abzufragen oder eine leichte Verzögerung einzubauen.
            // Für diese Implementierung verlassen wir uns darauf, dass der Nutzer nach dem Markieren auf den Button klickt
            // und der _uiState bis dahin aktuell ist. Alternativ:
            // val checkedItems = shoppingListRepository.getAllItems().first().filter { it.isChecked }
            val checkedItems = _uiState.value.items.filter { it.isChecked }

            println("transferCheckedItemsToPantryAndClear: Anzahl geprüfter ShoppingListItems: ${checkedItems.size}")
            if (checkedItems.isEmpty()) {
                println("transferCheckedItemsToPantryAndClear: Keine Items zum Übertragen ausgewählt. Funktion wird beendet.")
                return@launch
            }
            checkedItems.forEachIndexed { index, item ->
                println("transferCheckedItemsToPantryAndClear: ShoppingListItem[$index] - ID: ${item.id}, Name: ${item.name}, Menge: ${item.quantity}, Marke: ${item.brand}, isChecked: ${item.isChecked}")
            }

            val itemsToTransfer = mutableListOf<FoodItem>()
            for (checkedShoppingItem in checkedItems) {
                val quantityString = checkedShoppingItem.quantity
                val numericQuantity = quantityString.split(" ").firstOrNull()?.toDoubleOrNull()?.toInt()
                    ?: quantityString.filter { it.isDigit() }.toIntOrNull()
                    ?: 1

                val brandName = checkedShoppingItem.brand
                println("transferCheckedItemsToPantryAndClear: Für '${checkedShoppingItem.name}' - numericQuantity: $numericQuantity, brandName: $brandName")

                val foodItemToPantry = FoodItem(
                    id = UUID.randomUUID().toString(), // Eindeutige ID für jedes FoodItem
                    name = checkedShoppingItem.name,
                    brand = brandName,
                    quantity = numericQuantity,
                    openFoodFactsId = checkedShoppingItem.openFoodFactsProductId
                )
                itemsToTransfer.add(foodItemToPantry)
            }

            println("transferCheckedItemsToPantryAndClear: Zu übertragende FoodItems (Gesamt: ${itemsToTransfer.size}):")
            itemsToTransfer.forEachIndexed { index, item ->
                println("transferCheckedItemsToPantryAndClear: FoodItemToTransfer[$index] - Name: ${item.name}, Menge: ${item.quantity}, Marke: ${item.brand}, ID: ${item.id}")
            }

            if (itemsToTransfer.isNotEmpty()) {
                try {
                    println("transferCheckedItemsToPantryAndClear: Rufe pantryRepository.addFoodItems auf...")
                    pantryRepository.addFoodItems(itemsToTransfer)
                    println("transferCheckedItemsToPantryAndClear: pantryRepository.addFoodItems ERFOLGREICH aufgerufen.")
                } catch (e: Exception) {
                    println("transferCheckedItemsToPantryAndClear: FEHLER beim Aufruf von pantryRepository.addFoodItems: ${e.message}")
                    e.printStackTrace()
                    return@launch // Bei Fehler hier abbrechen
                }

                try {
                    println("transferCheckedItemsToPantryAndClear: Rufe shoppingListRepository.deleteItems auf...")
                    shoppingListRepository.deleteItems(checkedItems) // Lösche die übertragenen ShoppingListItems
                    println("transferCheckedItemsToPantryAndClear: shoppingListRepository.deleteItems ERFOLGREICH aufgerufen.")
                } catch (e: Exception) {
                    println("transferCheckedItemsToPantryAndClear: FEHLER beim Aufruf von shoppingListRepository.deleteItems: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                println("transferCheckedItemsToPantryAndClear: Keine FoodItems zum Übertragen erstellt.")
            }
            println("transferCheckedItemsToPantryAndClear: Funktion beendet.")
        }
    }

    // Diese Funktion kann nützlich sein, um alle markierten Items zu löschen, ohne sie zu übertragen.
    fun deleteCheckedItems() {
        viewModelScope.launch {
            val checkedItems = _uiState.value.items.filter { it.isChecked }
            if (checkedItems.isNotEmpty()) {
                shoppingListRepository.deleteItems(checkedItems)
                println("deleteCheckedItems: ${checkedItems.size} markierte Items gelöscht.")
            }
        }
    }

    fun onShowAddItemDialogChanged(show: Boolean, item: ShoppingListItem?) {
        _uiState.update { it.copy(showAddItemDialog = show, itemToEdit = if (show) item else null) }
    }
}
