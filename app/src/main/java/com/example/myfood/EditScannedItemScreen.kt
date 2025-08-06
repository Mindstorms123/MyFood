package com.example.myfood.ui

import android.app.Application // Für Preview
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // Für Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myfood.FoodItem
import com.example.myfood.FoodViewModel
// Stelle sicher, dass diese Extension-Funktion entweder hier oder in FoodViewModel.kt ist,
// oder dass deine OFFProduct-Klasse bereits eine ähnliche Methode hat.
import com.example.myfood.getDisplayName
import com.example.myfood.data.openfoodfacts.OFFProduct
import java.time.Instant // Import für Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// EditMode enum bleibt gleich
enum class EditMode {
    EXISTING_ITEM,
    NEW_SCANNED_ITEM,
    NONE
}

@RequiresApi(Build.VERSION_CODES.O) // Erforderlich für java.time Klassen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    foodViewModel: FoodViewModel,
    onItemSaved: () -> Unit
) {
    val itemToEditFromVm: FoodItem? by foodViewModel.itemToEdit.collectAsState()
    val scannedProductFromVm: OFFProduct? by foodViewModel.scannedProductForEditing.collectAsState()

    // NEU: Beim Verlassen des Screens die Bearbeitungs-States im ViewModel zurücksetzen
    // Dieser DisposableEffect wird ausgeführt, wenn EditItemScreen aus der Komposition entfernt wird.
    DisposableEffect(Unit) {
        onDispose {
            foodViewModel.clearEditingStates() // Stelle sicher, dass diese Funktion im FoodViewModel existiert
        }
    }

    val currentMode = remember(itemToEditFromVm, scannedProductFromVm) {
        when {
            itemToEditFromVm != null -> EditMode.EXISTING_ITEM
            scannedProductFromVm != null -> EditMode.NEW_SCANNED_ITEM
            else -> EditMode.NONE
        }
    }

    var name by remember(currentMode, itemToEditFromVm, scannedProductFromVm) {
        mutableStateOf(
            when (currentMode) {
                EditMode.EXISTING_ITEM -> itemToEditFromVm?.name ?: ""
                EditMode.NEW_SCANNED_ITEM -> scannedProductFromVm?.getDisplayName() ?: ""
                else -> ""
            }
        )
    }
    var brand by remember(currentMode, itemToEditFromVm, scannedProductFromVm) {
        mutableStateOf(
            when (currentMode) {
                EditMode.EXISTING_ITEM -> itemToEditFromVm?.brand ?: ""
                EditMode.NEW_SCANNED_ITEM -> scannedProductFromVm?.brands?.takeIf { it.isNotBlank() } ?: ""
                else -> ""
            }
        )
    }
    var quantityText by remember(currentMode, itemToEditFromVm, scannedProductFromVm) {
        mutableStateOf(
            when (currentMode) {
                EditMode.EXISTING_ITEM -> itemToEditFromVm?.quantity?.toString() ?: "1"
                EditMode.NEW_SCANNED_ITEM -> "1"
                else -> "1"
            }
        )
    }
    var expiryDate: LocalDate? by remember(currentMode, itemToEditFromVm) {
        mutableStateOf(
            if (currentMode == EditMode.EXISTING_ITEM) itemToEditFromVm?.expiryDate else null
        )
    }

    val showDatePickerDialog = remember { mutableStateOf(false) }

    if (currentMode == EditMode.NONE) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Kein Produkt zum Bearbeiten geladen.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    // onItemSaved() hier aufzurufen, könnte zu einer Schleife führen,
                    // wenn der Aufrufer direkt wieder hierher navigiert, weil die States noch nicht klar sind.
                    // Besser ist es, wenn der FoodScreen oder die Navigationslogik
                    // entscheidet, was passiert, wenn EditItemScreen im NONE-Modus ist
                    // und eigentlich nicht hätte aufgerufen werden sollen.
                    // Fürs Erste ist der Zurück-Button aber eine sichere Option.
                    onItemSaved() // Navigiert typischerweise zurück
                }) { Text("Zurück") }
            }
        }
        return
    }

    val topBarTitle = when (currentMode) {
        EditMode.EXISTING_ITEM -> "Produkt bearbeiten"
        EditMode.NEW_SCANNED_ITEM -> "Gescannte Details anpassen"
        else -> "Details" // Sollte nicht erreicht werden, wenn NONE-Mode oben behandelt wird
    }

    if (showDatePickerDialog.value) {
        val currentInitialMillis: Long = expiryDate?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()?.toEpochMilli()
            ?: System.currentTimeMillis()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentInitialMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog.value = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedInstant = Instant.ofEpochMilli(millis)
                        expiryDate = selectedInstant.atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePickerDialog.value = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog.value = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(topBarTitle) }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Produktname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // enabled bleibt so, wie es war, da es von currentMode abhängt
                enabled = currentMode != EditMode.NONE
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Marke (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = currentMode != EditMode.NONE
            )

            OutlinedTextField(
                value = quantityText,
                onValueChange = { newQuantityText ->
                    quantityText = newQuantityText.filter { it.isDigit() }.take(3)
                },
                label = { Text("Menge") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = currentMode != EditMode.NONE
            )

            OutlinedTextField(
                value = expiryDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: "Nicht gesetzt",
                onValueChange = { /* Read-only */ },
                label = { Text("Mindesthaltbarkeitsdatum") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = currentMode != EditMode.NONE) {
                        if (currentMode != EditMode.NONE) showDatePickerDialog.value = true
                    },
                readOnly = true,
                trailingIcon = {
                    IconButton(
                        onClick = { showDatePickerDialog.value = true },
                        enabled = currentMode != EditMode.NONE
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Kalender öffnen")
                    }
                },
                enabled = currentMode != EditMode.NONE
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val finalQuantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    if (name.isNotBlank()) {
                        when (currentMode) {
                            EditMode.EXISTING_ITEM -> {
                                itemToEditFromVm?.id?.let {
                                    foodViewModel.updateExistingFoodItem(
                                        itemId = it,
                                        newName = name,
                                        newBrand = brand.takeIf { b -> b.isNotBlank() },
                                        newQuantity = finalQuantity,
                                        newExpiryDate = expiryDate
                                    )
                                }
                            }
                            EditMode.NEW_SCANNED_ITEM -> {
                                scannedProductFromVm?.id?.let { // ID für OFFProduct ist der Barcode
                                    foodViewModel.confirmAndAddEditedScannedItem(
                                        originalProductId = it, // Verwende die ID/Barcode des gescannten Produkts
                                        name = name,
                                        brand = brand.takeIf { b -> b.isNotBlank() },
                                        quantity = finalQuantity,
                                        expiryDate = expiryDate
                                    )
                                }
                            }
                            EditMode.NONE -> { /* Sollte nicht passieren, wenn oben behandelt */ }
                        }
                        onItemSaved() // Navigiert zurück nach dem Speichern
                    } else {
                        // Optional: Zeige eine Meldung, dass der Produktname nicht leer sein darf
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && currentMode != EditMode.NONE
            ) {
                Text(
                    if (currentMode == EditMode.EXISTING_ITEM) "Änderungen speichern"
                    else "Speichern und zur Liste hinzufügen"
                )
            }
        }
    }
}


