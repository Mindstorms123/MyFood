package com.example.myfood

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
import androidx.compose.foundation.lazy.items // Geändert von itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
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
import com.example.myfood.data.openfoodfacts.OFFProduct // Sicherstellen, dass dies der korrekte Pfad ist
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
import java.time.format.FormatStyle
import java.util.Calendar
import com.google.mlkit.vision.barcode.BarcodeScanner as MLKitBarcodeScanner

// Ktor client Instanz
private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true // Hilfreich für Debugging
        })
    }
}

// Die ProductResponse wird hier für die API-Antwort von OpenFoodFacts verwendet
// Deine FoodItem-Klasse ist separat und wird für die Speicherung/Anzeige in der App genutzt.
@Serializable
data class OpenFoodFactsApiResponse( // Umbenannt zur Klarheit, dass es sich um die API-Antwort handelt
    val status: Int,
    @kotlinx.serialization.SerialName("product")
    val product: OFFProduct? = null,
    val code: String? = null // Barcode, der gescannt wurde
)


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

    // Beobachte den StateFlow aus dem ViewModel
    val foodItems by viewModel.foodItemsStateFlow.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        if (granted) {
            showScanner = true
        } else {
            Log.w("FoodScreen", "Kamera-Berechtigung nicht erteilt.")
            // Hier Feedback geben
        }
    }

    LaunchedEffect(Unit) {
        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lebensmittelliste") }, windowInsets = WindowInsets(0.dp)) },
        floatingActionButton = {
            Row(
                modifier = Modifier.padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(onClick = {
                    viewModel.clearEditingStates() // Stelle sicher, dass kein alter Bearbeitungsstatus vorhanden ist
                    showAddDialogWithExpiry = true
                }) { Icon(Icons.Default.Add, "Neues Lebensmittel") }

                FloatingActionButton(onClick = {
                    if (cameraPermissionGranted) {
                        Log.d("FoodScreen", "Kamera FAB: Berechtigung vorhanden, zeige Scanner.")
                        viewModel.clearEditingStates() // Stelle sicher, dass kein alter Bearbeitungsstatus vorhanden ist
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
            items(
                items = foodItems,
                key = { item -> item.id } // Stabile ID für jedes Item
            ) { item ->
                FoodItemEntry(
                    item = item,
                    onEditClick = {
                        Log.d("FoodScreen", "onEditClick: Item ID ${item.id}, Name: ${item.name}")
                        viewModel.setItemToEdit(item)
                        navController.navigate(Screen.EditFoodItem.route)
                    },
                    onQuantityIncrease = {
                        viewModel.updateItemQuantityById(item.id, item.quantity + 1)
                    },
                    onQuantityDecrease = {
                        if (item.quantity > 0) { // Verhindere negative Mengen direkt hier oder im VM
                            viewModel.updateItemQuantityById(item.id, item.quantity - 1)
                        }
                    },
                    onDeleteClick = {
                        viewModel.removeItemById(item.id)
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
                    showScanner = false
                    Log.d("FoodScreen", "ScannerView: Barcode gescannt: $barcode")
                    coroutineScope.launch {
                        val productData: OFFProduct? = fetchProductApi(barcode)
                        if (productData != null) {
                            Log.d("FoodScreen", "Produkt für Barcode '$barcode' gefunden: ${productData.getDisplayName()}")
                            viewModel.setScannedProductForEditing(productData)
                            navController.navigate(Screen.EditFoodItem.route)
                        } else {
                            Log.w("FoodScreen", "Produkt für Barcode '$barcode' nicht gefunden. Option zum manuellen Hinzufügen anbieten oder direkt zum EditScreen mit Barcode.")
                            // Option 1: Direkt zum EditScreen mit dem Barcode als Teil des Namensvorschlags
                            viewModel.setScannedProductForEditing(OFFProduct(id = barcode, productName = "Neues Produkt ($barcode)"))
                            navController.navigate(Screen.EditFoodItem.route)
                            // Option 2: Einfaches Item hinzufügen (wie zuvor)
                            // viewModel.addManualItem("Produkt nicht gefunden: $barcode", quantity = 1, expiryDate = null)
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
    onQuantityDecrease: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                item.brand?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.expiryDate?.let {
                    Text("MHD: ${it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            QuantityControl(
                quantity = item.quantity,
                onIncrease = onQuantityIncrease,
                onDecrease = onQuantityDecrease,
                enabled = item.quantity >= 0 // Button zum Verringern nur aktiv, wenn Menge > 0
            )
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
            }
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
        IconButton(onClick = onDecrease, enabled = quantity > 0 && enabled) { // Sicherstellen, dass Menge > 0 ist
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
                        val currentQuantity = quantityString.toIntOrNull()
                        quantityError = currentQuantity == null || currentQuantity < 0
                    },
                    label = { Text("Menge*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = quantityError
                )
                if (quantityError) {
                    Text("Menge muss eine nicht-negative Zahl sein.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = expiryDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: "Kein MHD",
                    onValueChange = { /* ReadOnly */ },
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
                    val currentQuantity = quantityString.toIntOrNull()
                    quantityError = currentQuantity == null || currentQuantity < 0

                    if (!nameError && !quantityError) {
                        onSave(name, brand.takeIf { it.isNotBlank() }, currentQuantity ?: 1, expiryDate)
                    }
                }
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
    var hasScanned by remember { mutableStateOf(false) }

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
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128, // Ggf. weitere Formate hinzufügen
                            Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93
                        )
                        .build()
                    val mlKitScanner = BarcodeScanning.getClient(options)

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                if (!hasScanned) {
                                    processImageProxyForScan(mlKitScanner, imageProxy,
                                        onBarcodeFound = { barcodeValue ->
                                            if (!hasScanned) { // Doppelte Prüfung zur Sicherheit
                                                hasScanned = true
                                                Log.d("ScannerView", "Barcode gefunden im Analyzer: $barcodeValue")
                                                onBarcodeScanned(barcodeValue)
                                            }
                                        }
                                    )
                                } else {
                                    imageProxy.close() // Schließe Proxy, wenn schon gescannt
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                        Log.d("ScannerView", "Kamera an Lifecycle gebunden.")
                    } catch (exc: Exception) {
                        Log.e("ScannerView", "Fehler beim Binden der Kamera an Lifecycle", exc)
                        onClose() // Bei Fehler Scanner schließen
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
            Icon(Icons.Filled.Close, "Scanner schließen", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxyForScan(
    scanner: MLKitBarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    // Nimm den ersten gefundenen Barcode-Wert, der nicht null oder leer ist
                    barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }?.let { barcodeValue ->
                        onBarcodeFound(barcodeValue)
                        // Es ist nicht nötig, hier explizit zu returnen, da onBarcodeFound hasScanned setzt.
                        // Der Analyzer wird beim nächsten Frame sowieso erneut aufgerufen und durch hasScanned gestoppt.
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ScannerView", "Fehler bei der Barcode-Erkennung", e)
            }
            .addOnCompleteListener {
                imageProxy.close() // WICHTIG: ImageProxy immer schließen
            }
    } else {
        imageProxy.close()
    }
}

// --- Hilfsfunktionen für API ---
suspend fun fetchProductApi(barcode: String): OFFProduct? = withContext(Dispatchers.IO) {
    try {
        Log.d("Network", "API-Aufruf für Barcode: $barcode")
        // Verwende OpenFoodFactsApiResponse für die Deserialisierung
        val response: OpenFoodFactsApiResponse =
            client.get("https://world.openfoodfacts.org/api/v2/product/$barcode") {
                // Du kannst hier weitere Parameter hinzufügen, z.B. fields, falls benötigt
                // parameter("fields", "product_name,brands,nutriments,image_url")
            }.body()

        Log.d("Network", "API-Antwort Status: ${response.status}, Barcode: $barcode, Produkt: ${response.product?.productName}")

        if (response.status == 1 && response.product != null) {
            // Stelle sicher, dass die Produkt-ID (Barcode) im OFFProduct-Objekt gesetzt ist, falls nicht von der API geliefert
            response.product.copy(id = response.product.id ?: response.code ?: barcode)
        } else if (response.status == 0 && response.code != null) {
            Log.w("Network", "Produkt nicht gefunden (status 0), aber Code vorhanden: ${response.code}")
            null // Oder erstelle ein Dummy-OFFProduct mit dem Barcode
        }
        else {
            Log.w("Network", "Produkt nicht gefunden oder Fehler bei der API-Anfrage für Barcode: $barcode. Status: ${response.status}")
            null
        }
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        Log.e("Network", "ClientRequestException (z.B. 404) für Barcode: $barcode. Status: ${e.response.status}", e)
        null
    }
    catch (e: Exception) {
        Log.e("Network", "Allgemeiner API-Fehler für Barcode: $barcode", e)
        null
    }
}

// Extension Funktion, idealerweise in einer Utility-Datei oder beim OFFProduct-Modell
// Bereits in deinem ViewModel-Code vorhanden, hier zur Vollständigkeit
// fun OFFProduct.getDisplayName(): String = this.productName ?: this.genericName ?: this.productNameDE ?: this.productNameEN ?: "Unbekanntes Produkt"

