package com.example.myfood.ui.shoppinglist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage // Für Bilder von Open Food Facts
import com.example.myfood.data.openfoodfacts.OFFProduct
import com.example.myfood.data.shopping.ShoppingListItem
import androidx.navigation.NavController


@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel = hiltViewModel(),
    // onNavigateToRecipeDetail: (String) -> Unit // Falls du von hier navigieren willst
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einkaufsliste") },
                actions = {
                    if (uiState.items.any { it.isChecked }) {
                        IconButton(onClick = { viewModel.deleteCheckedItems() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Gekaufte löschen")
                        }
                    }
                    IconButton(onClick = { viewModel.onShowAddItemDialogChanged(true) }) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = "Manuell hinzufügen")
                    }
                }
            )
        },
        floatingActionButton = {
            // Optional: FAB für schnelles manuelles Hinzufügen
            /*
            FloatingActionButton(onClick = { viewModel.onShowAddItemDialogChanged(true) }) {
                Icon(Icons.Filled.Add, "Neues Element")
            }
            */
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Suchleiste
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = { Text("Produkt suchen (Open Food Facts)...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearchResults() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Suche leeren")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    // Die Suche wird automatisch durch debounce ausgelöst
                })
            )

            // Suchergebnisse oder Einkaufsliste
            if (uiState.isLoadingSearch) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.searchQuery.length > 2 && uiState.searchResults.isNotEmpty()) {
                Text("Suchergebnisse:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.searchResults, key = { it.id }) { product ->
                        OffSearchResultItem(
                            product = product,
                            onAddClick = { viewModel.addItemFromOffProduct(product) }
                        )
                    }
                }
            } else if (uiState.searchQuery.length > 2 && uiState.searchResults.isEmpty() && !uiState.isLoadingSearch && uiState.errorSearching == null) {
                Text("Keine Produkte für '${uiState.searchQuery}' gefunden.", modifier = Modifier.padding(16.dp))
            } else if (uiState.errorSearching != null) {
                Text("Fehler bei der Suche: ${uiState.errorSearching}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            } else {
                // Einkaufsliste anzeigen
                if (uiState.items.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Deine Einkaufsliste ist leer.")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.items, key = { it.id }) { item ->
                            ShoppingListItemRow(
                                item = item,
                                onCheckedChange = { viewModel.toggleItemChecked(item) },
                                onEditClick = { viewModel.onShowEditItemDialog(item) },
                                onDeleteClick = { viewModel.deleteItem(item) }
                            )
                            Divider()
                        }
                    }
                }
            }
        }

        // Dialog zum Hinzufügen/Bearbeiten von Elementen
        if (uiState.showAddItemDialog) {
            AddItemDialog(
                itemToEdit = uiState.itemToEdit,
                onDismiss = { viewModel.onShowAddItemDialogChanged(false) },
                onConfirm = { id, name, quantity, unit ->
                    viewModel.saveEditedItem(id, name, quantity, unit)
                }
            )
        }
    }
}

@Composable
fun ShoppingListItemRow(
    item: ShoppingListItem,
    onCheckedChange: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onCheckedChange() }
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = if (item.isChecked) MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough)
                else MaterialTheme.typography.bodyLarge,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${item.quantity} ${item.unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Löschen")
        }
    }
}

@Composable
fun OffSearchResultItem(
    product: OFFProduct,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onAddClick() }, // Mache die ganze Karte klickbar
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = product.imageSmallUrl,
                contentDescription = product.getDisplayName(),
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(product.getDisplayName(), style = MaterialTheme.typography.titleMedium)
                product.brands?.let {
                    Text("Marke: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onAddClick) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Zur Liste hinzufügen")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    itemToEdit: ShoppingListItem?,
    onDismiss: () -> Unit,
    onConfirm: (id: Int, name: String, quantity: String, unit: String) -> Unit
) {
    var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
    var quantity by remember { mutableStateOf(itemToEdit?.quantity ?: "1") }
    var unit by remember { mutableStateOf(itemToEdit?.unit ?: "Stk.") }
    val isEditMode = itemToEdit != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Element bearbeiten" else "Neues Element hinzufügen") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Menge") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Einheit") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(itemToEdit?.id ?: 0, name, quantity, unit)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}