package com.example.myfood.ui.shoppinglist

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration // WIEDER HINZUGEFÜGT
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.myfood.data.openfoodfacts.OFFProduct
import com.example.myfood.data.shopping.ShoppingListItem
import android.util.Log
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einkaufsliste") },windowInsets = WindowInsets(0.dp),
                actions = {
                    // Button, um ALLE markierten Items zu verarbeiten
                    if (uiState.items.any { it.isChecked }) {
                        IconButton(onClick = {
                            Log.d("ShoppingListScreen", "TopAppBar DoneAll geklickt - rufe transferCheckedItemsToPantryAndClear")
                            viewModel.transferCheckedItemsToPantryAndClear()
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Gekaufte in Vorrat übertragen & abhaken")
                        }
                        // Optional: Button, um alle markierten Items nur zu löschen (ohne Übertrag)
                        IconButton(onClick = {
                            Log.d("ShoppingListScreen", "TopAppBar DeleteSweep geklickt - rufe deleteCheckedItems")
                            viewModel.deleteCheckedItems()
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Markierte von Liste löschen")
                        }
                    }
                    IconButton(onClick = { viewModel.onShowAddItemDialogChanged(true, null) }) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = "Manuell hinzufügen")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
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
                        IconButton(onClick = { viewModel.clearSearchResultsAndQuery() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Suche leeren")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                })
            )

            if (uiState.isLoadingSearch) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.searchQuery.length > 2 && uiState.searchResults.isNotEmpty()) {
                Text("Suchergebnisse:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.searchResults, key = { product -> product.id }) { product ->
                        OffSearchResultItem(
                            product = product,
                            onAddClick = { viewModel.addItemFromOffProduct(product) }
                        )
                    }
                }
            } else if (uiState.searchQuery.length > 2 && uiState.errorSearching == null) {
                Text("Keine Produkte für '${uiState.searchQuery}' gefunden.", modifier = Modifier.padding(16.dp))
            } else if (uiState.errorSearching != null) {
                Text("Fehler bei der Suche: ${uiState.errorSearching}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            } else {
                if (uiState.items.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Deine Einkaufsliste ist leer.")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.items, key = { it.id }) { item ->
                            ShoppingListItemRow(
                                item = item,
                                // --- HIER DIE ÄNDERUNG ZURÜCK ---
                                // Ruft jetzt wieder die Funktion zum Umschalten des isChecked-Status auf.
                                onCheckedChange = {
                                    Log.d("ShoppingListScreen", "Checkbox für Item '${item.name}' (ID: ${item.id}) geklickt. Rufe toggleItemChecked auf.")
                                    viewModel.toggleItemChecked(item.id)
                                },
                                onEditClick = { viewModel.onShowAddItemDialogChanged(true, item) },
                                onDeleteClick = { viewModel.deleteItem(item.id) }
                            )
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                DividerDefaults.color
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showAddItemDialog) {
            AddItemDialog(
                itemToEdit = uiState.itemToEdit,
                onDismiss = { viewModel.onShowAddItemDialogChanged(false, null) },
                onConfirm = { id, name, brand, quantity, unit ->
                    viewModel.saveShoppingListItem(id, name, brand, quantity, unit)
                }
            )
        }
    }
}

@Composable
fun ShoppingListItemRow(
    item: ShoppingListItem,
    onCheckedChange: () -> Unit, // Löst jetzt wieder das Umschalten des Hakens aus
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // --- WIEDERHERGESTELLTE LOGIK FÜR DURCHSTREICHEN ---
    val textAlpha = if (item.isChecked) 0.6f else 1f
    val textDecoration = if (item.isChecked) TextDecoration.LineThrough else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange() } // Klick auf ganze Zeile ODER nur Checkbox ändert Status
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onCheckedChange() } // Löst onCheckedChange der Row aus (oder direkt VM-Aufruf)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = textDecoration), // WIEDER AKTIV
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha) // WIEDER AKTIV
            )
            item.brand?.takeIf { it.isNotBlank() }?.let { brandName ->
                Text(
                    text = brandName,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic, textDecoration = textDecoration), // WIEDER AKTIV
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha) // WIEDER AKTIV
                )
            }
            Text(
                text = "${item.quantity} ${item.unit}",
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = textDecoration), // WIEDER AKTIV
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha) // WIEDER AKTIV
            )
        }
        Spacer(Modifier.width(8.dp))
        // Bearbeiten-Button deaktivieren, wenn abgehakt, falls gewünscht
        IconButton(onClick = onEditClick, enabled = !item.isChecked) {
            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Löschen")
        }
    }
}

// OffSearchResultItem und AddItemDialog bleiben unverändert von deiner letzten Version.
// Stelle sicher, dass sie hier sind. Ich füge sie der Vollständigkeit halber ein.

@Composable
fun OffSearchResultItem(
    product: OFFProduct,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onAddClick() },
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
    onConfirm: (id: Int, name: String, brand: String?, quantity: String, unit: String) -> Unit
) {
    var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
    var brand by remember { mutableStateOf(itemToEdit?.brand ?: "") }
    var quantity by remember { mutableStateOf(itemToEdit?.quantity ?: "1") }
    var unit by remember { mutableStateOf(itemToEdit?.unit ?: "Stk.") }
    val isEditMode = itemToEdit != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Element bearbeiten" else "Neues Element hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isBlank() && isEditMode
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Hersteller (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { char -> char.isDigit() || char == ',' || char == '.' } },
                        label = { Text("Menge*") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Einheit*") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && quantity.isNotBlank() && unit.isNotBlank()) {
                        onConfirm(itemToEdit?.id ?: 0, name, brand.takeIf { it.isNotBlank() }, quantity, unit)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && quantity.isNotBlank() && unit.isNotBlank()
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