// --- Previews ---
// Die Previews sollten weiterhin funktionieren, da sie das ViewModel und seine Methoden
// direkt aufrufen, um die benötigten Zustände zu setzen.

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, name = "Edit Existing Item Preview")
@Composable
fun PreviewEditExistingItemScreen() {
    val app = LocalContext.current.applicationContext as Application
    val mockFoodViewModel = remember { FoodViewModel(app) }

    LaunchedEffect(Unit) {
        mockFoodViewModel.setItemToEdit(
            FoodItem(
                id = "prev1",
                name = "Vorschau Produkt Alt",
                brand = "Vorschau Marke Alt",
                quantity = 2,
                expiryDate = LocalDate.now().plusDays(10)
            )
        )
    }
    MaterialTheme {
        EditItemScreen(
            foodViewModel = mockFoodViewModel,
            onItemSaved = {
                // Im Preview den State auch zurücksetzen, um das Verhalten zu simulieren
                mockFoodViewModel.clearEditingStates()
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, name = "Edit New Scanned Item Preview")
@Composable
fun PreviewEditNewScannedItemScreen() {
    val app = LocalContext.current.applicationContext as Application
    val mockFoodViewModel = remember { FoodViewModel(app) }

    LaunchedEffect(Unit) {
        mockFoodViewModel.setScannedProductForEditing(
            OFFProduct(id = "scan123", productName = "Gescanntes Produkt Vorschau", brands = "ScanMarke")
        )
    }
    MaterialTheme {
        EditItemScreen(
            foodViewModel = mockFoodViewModel,
            onItemSaved = {
                // Im Preview den State auch zurücksetzen
                mockFoodViewModel.clearEditingStates()
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, name = "Edit Screen in NONE mode")
@Composable
fun PreviewEditNoneModeScreen() {
    val app = LocalContext.current.applicationContext as Application
    val mockFoodViewModel = remember { FoodViewModel(app) }
    // Für den NONE-Mode-Preview stellen wir sicher, dass die States initial null sind.
    // Ein expliziter Aufruf von clearEditingStates() ist hier nicht unbedingt nötig,
    // da ein neues ViewModel-Instanz initial so sein sollte, aber schadet auch nicht.
    LaunchedEffect(Unit) {
        mockFoodViewModel.clearEditingStates() // Stellt sicher, dass beide null sind
    }
    MaterialTheme {
        EditItemScreen(
            foodViewModel = mockFoodViewModel,
            onItemSaved = { }
        )
    }
}
