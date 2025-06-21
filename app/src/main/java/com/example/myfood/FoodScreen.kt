package com.example.myfood

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AddCircle // Für Plus-Button
import androidx.compose.material.icons.filled.RemoveCircle // Für Minus-Button
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner // MLKit BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.lifecycle.LifecycleOwner
import androidx.activity.compose.BackHandler

// Ktor client Instanz
private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FoodScreen(viewModel: FoodViewModel) {
    var addOrEditDialogOpen by remember { mutableStateOf(false) }
    var itemBeingEdited by remember { mutableStateOf<FoodItem?>(null) }
    var editingIndex by remember { mutableStateOf(-1) } // Behalten für direkte Index-Updates

    var showScanner by remember { mutableStateOf(false) }
    var cameraPermissionGranted by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        if (granted) {
            showScanner = true
        }
    }

    LaunchedEffect(Unit) {
        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lebensmittelliste") }) },
        floatingActionButton = {
            Row(
                modifier = Modifier.padding(end = 16.dp), // Padding für FABs von Scaffold Kante
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(onClick = {
                    itemBeingEdited = null // Add-Modus
                    addOrEditDialogOpen = true
                }) { Icon(Icons.Default.Add, "Neues Lebensmittel") }

                FloatingActionButton(onClick = {
                    if (cameraPermissionGranted) {
                        showScanner = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) { Icon(Icons.Default.CameraAlt, "Barcode scannen") }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp), // Innenpadding für die Liste
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = viewModel.foodItems,
                key = { _, item -> item.id } // Eindeutige ID als Key
            ) { index, item ->
                val dismissState = rememberDismissState(
                    confirmStateChange = {
                        if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                            viewModel.removeItem(index)
                            true
                        } else false
                    }
                )
                SwipeToDismiss(
                    state = dismissState,
                    directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                    background = { /* ... (Lösch-Icon Hintergrund bleibt gleich) ... */
                        val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
                        val alignment = if (direction == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp), // Padding für das Icon
                            contentAlignment = alignment
                        ) {
                            Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissContent = {
                        FoodItemEntry( // Verwende eine neue Composable für den Eintrag
                            item = item,
                            onEditClick = {
                                itemBeingEdited = item
                                editingIndex = index
                                addOrEditDialogOpen = true
                            },
                            onQuantityIncrease = { viewModel.updateItemQuantity(index, item.quantity + 1) },
                            onQuantityDecrease = { viewModel.updateItemQuantity(index, item.quantity - 1) }
                        )
                    }
                )
            }
        }

        if (addOrEditDialogOpen) {
            AddEditFoodDialog(
                itemToEdit = itemBeingEdited,
                onDismiss = { addOrEditDialogOpen = false },
                onSave = { name, brand, quantity ->
                    if (itemBeingEdited == null) { // Add-Modus
                        viewModel.addManualItem(name, brand, quantity)
                    } else { // Edit-Modus
                        if(editingIndex != -1) {
                            viewModel.updateItemName(editingIndex, name)
                            viewModel.updateItemBrand(editingIndex, brand) // Stelle sicher, dass diese Methode im VM existiert
                            viewModel.updateItemQuantity(editingIndex, quantity)
                        }
                    }
                    addOrEditDialogOpen = false
                }
            )
        }

        if (showScanner) {
            ScannerView( // Umbenannt von BarcodeScanner zu ScannerView, um Verwechslung mit MLKit zu vermeiden
                onClose = { showScanner = false },
                onBarcodeScanned = { barcode ->
                    showScanner = false // Scanner sofort schließen
                    coroutineScope.launch {
                        val productData = fetchProductApi(barcode) // API-Aufruf
                        if (productData != null) {
                            viewModel.addScannedProduct(productData)
                        } else {
                            // Optional: Zeige eine Snackbar oder füge ein "Nicht gefunden"-Item hinzu
                            viewModel.addManualItem("Produkt nicht gefunden: $barcode", quantity = 0)
                        }
                    }
                },
                lifecycleOwner = lifecycleOwner
            )
        }
    }
}

@Composable
fun FoodItemEntry(
    item: FoodItem,
    onEditClick: () -> Unit,
    onQuantityIncrease: () -> Unit,
    onQuantityDecrease: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onEditClick // Klick auf die Karte öffnet den Edit-Dialog
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                item.brand?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            QuantityControl(
                quantity = item.quantity,
                onIncrease = onQuantityIncrease,
                onDecrease = onQuantityDecrease,
                enabled = item.quantity > 0 // Minus-Button deaktivieren, wenn Menge 0 ist
            )
        }
    }
}

