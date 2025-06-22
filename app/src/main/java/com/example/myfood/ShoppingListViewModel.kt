package com.example.myfood.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.data.openfoodfacts.OFFProduct
import com.example.myfood.data.openfoodfacts.OpenFoodFactsApiService
import com.example.myfood.data.shopping.ShoppingListItem
import com.example.myfood.data.shopping.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel // Wenn du Hilt verwendest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hilfs-Datenklasse für den UI-Zustand
data class ShoppingListUiState(
    val items: List<ShoppingListItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<OFFProduct> = emptyList(),
    val isLoadingSearch: Boolean = false,
    val errorSearching: String? = null,
    val showAddItemDialog: Boolean = false, // Für ein manuelles Hinzufügen-Dialog
    val itemToEdit: ShoppingListItem? = null // Für Bearbeitungsdialog
)

@OptIn(FlowPreview::class) // Für debounce
@HiltViewModel // Wenn du Hilt verwendest
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingListRepository,
    private val openFoodFactsApiService: OpenFoodFactsApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadShoppingListItems()

        // Beobachte Änderungen an der searchQuery mit Debounce
        viewModelScope.launch {
            _uiState
                .map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(500L) // Warte 500ms nach der letzten Eingabe, bevor gesucht wird
                .collectLatest { query ->
                    if (query.length > 2) { // Suche erst ab 3 Zeichen
                        performSearch(query)
                    } else {
                        _uiState.update { it.copy(searchResults = emptyList(), isLoadingSearch = false, errorSearching = null) }
                    }
                }
        }
    }

    private fun loadShoppingListItems() {
        viewModelScope.launch {
            repository.getAllItems().collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel() // Breche vorherige Suche ab
        _uiState.update { it.copy(isLoadingSearch = true, errorSearching = null) }
        searchJob = viewModelScope.launch {
            val result = openFoodFactsApiService.searchProducts(searchTerm = query)
            result.fold(
                onSuccess = { products ->
                    _uiState.update {
                        it.copy(searchResults = products, isLoadingSearch = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(searchResults = emptyList(), isLoadingSearch = false, errorSearching = "Fehler: ${error.localizedMessage}")
                    }
                }
            )
        }
    }

    fun clearSearchResults() {
        _uiState.update { it.copy(searchResults = emptyList(), searchQuery = "", isLoadingSearch = false, errorSearching = null) }
    }

    fun addItem(name: String, quantity: String = "1", unit: String = "Stk.", openFoodFactsId: String? = null) {
        viewModelScope.launch {
            val newItem = ShoppingListItem(
                name = name,
                quantity = quantity,
                unit = unit,
                openFoodFactsProductId = openFoodFactsId
            )
            repository.insertItem(newItem)
            // Optional: Suchergebnisse leeren nach Hinzufügen
            if (openFoodFactsId != null) {
                clearSearchResults()
            }
        }
    }

    fun addItemFromOffProduct(product: OFFProduct) {
        addItem(
            name = product.getDisplayName(),
            quantity = product.brands ?: "1", // Beispiel: Marke als Menge, oder fest "1"
            unit = "Produkt", // oder spezifischer, falls die API es liefert
            openFoodFactsId = product.id
        )
    }

    fun updateItem(item: ShoppingListItem) {
        viewModelScope.launch {
            repository.updateItem(item)
        }
    }

    fun deleteItem(item: ShoppingListItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun toggleItemChecked(item: ShoppingListItem) {
        viewModelScope.launch {
            repository.updateItem(item.copy(isChecked = !item.isChecked))
        }
    }

    fun deleteCheckedItems() {
        viewModelScope.launch {
            repository.deleteCheckedItems()
        }
    }

    // --- Für Dialoge zum manuellen Hinzufügen/Bearbeiten ---
    fun onShowAddItemDialogChanged(show: Boolean) {
        _uiState.update { it.copy(showAddItemDialog = show, itemToEdit = null) }
    }

    fun onShowEditItemDialog(item: ShoppingListItem) {
        _uiState.update { it.copy(showAddItemDialog = true, itemToEdit = item) }
    }

    fun saveEditedItem(id: Int, name: String, quantity: String, unit: String) {
        viewModelScope.launch {
            val itemToSave = _uiState.value.itemToEdit?.copy(
                name = name,
                quantity = quantity,
                unit = unit
            ) ?: ShoppingListItem(id = 0, name = name, quantity = quantity, unit = unit) // Neues Element, wenn itemToEdit null ist

            if (itemToSave.id == 0 && _uiState.value.itemToEdit == null) { // Neues Item
                repository.insertItem(itemToSave)
            } else { // Bestehendes Item
                repository.updateItem(itemToSave)
            }
            onShowAddItemDialogChanged(false) // Dialog schließen
        }
    }
}