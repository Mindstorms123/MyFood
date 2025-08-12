package com.example.myfood.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.FoodItem // Stelle sicher, dass FoodItem auch 'brand: String?' hat und @Serializable ist
import com.example.myfood.data.model.Ingredient // ### NEUER IMPORT BENÖTIGT ###
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
                .debounce(500L) // Wartet 500ms nach der letzten Eingabe bevor die Suche startet
                .collectLatest { query ->
                    if (query.length > 2) { // Startet die Suche erst ab 3 Zeichen
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
        _uiState.update { it.copy(searchQuery = query, isLoadingSearch = query.length > 2 && query.isNotBlank()) }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel() // Bricht laufende Suchen ab
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
            } catch (e: Exception) { // Fängt Netzwerkfehler etc. ab
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
            id = 0, // 0 signalisiert ein neues Item
            name = product.getDisplayName(),
            brand = product.brands?.split(",")?.firstOrNull()?.trim(),
            quantity = "1", // Standardmenge
            unit = "Stück", // Standardeinheit, anpassbar
            openFoodFactsProductId = product.id
        )
        clearSearchResultsAndQuery() // Nach dem Hinzufügen Suchergebnisse leeren
    }

    fun saveShoppingListItem(
        id: Int,
        name: String,
        brand: String?,
        quantity: String,
        unit: String,
        openFoodFactsProductId: String? = null,
        recipeSource: String? = null // Optional: Herkunft aus Rezept
    ) {
        viewModelScope.launch {
            if (name.isBlank()) {
                // Hier könntest du einen Fehler im UI anzeigen oder einfach nichts tun
                println("saveShoppingListItem: Name darf nicht leer sein.")
                return@launch
            }

            val itemToSave: ShoppingListItem
            if (id != 0) { // Edit mode
                val existingItem = shoppingListRepository.getItemById(id)
                itemToSave = existingItem?.copy(
                    name = name,
                    brand = brand,
                    quantity = quantity,
                    unit = unit,
                    // openFoodFactsProductId und recipeSource bleiben erhalten, wenn schon gesetzt,
                    // es sei denn, sie werden explizit als neue Werte übergeben (hier nicht der Fall für recipeSource beim Editieren)
                ) ?: ShoppingListItem(
                    id = 0, // Neuer Eintrag, falls das Original nicht gefunden wurde (Fallback)
                    name = name,
                    brand = brand,
                    quantity = quantity,
                    unit = unit,
                    openFoodFactsProductId = openFoodFactsProductId,
                    recipeSource = recipeSource // Auch für den Fallback
                )
                if (existingItem != null) {
                    shoppingListRepository.updateItem(itemToSave)
                } else {
                    // Sollte idealerweise nicht passieren, wenn id != 0
                    shoppingListRepository.insertItem(itemToSave.copy(id = 0))
                }
            } else { // Add mode
                itemToSave = ShoppingListItem(
                    name = name,
                    brand = brand,
                    quantity = quantity,
                    unit = unit,
                    openFoodFactsProductId = openFoodFactsProductId,
                    recipeSource = recipeSource // Hinzufügen der Herkunft
                )
                shoppingListRepository.insertItem(itemToSave)
            }
            onShowAddItemDialogChanged(false, null) // Dialog schließen
        }
    }

    fun deleteItem(itemId: Int) {
        viewModelScope.launch {
            shoppingListRepository.deleteItemById(itemId)
        }
    }

    fun toggleItemChecked(itemId: Int) {
        viewModelScope.launch {
            val item = shoppingListRepository.getItemById(itemId)
            if (item == null) {
                println("toggleItemChecked: Item mit ID $itemId nicht gefunden.")
                return@launch
            }
            val updatedItem = item.copy(isChecked = !item.isChecked)
            shoppingListRepository.updateItem(updatedItem)
        }
    }

    fun transferCheckedItemsToPantryAndClear() {
        viewModelScope.launch {
            // Um sicherzustellen, dass der isChecked Status aktuell ist, direkt aus der DB laden
            val allItems = shoppingListRepository.getAllItems().first() // Holt den aktuellen Stand
            val checkedItems = allItems.filter { it.isChecked }

            if (checkedItems.isEmpty()) {
                println("transferCheckedItemsToPantryAndClear: Keine Items zum Übertragen ausgewählt.")
                return@launch
            }

            val itemsToTransfer = mutableListOf<FoodItem>()
            for (checkedShoppingItem in checkedItems) {
                val quantityString = checkedShoppingItem.quantity
                // Verbesserte Logik zur Extraktion der numerischen Menge
                val numericQuantity = quantityString.filter { it.isDigit() }.toIntOrNull() ?: 1


                val foodItemToPantry = FoodItem(
                    id = UUID.randomUUID().toString(),
                    name = checkedShoppingItem.name,
                    brand = checkedShoppingItem.brand,
                    quantity = numericQuantity,
                    openFoodFactsId = checkedShoppingItem.openFoodFactsProductId
                    // expiryDate, etc. könnten hier Standardwerte bekommen oder über einen Dialog abgefragt werden
                )
                itemsToTransfer.add(foodItemToPantry)
            }

            if (itemsToTransfer.isNotEmpty()) {
                try {
                    pantryRepository.addFoodItems(itemsToTransfer)
                    shoppingListRepository.deleteItems(checkedItems) // Lösche die übertragenen ShoppingListItems
                    println("transferCheckedItemsToPantryAndClear: ${itemsToTransfer.size} Items erfolgreich übertragen und von Einkaufsliste gelöscht.")
                } catch (e: Exception) {
                    println("transferCheckedItemsToPantryAndClear: FEHLER beim Übertragen oder Löschen: ${e.message}")
                    e.printStackTrace()
                    // Überlege dir eine Fehlerbehandlung für den User
                }
            }
        }
    }

    fun deleteCheckedItems() {
        viewModelScope.launch {
            val allItems = shoppingListRepository.getAllItems().first()
            val checkedItems = allItems.filter { it.isChecked }
            if (checkedItems.isNotEmpty()) {
                shoppingListRepository.deleteItems(checkedItems)
                println("deleteCheckedItems: ${checkedItems.size} markierte Items gelöscht.")
            }
        }
    }

    fun onShowAddItemDialogChanged(show: Boolean, item: ShoppingListItem?) {
        _uiState.update { it.copy(showAddItemDialog = show, itemToEdit = if (show) item else null) }
    }

    // ### NEUE FUNKTION HINZUGEFÜGT ###
    /**
     * Fügt eine Liste von Rezept-Zutaten zur Einkaufsliste hinzu.
     * @param ingredients Die Liste der hinzuzufügenden Zutaten vom Typ [Ingredient].
     * @param recipeName Der Name des Rezepts, aus dem die Zutaten stammen.
     */
    fun addIngredientsToShoppingList(ingredients: List<Ingredient>, recipeName: String) {
        viewModelScope.launch {
            val itemsToInsert = ingredients.map { ingredient ->
                ShoppingListItem(
                    // id wird von Room automatisch generiert, daher hier nicht explizit setzen (oder 0 lassen)
                    name = ingredient.name,
                    brand = null, // Marke ist bei Rezeptzutaten oft nicht spezifiziert
                    quantity = ingredient.quantity ?: "1", // Standardmenge, falls nicht vorhanden
                    unit = ingredient.unit ?: "Stk.", // Standardeinheit, falls nicht vorhanden
                    isChecked = false,
                    openFoodFactsProductId = null, // In der Regel nicht vorhanden für Rezeptzutaten
                    recipeSource = recipeName // Herkunft des Items
                )
            }
            if (itemsToInsert.isNotEmpty()) {
                shoppingListRepository.insertItems(itemsToInsert) // Repository-Methode zum Einfügen mehrerer Items
                println("addIngredientsToShoppingList: ${itemsToInsert.size} Zutaten von Rezept '$recipeName' zur Einkaufsliste hinzugefügt.")
            }
        }
    }
}

// Kleine Hilfsfunktion in OFFProduct, falls nicht schon global vorhanden
// Es ist besser, dies direkt in der OFFProduct-Klasse oder als Top-Level Extension zu haben.
// fun OFFProduct.getDisplayName(): String = this.productName ?: this.genericName ?: "Unbekanntes Produkt"

