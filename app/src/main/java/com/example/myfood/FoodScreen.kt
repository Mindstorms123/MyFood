package com.example.myfood

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log // Import für Logging
import android.widget.DatePicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.example.myfood.data.openfoodfacts.OFFProduct
import com.example.myfood.navigation.Screen
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import com.google.mlkit.vision.barcode.BarcodeScanner as MLKitBarcodeScanner

// Ktor client Instanz
private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

@Serializable
data class PantryProductResponse(
    val status: Int,
    @kotlinx.serialization.SerialName("product")
    val product: OFFProduct? = null
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FoodScreen(
    navController: NavController,
    viewModel: FoodViewModel
) {
    var showAddDialogWithExpiry by remember { mutableStateOf(false) }
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
        } else {
            Log.w("FoodScreen", "Kamera-Berechtigung nicht erteilt.")
            // Hier könntest du dem Nutzer Feedback geben, dass die Funktion ohne Berechtigung nicht nutzbar ist.
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
                    showAddDialogWithExpiry = true
                }) { Icon(Icons.Default.Add, "Neues Lebensmittel") }

                FloatingActionButton(onClick = {
                    if (cameraPermissionGranted) {
                        Log.d("FoodScreen", "Kamera FAB: Berechtigung vorhanden, zeige Scanner.")
                        showScanner = true
                    } else {
                        Log.d("FoodScreen", "Kamera FAB: Berechtigung fehlt, frage an.")
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
                items = viewModel.foodItems,
                key = { _, item -> item.id }
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
                                Log.d("FoodScreen", "onEditClick: Item ID ${item.id}, Name: ${item.name}")
                                // 1. Setze NUR das zu bearbeitende Item im ViewModel.
                                // Die setItemToEdit-Methode im ViewModel sollte intern
                                // scannedProductForEditing auf null setzen.
                                viewModel.setItemToEdit(item)
                                // 2. Navigiere zum EditScreen.
                                navController.navigate(Screen.EditItemScreen.route)
                            },
                            onQuantityIncrease = { viewModel.updateItemQuantity(index, item.quantity + 1) },
                            onQuantityDecrease = { viewModel.updateItemQuantity(index, item.quantity - 1) }
                        )
                    }
                )
            }
        }

        if (showAddDialogWithExpiry) {
            AddFoodDialogWithExpiry(
                onDismiss = { showAddDialogWithExpiry = false },
                onSave = { name, brand, quantity, expiryDate ->
                    viewModel.addManualItem(name, brand, quantity, expiryDate)
                    showAddDialogWithExpiry = false
                }
            )
        }

        if (showScanner) {
            ScannerView(
                onClose = {
                    Log.d("FoodScreen", "ScannerView: onClose aufgerufen.")
                    showScanner = false
                },
                onBarcodeScanned = { barcode ->
                    showScanner = false // Scanner sofort schließen
                    Log.d("FoodScreen", "ScannerView: Barcode gescannt: $barcode")
                    coroutineScope.launch {
                        val productData: OFFProduct? = fetchProductApi(barcode)
                        if (productData != null) {
                            Log.d("FoodScreen", "Produkt für Barcode '$barcode' gefunden: ${productData.getDisplayName()}")
                            // 1. Setze NUR das gescannte Produkt im ViewModel.
                            // Die setScannedProductForEditing-Methode im ViewModel sollte intern
                            // itemToEdit auf null setzen.
                            viewModel.setScannedProductForEditing(productData)
                            // 2. Navigiere zum EditScreen.
                            navController.navigate(Screen.EditItemScreen.route)
                        } else {
                            Log.w("FoodScreen", "Produkt für Barcode '$barcode' nicht gefunden. Füge manuell hinzu.")
                            // Produkt nicht gefunden: Füge ein einfaches Item hinzu oder zeige eine Meldung.
                            viewModel.addManualItem("Produkt nicht gefunden: $barcode", quantity = 1, expiryDate = null)
                            // Hier könntest du auch einen Toast oder eine Snackbar anzeigen.
                        }
                    }
                },
                lifecycleOwner = lifecycleOwner
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FoodItemEntry(
    item: FoodItem,
    onEditClick: () -> Unit,
    onQuantityIncrease: () -> Unit,
    onQuantityDecrease: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onEditClick // Die gesamte Karte ist klickbar für Bearbeiten
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                item.brand?.takeIf { it.isNotBlank() }?.let { // Zeige Marke nur, wenn nicht leer
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.expiryDate?.let {
                    Text("MHD: ${it.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            QuantityControl(
                quantity = item.quantity,
                onIncrease = onQuantityIncrease,
                onDecrease = onQuantityDecrease,
                enabled = item.quantity > 0 // Verringern nur möglich, wenn Menge > 0
            )
        }
    }
}

@Composable
fun QuantityControl(
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    enabled: Boolean = true // Gesamtsteuerung für beide Buttons
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrease, enabled = quantity > 0 && enabled) { // Verringern nur möglich, wenn Menge > 0
            Icon(Icons.Filled.RemoveCircle, "Menge verringern")
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = onIncrease, enabled = enabled) {
            Icon(Icons.Filled.AddCircle, "Menge erhöhen")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodDialogWithExpiry(
    onDismiss: () -> Unit,
    onSave: (name: String, brand: String?, quantity: Int, expiryDate: LocalDate?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var quantityString by remember { mutableStateOf("1") }
    var expiryDate by remember { mutableStateOf<LocalDate?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    expiryDate?.let {
        calendar.set(it.year, it.monthValue - 1, it.dayOfMonth)
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            expiryDate = LocalDate.of(year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Lebensmittel hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Produktname*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError
                )
                if (nameError) {
                    Text("Produktname darf nicht leer sein.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
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
                        quantityString = newValue.filter { it.isDigit() }.take(3)
                        quantityError = (quantityString.toIntOrNull() ?: -1) < 0
                    },
                    label = { Text("Menge*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = quantityError
                )
                if (quantityError) {
                    Text("Menge muss eine positive Zahl sein.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = expiryDate?.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) ?: "Kein MHD",
                    onValueChange = { /* Feld ist nicht direkt editierbar */ },
                    label = { Text("Mindesthaltbarkeitsdatum") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() },
                    readOnly = true,
                    trailingIcon = {
                        Row {
                            if (expiryDate != null) {
                                IconButton(onClick = { expiryDate = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "MHD entfernen")
                                }
                            }
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Datum auswählen")
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    nameError = name.isBlank()
                    quantityError = (quantityString.toIntOrNull() ?: -1) < 0
                    if (!nameError && !quantityError) {
                        onSave(name, brand.ifBlank { null }, quantityString.toIntOrNull() ?: 1, expiryDate)
                    }
                },
                // enabled wird durch Fehlerprüfung oben gesteuert oder man kann es hier nochmal explizit machen
            ) {
                Text("Hinzufügen")
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
    var hasScanned by remember { mutableStateOf(false) } // Verhindert mehrfaches Scannen

    // Schließe den Scanner, wenn die Zurück-Taste gedrückt wird
    BackHandler(enabled = true) {
        Log.d("ScannerView", "BackHandler aufgerufen, schließe Scanner.")
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
                                if (!hasScanned) { // Nur verarbeiten, wenn noch nicht gescannt wurde
                                    processImageProxyForScan(mlKitScanner, imageProxy,
                                        onBarcodeFound = { barcodeValue ->
                                            if (!hasScanned) { // Doppelte Prüfung zur Sicherheit
                                                hasScanned = true
                                                Log.d("ScannerView", "Barcode gefunden im Analyzer: $barcodeValue")
                                                onBarcodeScanned(barcodeValue)
                                                // Der Scanner wird jetzt im FoodScreen geschlossen, nachdem navigiert wurde.
                                                // cameraProvider.unbindAll() // Optional: Kamera hier stoppen
                                            }
                                        },
                                        onScanAttemptComplete = { /* Nichts zu tun hier, da hasScanned es steuert */ }
                                    )
                                } else {
                                    imageProxy.close() // Schließe Proxy, wenn schon gescannt
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll() // Wichtig, um vorherige Bindungen zu lösen
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                        Log.d("ScannerView", "Kamera an Lifecycle gebunden.")
                    } catch (exc: Exception) {
                        Log.e("ScannerView", "Fehler beim Binden der Kamera an Lifecycle", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = {
                Log.d("ScannerView", "Schließen-Button geklickt.")
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, "Scanner schließen", tint = MaterialTheme.colorScheme.onSurface) // Icon geändert zu Close
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxyForScan(
    scanner: MLKitBarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeFound: (String) -> Unit,
    onScanAttemptComplete: (Boolean) -> Unit // Wird hier nicht mehr direkt benötigt, aber die Signatur bleibt
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    barcodes.firstNotNullOfOrNull { it.rawValue }?.let { barcodeValue ->
                        onBarcodeFound(barcodeValue) // Ruft onBarcodeFound auf, was hasScanned setzt
                        // onScanAttemptComplete(true) nicht mehr hier, da hasScanned die Logik steuert
                        return@addOnSuccessListener
                    }
                }
                // onScanAttemptComplete(false) nicht mehr hier
            }
            .addOnFailureListener { e ->
                Log.e("ScannerView", "Fehler bei der Barcode-Erkennung", e)
                // onScanAttemptComplete(false) nicht mehr hier
            }
            .addOnCompleteListener {
                // WICHTIG: ImageProxy immer schließen, egal ob erfolgreich oder nicht
                imageProxy.close()
            }
    } else {
        // Wenn mediaImage null ist, trotzdem den Proxy schließen.
        imageProxy.close()
        // onScanAttemptComplete(false) nicht mehr hier
    }
}

// --- Hilfsfunktionen für API und Internet ---
suspend fun fetchProductApi(barcode: String): OFFProduct? = withContext(Dispatchers.IO) {
    try {
        Log.d("Network", "API-Aufruf für Barcode: $barcode")
        val response: PantryProductResponse =
            client.get("https://world.openfoodfacts.org/api/v0/product/$barcode.json").body()
        Log.d("Network", "API-Antwort Status: ${response.status} für Barcode: $barcode")
        if (response.status == 1 && response.product != null) {
            response.product
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("Network", "API-Fehler für Barcode: $barcode", e)
        null
    }
}

// Die `checkInternetConnection` Funktion bleibt wie sie ist.
// `getDisplayName` Extension für OFFProduct sollte im FoodViewModel oder einer Utility-Datei sein.
// fun OFFProduct.getDisplayName(): String = this.productName ?: this.genericName ?: this.name ?: "Produkt ohne Namen"

