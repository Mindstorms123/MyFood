package com.example.myfood.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.FoodItem
import com.example.myfood.data.openfoodfacts.OFFProduct
// Importiere OFFSearchResponse, falls du es an anderer Stelle noch brauchst, hier nicht mehr direkt für 'result'
// import com.example.myfood.data.openfoodfacts.OFFSearchResponse
import com.example.myfood.data.openfoodfacts.OpenFoodFactsApiService
import com.example.myfood.data.pantry.PantryRepository
import com.example.myfood.data.shopping.ShoppingListItem
import com.example.myfood.data.shopping.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
            shoppingListRepository.getAllItems().collect { items ->
                _uiState.update { it.copy(items = items) }
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
                        _uiState.update { it.copy(searchResults = emptyList(), isLoadingSearch = false, errorSearching = null) }
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(isLoadingSearch = true, errorSearching = null) }
        searchJob = viewModelScope.launch {
            try {
                // KORREKTUR: Der Service liefert direkt Result<List<OFFProduct>>
                val result: Result<List<OFFProduct>> = openFoodFactsApiService.searchProducts(searchTerm = query)

                result.fold(
                    onSuccess = { productsList -> // productsList ist hier direkt vom Typ List<OFFProduct>
                        _uiState.update {
                            it.copy(searchResults = productsList, isLoadingSearch = false) // Verwende productsList direkt
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(searchResults = emptyList(), isLoadingSearch = false, errorSearching = "Fehlersuche: ${error.localizedMessage}")
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
            shoppingListRepository.insertItem(newItem)
            if (openFoodFactsId != null) {
                clearSearchResults()
            }
        }
    }

    fun addItemFromOffProduct(product: OFFProduct) {
        addItem(
            name = product.getDisplayName(),
            quantity = "1",
            unit = "Produkt",
            openFoodFactsId = product.id
        )
    }

    fun updateItem(item: ShoppingListItem) {
        viewModelScope.launch {
            shoppingListRepository.updateItem(item)
        }
    }

    fun deleteItem(item: ShoppingListItem) {
        viewModelScope.launch {
            shoppingListRepository.deleteItem(item)
        }
    }

    fun toggleItemChecked(item: ShoppingListItem) {
        viewModelScope.launch {
            val newCheckedState = !item.isChecked
            val updatedShoppingListItem = item.copy(isChecked = newCheckedState)
            shoppingListRepository.updateItem(updatedShoppingListItem)

            if (newCheckedState) {
                transferToPantry(updatedShoppingListItem)
            }
        }
    }

    private suspend fun transferToPantry(checkedShoppingItem: ShoppingListItem) {
        val quantityString = checkedShoppingItem.quantity
        val numericQuantity = quantityString.split(" ").firstOrNull()?.toIntOrNull() ?:
        quantityString.filter { it.isDigit() }.toIntOrNull() ?: 1

        var brandName: String? = null
        if (checkedShoppingItem.openFoodFactsProductId != null) {
            val originalOffProduct = _uiState.value.searchResults.find { it.id == checkedShoppingItem.openFoodFactsProductId }
            brandName = originalOffProduct?.brands
        }

        if (brandName == null && checkedShoppingItem.name.isNotEmpty()) {
            // Fallback Markenermittlung
        }

        val foodItemToPantry = FoodItem(
            name = checkedShoppingItem.name,
            brand = brandName,
            quantity = numericQuantity,
            openFoodFactsId = checkedShoppingItem.openFoodFactsProductId
        )

        val existingPantryItems = pantryRepository.getFoodItems()
        val existingItemInPantry = existingPantryItems.find { pantryItem ->
            (pantryItem.openFoodFactsId != null && pantryItem.openFoodFactsId == foodItemToPantry.openFoodFactsId) ||
                    (pantryItem.openFoodFactsId == null && foodItemToPantry.openFoodFactsId == null &&
                            pantryItem.name.equals(foodItemToPantry.name, ignoreCase = true) &&
                            ( (pantryItem.brand == null && foodItemToPantry.brand == null) ||
                                    (pantryItem.brand != null && pantryItem.brand.equals(foodItemToPantry.brand, ignoreCase = true)) )
                            )
        }

        if (existingItemInPantry != null) {
            val updatedPantryItem = existingItemInPantry.copy(
                quantity = existingItemInPantry.quantity + foodItemToPantry.quantity,
                name = foodItemToPantry.name,
                brand = foodItemToPantry.brand ?: existingItemInPantry.brand
            )
            pantryRepository.updateFoodItem(updatedPantryItem)
        } else {
            pantryRepository.addFoodItem(foodItemToPantry)
        }
    }

    fun deleteCheckedItems() {
        viewModelScope.launch {
            shoppingListRepository.deleteCheckedItems()
        }
    }

    fun onShowAddItemDialogChanged(show: Boolean) {
        _uiState.update { it.copy(showAddItemDialog = show, itemToEdit = null) }
    }

    fun onShowEditItemDialog(item: ShoppingListItem) {
        _uiState.update { it.copy(showAddItemDialog = true, itemToEdit = item) }
    }

    fun saveEditedItem(id: Int, name: String, quantity: String, unit: String) {
        viewModelScope.launch {
            val currentItemToEdit = _uiState.value.itemToEdit
            val itemToSave = currentItemToEdit?.copy(
                name = name,
                quantity = quantity,
                unit = unit
            ) ?: ShoppingListItem(id = 0, name = name, quantity = quantity, unit = unit)

            if (itemToSave.id != 0 && currentItemToEdit != null) {
                shoppingListRepository.updateItem(itemToSave)
            } else {
                shoppingListRepository.insertItem(itemToSave.copy(id = 0))
            }
            onShowAddItemDialogChanged(false)
        }
    }
}