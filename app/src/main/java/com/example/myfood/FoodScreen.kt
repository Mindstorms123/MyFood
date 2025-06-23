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
// MLKit BarcodeScanner, expliziter Import um Doppeldeutigkeit zu vermeiden, falls andere Scanner Klassen existieren
import com.google.mlkit.vision.barcode.BarcodeScanner as MLKitBarcodeScanner
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
import com.example.myfood.data.openfoodfacts.OFFProduct // <--- KORREKTUR: Importiere OFFProduct
import kotlinx.serialization.Serializable // <--- KORREKTUR: Importiere Serializable für ProductResponse unten

// Ktor client Instanz
private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

// KORREKTUR: Diese ProductResponse ist spezifisch für den API-Endpunkt, den fetchProductApi hier verwendet.
// Sie muss OFFProduct für das 'product'-Feld verwenden.
@Serializable
data class PantryProductResponse( // Umbenannt, um Verwechslung mit globaler ProductResponse zu vermeiden, falls vorhanden
    val status: Int,
    @kotlinx.serialization.SerialName("product")
    val product: OFFProduct? = null // <--- KORREKTUR: Verwende OFFProduct
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FoodScreen(viewModel: FoodViewModel) {
    var addOrEditDialogOpen by remember { mutableStateOf(false) }
    var itemBeingEdited by remember { mutableStateOf<FoodItem?>(null) }
    var editingIndex by remember { mutableStateOf(-1) }

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
                modifier = Modifier.padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(onClick = {
                    itemBeingEdited = null
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = viewModel.foodItems, // Annahme: viewModel.foodItems ist StateFlow<List<FoodItem>> oder ähnliches
                key = { _, item -> item.id }
            ) { index, item ->
                val dismissState = rememberDismissState(
                    confirmStateChange = {
                        if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                            viewModel.removeItem(index) // viewModel.removeItem sollte den Index oder das Item selbst nehmen
                            true
                        } else false
                    }
                )
                SwipeToDismiss(
                    state = dismissState,
                    directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                    background = {
                        val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
                        val alignment = if (direction == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            contentAlignment = alignment
                        ) {
                            Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissContent = {
                        FoodItemEntry(
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
                    if (itemBeingEdited == null) {
                        viewModel.addManualItem(name, brand, quantity)
                    } else {
                        if(editingIndex != -1) {
                            // Annahme: diese Methoden existieren und aktualisieren das Item im ViewModel
                            viewModel.updateItemName(editingIndex, name)
                            viewModel.updateItemBrand(editingIndex, brand)
                            viewModel.updateItemQuantity(editingIndex, quantity)
                        }
                    }
                    addOrEditDialogOpen = false
                }
            )
        }

        if (showScanner) {
            ScannerView(
                onClose = { showScanner = false },
                onBarcodeScanned = { barcode ->
                    showScanner = false
                    coroutineScope.launch {
                        val productData: OFFProduct? = fetchProductApi(barcode) // <--- KORREKTUR: Typ ist OFFProduct?
                        if (productData != null) {
                            // Annahme: viewModel.addScannedProduct nimmt ein OFFProduct entgegen
                            viewModel.addScannedProduct(productData)
                        } else {
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
        onClick = onEditClick
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
                enabled = item.quantity > 0
            )
        }
    }
}

@Composable
fun QuantityControl(
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    enabled: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrease, enabled = quantity > 0 && enabled) {
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
                    val currentQuantity = quantityString.toIntOrNull() ?: (if (itemToEdit == null) 1 else 0)
                    if (name.isNotBlank()) {
                        onSave(name, brand, currentQuantity.coerceAtLeast(0))
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (itemToEdit == null) "Hinzufügen" else "Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun ScannerView(
    onClose: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    var hasScanned by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        onClose()
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
                        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_QR_CODE)
                        .build()
                    val mlKitScanner = BarcodeScanning.getClient(options)
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                if (!hasScanned) {
                                    processImageProxyForScan(mlKitScanner, imageProxy, onBarcodeScanned) { scanned ->
                                        if (scanned) hasScanned = true
                                    }
                                } else {
                                    imageProxy.close()
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
    scanner: MLKitBarcodeScanner, // KORREKTUR: Verwende den Alias für Klarheit
    imageProxy: ImageProxy,
    onBarcodeFound: (String) -> Unit,
    onScanAttemptComplete: (Boolean) -> Unit
) {
    imageProxy.image?.let { mediaImage ->
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    barcodes.firstNotNullOfOrNull { it.rawValue }?.let { barcodeValue ->
                        onBarcodeFound(barcodeValue)
                        onScanAttemptComplete(true)
                        return@addOnSuccessListener
                    }
                }
                onScanAttemptComplete(false)
            }
            .addOnFailureListener {
                it.printStackTrace()
                onScanAttemptComplete(false)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } ?: imageProxy.close()
}

// API-Abruffunktion
// KORREKTUR: Rückgabetyp ist jetzt OFFProduct?
suspend fun fetchProductApi(barcode: String): OFFProduct? = withContext(Dispatchers.IO) {
    try {
        // KORREKTUR: Die Antwort wird in PantryProductResponse deserialisiert, die OFFProduct enthält
        val response: PantryProductResponse =
            client.get("https://world.openfoodfacts.org/api/v0/product/$barcode.json").body()
        if (response.status == 1 && response.product != null) {
            response.product // Gibt das OFFProduct-Objekt zurück
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Internet Check Funktion
suspend fun checkInternetConnection(): Boolean = withContext(Dispatchers.IO) {
    try {
        HttpClient(CIO).use { clientCheck ->
            clientCheck.get("https://world.openfoodfacts.org").status.value in 200..299
        }
    } catch (e: Exception) {
        false
    }
}