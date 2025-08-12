package com.example.myfood

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.util.Log
import android.widget.DatePicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear // Import für das Clear-Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search // Import für das Search-Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager // Für das Verstecken der Tastatur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
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
    var searchQuery by remember { mutableStateOf("") } // Zustand für den Suchtext

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current // Um die Tastatur zu verstecken

    // Beobachte den StateFlow aus dem ViewModel
    val foodItems by viewModel.foodItemsStateFlow.collectAsState()

    // Filtere die foodItems basierend auf der searchQuery
    val filteredFoodItems = remember(foodItems, searchQuery) {
        if (searchQuery.isBlank()) {
            foodItems
        } else {
            foodItems.filter { item ->
                item.name.contains(searchQuery, ignoreCase = true) ||
                        (item.brand?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

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
                    viewModel.clearEditingStates()
                    showAddDialogWithExpiry = true
                }) { Icon(Icons.Default.Add, "Neues Lebensmittel") }

                FloatingActionButton(onClick = {
                    if (cameraPermissionGranted) {
                        Log.d("FoodScreen", "Kamera FAB: Berechtigung vorhanden, zeige Scanner.")
                        viewModel.clearEditingStates()
                        showScanner = true
                    } else {
                        Log.d("FoodScreen", "Kamera FAB: Berechtigung fehlt, frage an.")
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) { Icon(Icons.Default.CameraAlt, "Barcode scannen") }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) { // Column hinzugefügt, um Suchleiste und Liste unterzubringen
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Suche im Vorrat") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Suchen")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Suche löschen")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus() // Tastatur verstecken bei "Search"
                })
            )

            LazyColumn(
                modifier = Modifier
                    // .padding(paddingValues) // Padding wird jetzt von der äußeren Column gehandhabt
                    .padding(horizontal = 16.dp) // Vertikales Padding kann hier bleiben oder angepasst werden
                    .weight(1f), // Damit die LazyColumn den verbleibenden Platz einnimmt
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredFoodItems, // Verwende die gefilterte Liste
                    key = { item -> item.id }
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
                            if (item.quantity > 0) {
                                viewModel.updateItemQuantityById(item.id, item.quantity - 1)
                            }
                        },
                        onDeleteClick = {
                            viewModel.removeItemById(item.id)
                        }
                    )
                }
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
                            Log.w("FoodScreen", "Produkt für Barcode '$barcode' nicht gefunden.")
                            viewModel.setScannedProductForEditing(OFFProduct(id = barcode, productName = "Neues Produkt ($barcode)"))
                            navController.navigate(Screen.EditFoodItem.route)
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
                enabled = item.quantity >= 0
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
        IconButton(onClick = onDecrease, enabled = quantity > 0 && enabled) {
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
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128,
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
                                            if (!hasScanned) {
                                                hasScanned = true
                                                Log.d("ScannerView", "Barcode gefunden im Analyzer: $barcodeValue")
                                                onBarcodeScanned(barcodeValue)
                                            }
                                        }
                                    )
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                        Log.d("ScannerView", "Kamera an Lifecycle gebunden.")
                    } catch (exc: Exception) {
                        Log.e("ScannerView", "Fehler beim Binden der Kamera an Lifecycle", exc)
                        onClose()
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
                    barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }?.let { barcodeValue ->
                        onBarcodeFound(barcodeValue)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ScannerView", "Fehler bei der Barcode-Erkennung", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

suspend fun fetchProductApi(barcode: String): OFFProduct? = withContext(Dispatchers.IO) {
    try {
        Log.d("Network", "API-Aufruf für Barcode: $barcode")
        val response: OpenFoodFactsApiResponse =
            client.get("https://world.openfoodfacts.org/api/v2/product/$barcode").body()

        Log.d("Network", "API-Antwort Status: ${response.status}, Barcode: $barcode, Produkt: ${response.product?.productName}")

        if (response.status == 1 && response.product != null) {
            response.product.copy(id = response.product.id)
        } else if (response.status == 0 && response.code != null) {
            Log.w("Network", "Produkt nicht gefunden (status 0), aber Code vorhanden: ${response.code}")
            null
        } else {
            Log.w("Network", "Produkt nicht gefunden oder Fehler bei der API-Anfrage für Barcode: $barcode. Status: ${response.status}")
            null
        }
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        Log.e("Network", "ClientRequestException (z.B. 404) für Barcode: $barcode. Status: ${e.response.status}", e)
        null
    } catch (e: Exception) {
        Log.e("Network", "Allgemeiner API-Fehler für Barcode: $barcode", e)
        null
    }
}