@Composable
fun QuantityControl(
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    enabled: Boolean = true // Um den Minus-Button bei Menge 0 zu deaktivieren
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrease, enabled = quantity > 0 && enabled) { // Minus-Button ist deaktiviert, wenn Menge 0 oder explizit deaktiviert
            Icon(Icons.Filled.RemoveCircle, "Menge verringern")
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.AddCircle, "Menge erhöhen")
        }
    }
}


@Composable
fun AddEditFoodDialog(
    itemToEdit: FoodItem?,
    onDismiss: () -> Unit,
    onSave: (name: String, brand: String, quantity: Int) -> Unit
) {
    var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
    var brand by remember { mutableStateOf(itemToEdit?.brand ?: "") }
    var quantityString by remember(itemToEdit) { mutableStateOf((itemToEdit?.quantity ?: 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemToEdit == null) "Neues Lebensmittel" else "Lebensmittel bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Produktname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marke (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityString,
                    onValueChange = { newValue ->
                        // Erlaube nur Ziffern und leeren String (für vorübergehende Eingabe)
                        if (newValue.all { it.isDigit() }) {
                            quantityString = newValue
                        }
                    },
                    label = { Text("Menge") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val currentQuantity = quantityString.toIntOrNull() ?: (if (itemToEdit == null) 1 else 0) // Default 1 bei neu, 0 wenn Umwandlung fehlschlägt
                    if (name.isNotBlank()) {
                        onSave(name, brand, currentQuantity.coerceAtLeast(0))
                    }
                },
                enabled = name.isNotBlank() // Speichern nur möglich, wenn Name nicht leer ist
            ) {
                Text(if (itemToEdit == null) "Hinzufügen" else "Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// Barcode Scanner View (umbenannt, um Konflikt mit MLKit Scanner zu vermeiden)
@Composable
fun ScannerView(
    onClose: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    var hasScanned by remember { mutableStateOf(false) } // Verhindert mehrfaches Scannen

    // NEU: BackHandler, um das Drücken der Zurück-Taste abzufangen
    BackHandler(enabled = true) { // enabled = true, da der Handler aktiv sein soll, solange der Scanner sichtbar ist
        onClose() // Ruft die onClose-Lambda auf, die showScanner auf false setzt
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_QR_CODE) // Gängige Formate
                        .build()
                    val mlKitScanner = BarcodeScanning.getClient(options) // MLKit Instanz
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                if (!hasScanned) { // Nur analysieren, wenn noch nicht gescannt
                                    processImageProxyForScan(mlKitScanner, imageProxy, onBarcodeScanned) { scanned ->
                                        if (scanned) hasScanned = true // Setze Flag, wenn erfolgreich gescannt
                                    }
                                } else {
                                    imageProxy.close() // Schließe, wenn schon gescannt
                                }
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Delete, "Scanner schließen", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxyForScan(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner, // Explizit MLKit Scanner
    imageProxy: ImageProxy,
    onBarcodeFound: (String) -> Unit,
    onScanAttemptComplete: (Boolean) -> Unit // Callback, ob ein Barcode gefunden wurde
) {
    imageProxy.image?.let { mediaImage ->
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    barcodes.firstNotNullOfOrNull { it.rawValue }?.let { barcodeValue ->
                        onBarcodeFound(barcodeValue)
                        onScanAttemptComplete(true) // Barcode gefunden
                        return@addOnSuccessListener
                    }
                }
                onScanAttemptComplete(false) // Kein Barcode gefunden
            }
            .addOnFailureListener {
                it.printStackTrace()
                onScanAttemptComplete(false) // Fehler
            }
            .addOnCompleteListener {
                imageProxy.close() // Wichtig: ImageProxy immer schließen
            }
    } ?: imageProxy.close() // Schließen, wenn mediaImage null ist
}

// API-Abruffunktion
suspend fun fetchProductApi(barcode: String): ProductFromAPI? = withContext(Dispatchers.IO) {
    try {
        val response: ProductResponse = // Verwendet die ProductResponse aus FoodItem.kt
            client.get("https://world.openfoodfacts.org/api/v0/product/$barcode.json").body()
        if (response.status == 1 && response.product != null) {
            response.product // Gibt das ProductFromAPI-Objekt zurück
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Internet Check Funktion (optional, aber gut zu haben)
suspend fun checkInternetConnection(): Boolean = withContext(Dispatchers.IO) {
    try {
        HttpClient(CIO).use { clientCheck -> // .use stellt sicher, dass der Client geschlossen wird
            clientCheck.get("https://world.openfoodfacts.org").status.value in 200..299
        }
    } catch (e: Exception) {
        false
    }
}
