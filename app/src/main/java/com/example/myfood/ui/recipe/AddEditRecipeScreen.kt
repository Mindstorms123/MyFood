package com.example.myfood.ui.recipe // Passe den Paketnamen an

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.myfood.data.model.Ingredient // Stelle sicher, dass diese Klasse einen Konstruktor hat, der name ggf. mit Default erlaubt oder du ihn immer setzt
import com.example.myfood.data.model.Recipe
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
// Stelle sicher, dass diese Imports vorhanden sind:
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.net.toUri

// Hilfsfunktion zum Erstellen einer temporären Datei für die Kamera
fun Context.createImageFile(): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
    val imageFileName = "JPEG_${timeStamp}_"
    val storageDir = cacheDir // Verwende cacheDir für temporäre Dateien
    return File.createTempFile(
        imageFileName,
        ".jpg",
        storageDir
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecipeScreen(
    navController: NavController,
    recipeId: Long?,
    viewModel: RecipeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var uiIsLoading by remember { mutableStateOf(recipeId != null && recipeId != 0L) }

    var title by rememberSaveable { mutableStateOf("") }
    val ingredients = remember { mutableStateListOf<Ingredient>() }
    val instructions = remember { mutableStateListOf<String>() }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var currentImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var initialImagePathInRecipe by rememberSaveable { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("AddEditRecipeScreen", "Image picked from gallery: $it")
            currentImageUri = it
        }
    }

    var tempCameraImageUriHolder by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempCameraImageUriHolder?.let { capturedUri ->
                Log.d("AddEditRecipeScreen", "Image captured by camera: $capturedUri")
                currentImageUri = capturedUri
            }
        } else {
            Log.d("AddEditRecipeScreen", "Camera capture was not successful.")
        }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val imageFile = context.createImageFile()
            val newUriForCamera = FileProvider.getUriForFile(
                Objects.requireNonNull(context),
                "${context.packageName}.provider", // Stelle sicher, dass dies mit deinem Provider im Manifest übereinstimmt
                imageFile
            )
            tempCameraImageUriHolder = newUriForCamera
            cameraLauncher.launch(newUriForCamera)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Kameraberechtigung wurde verweigert.")
            }
        }
    }

    LaunchedEffect(recipeId) {
        Log.d("AddEditRecipeScreen", "Initial load triggered for recipeId: $recipeId")
        if (recipeId != null && recipeId != 0L) {
            viewModel.loadRecipeForEditing(recipeId)
        } else {
            viewModel.clearRecipeForEditing() // Für den "Neues Rezept"-Fall
            uiIsLoading = false // Nicht laden, wenn es ein neues Rezept ist
        }
    }

    val recipeBeingEdited: Recipe? by viewModel.recipeToEdit.collectAsState()

    LaunchedEffect(recipeBeingEdited, recipeId) {
        val loadedRecipe = recipeBeingEdited
        Log.d("AddEditRecipeScreen", "Recipe observer triggered. RecipeId: $recipeId, LoadedRecipe ID: ${loadedRecipe?.id}, Title: ${loadedRecipe?.title}")

        if (recipeId != null && recipeId != 0L) { // Modus: Rezept bearbeiten
            if (loadedRecipe != null && loadedRecipe.id == recipeId) {
                Log.d("AddEditRecipeScreen", "Editing existing recipe. ID: ${loadedRecipe.id}, ImagePath: '${loadedRecipe.imagePath}'")
                title = loadedRecipe.title
                ingredients.clear(); ingredients.addAll(loadedRecipe.ingredients.map { it.copy() }) // Defensive Kopie
                if (ingredients.isEmpty()) ingredients.add(Ingredient(name = "")) // KORREKTUR
                instructions.clear(); instructions.addAll(loadedRecipe.instructions.map { it }) // Defensive Kopie
                if (instructions.isEmpty()) instructions.add("")
                category = loadedRecipe.category
                initialImagePathInRecipe = loadedRecipe.imagePath

                currentImageUri = loadedRecipe.imagePath?.let { path ->
                    try {
                        if (path.startsWith("content://")) {
                            Log.d("AddEditRecipeScreen", "Path is content URI: $path")
                            path.toUri()
                        } else if (path.startsWith("file://")) {
                            Log.d("AddEditRecipeScreen", "Path is file URI: $path")
                            path.toUri()
                        } else if (path.startsWith("/")) { // Absoluter Dateipfad
                            val file = File(path)
                            if (file.exists()) {
                                Log.d("AddEditRecipeScreen", "Path is an existing absolute file path. Creating file URI: $path")
                                Uri.fromFile(file)
                            } else {
                                Log.w("AddEditRecipeScreen", "Absolute file path does not exist: $path")
                                null
                            }
                        } else if (path.startsWith("pics/")) { // Asset-Pfad
                            Log.d("AddEditRecipeScreen", "Path is an asset path (pics/): $path. Setting currentImageUri to null for editing.")
                            null
                        } else {
                            Log.w("AddEditRecipeScreen", "Unrecognized imagePath format, trying to check if file exists: $path")
                            val directFile = File(path)
                            val fileInAppContext = File(context.filesDir, path)

                            if (directFile.isAbsolute && directFile.exists()) {
                                Uri.fromFile(directFile)
                            } else if (fileInAppContext.exists()) {
                                Uri.fromFile(fileInAppContext)
                            }
                            else {
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AddEditRecipeScreen", "Error parsing initial imagePath '$path' into Uri", e)
                        null
                    }
                }
                Log.d("AddEditRecipeScreen", "Set currentImageUri to: $currentImageUri (from initialImagePathInRecipe: $initialImagePathInRecipe)")
                uiIsLoading = false
            } else if (loadedRecipe == null && (isRecipeLoadAttempted())) {
                Log.w("AddEditRecipeScreen", "Recipe with ID $recipeId was requested but not found after load attempt.")
                uiIsLoading = false
                coroutineScope.launch { snackbarHostState.showSnackbar("Rezept nicht gefunden.") }
                navController.popBackStack()
            }
        } else { // Modus: Neues Rezept
            Log.d("AddEditRecipeScreen", "Configuring for new recipe.")
            title = ""
            ingredients.clear(); ingredients.add(Ingredient(name = "")) // KORREKTUR
            instructions.clear(); instructions.add("")
            category = null
            initialImagePathInRecipe = null
            currentImageUri = null
            uiIsLoading = false
        }
    }


    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "recipe_img_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = file.outputStream()
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            Log.d("AddEditRecipeScreen", "Image saved to internal storage: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("AddEditRecipeScreen", "Error saving image to internal storage", e)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Fehler beim Speichern des Bildes.")
            }
            null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId != null && recipeId != 0L) "Rezept bearbeiten" else "Neues Rezept") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearRecipeForEditing()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        var errorMessage: String? = null
                        val activeIngredients = ingredients.filter { it.name.isNotBlank() }

                        when {
                            title.isBlank() -> errorMessage = "Titel darf nicht leer sein."
                            activeIngredients.any { it.name.isBlank() && (it.quantity?.isNotBlank() == true || it.unit?.isNotBlank() == true) } -> errorMessage = "Jede aktive Zutat mit Menge/Einheit benötigt einen Namen."
                        }

                        if (errorMessage != null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(errorMessage) }
                            return@IconButton
                        }

                        uiIsLoading = true
                        coroutineScope.launch {
                            var finalImagePath = initialImagePathInRecipe

                            if (currentImageUri != null && currentImageUri.toString() != initialImagePathInRecipe) {
                                Log.d("AddEditRecipeScreen", "New image selected. Current URI: $currentImageUri, Initial Path: $initialImagePathInRecipe")
                                val savedPath = saveImageToInternalStorage(context, currentImageUri!!)
                                if (savedPath != null) {
                                    if (initialImagePathInRecipe != null && initialImagePathInRecipe != savedPath && !initialImagePathInRecipe!!.startsWith("pics/")) {
                                        try {
                                            File(initialImagePathInRecipe!!).delete()
                                            Log.d("AddEditRecipeScreen", "Deleted old image file: $initialImagePathInRecipe")
                                        } catch (e: Exception) {
                                            Log.e("AddEditRecipeScreen", "Error deleting old image file: $initialImagePathInRecipe", e)
                                        }
                                    }
                                    finalImagePath = savedPath
                                } else {
                                    snackbarHostState.showSnackbar("Bild konnte nicht gespeichert werden.")
                                    uiIsLoading = false
                                    return@launch
                                }
                            } else if (currentImageUri == null && initialImagePathInRecipe != null) {
                                Log.d("AddEditRecipeScreen", "Image removed. Initial Path: $initialImagePathInRecipe")
                                if (!initialImagePathInRecipe!!.startsWith("pics/")) {
                                    try {
                                        File(initialImagePathInRecipe!!).delete()
                                        Log.d("AddEditRecipeScreen", "Deleted image file on removal: $initialImagePathInRecipe")
                                    } catch (e: Exception) {
                                        Log.e("AddEditRecipeScreen", "Error deleting image file on removal: $initialImagePathInRecipe", e)
                                    }
                                }
                                finalImagePath = null
                            }
                            else {
                                Log.d("AddEditRecipeScreen", "Image unchanged or no image involved. Final Path: $finalImagePath")
                            }

                            val recipeToSave = Recipe(
                                id = recipeBeingEdited?.id ?: 0L,
                                title = title.trim(),
                                ingredients = ingredients.toList().filter { it.name.isNotBlank() },
                                instructions = instructions.toList().filter { it.isNotBlank() },
                                category = category?.trim()?.takeIf { it.isNotEmpty() },
                                imagePath = finalImagePath,
                                source = recipeBeingEdited?.source ?: "Eigenes Rezept",
                                isFavorite = recipeBeingEdited?.isFavorite ?: false,
                            )

                            Log.d("AddEditRecipeScreen", "Saving recipe: $recipeToSave")
                            viewModel.saveRecipe(recipeToSave) { result ->
                                uiIsLoading = false
                                if (result.isSuccess) {
                                    Log.d("AddEditRecipeScreen", "Recipe saved successfully.")
                                    viewModel.clearRecipeForEditing()
                                    navController.popBackStack()
                                } else {
                                    Log.e("AddEditRecipeScreen", "Error saving recipe", result.exceptionOrNull())
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Fehler beim Speichern: ${result.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Done, "Speichern")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiIsLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = category ?: "",
                    onValueChange = { category = it },
                    label = { Text("Kategorie") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Bild", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Text("Galerie")
                    }
                    Button(onClick = {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                            PackageManager.PERMISSION_GRANTED -> {
                                val imageFile = context.createImageFile()
                                val newUriForCamera = FileProvider.getUriForFile(
                                    Objects.requireNonNull(context),
                                    "${context.packageName}.provider",
                                    imageFile
                                )
                                tempCameraImageUriHolder = newUriForCamera
                                cameraLauncher.launch(newUriForCamera)
                            }
                            else -> {
                                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Kamera")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kamera")
                    }
                }

                val imageToDisplayUri = currentImageUri

                if (imageToDisplayUri != null) {
                    Log.d("AddEditRecipeScreen", "Displaying currentImageUri in preview: $imageToDisplayUri")
                    Box(modifier = Modifier.padding(vertical = 16.dp)) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = imageToDisplayUri,
                                onError = { error -> Log.e("AddEditRecipeScreen", "Coil error for currentImageUri: $imageToDisplayUri", error.result.throwable)}
                            ),
                            contentDescription = "Ausgewähltes Rezeptbild",
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = {
                                Log.d("AddEditRecipeScreen", "Removing currentImageUri: $currentImageUri")
                                currentImageUri = null
                            },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Bild entfernen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    if (initialImagePathInRecipe != null && initialImagePathInRecipe!!.startsWith("pics/")) {
                        Log.d("AddEditRecipeScreen", "No currentImageUri, displaying initial asset image: $initialImagePathInRecipe")
                        Box(modifier = Modifier.padding(vertical = 16.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = "file:///android_asset/$initialImagePathInRecipe",
                                    onError = { error -> Log.e("AddEditRecipeScreen", "Coil error for initial asset: $initialImagePathInRecipe", error.result.throwable)}
                                ),
                                contentDescription = "Ursprüngliches Rezeptbild (Asset)",
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                            TextButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.align(Alignment.Center).padding(top = 8.dp)
                            ) {
                                Text("Neues Bild auswählen, um Asset zu ersetzen")
                            }
                        }
                    } else if (initialImagePathInRecipe != null && !initialImagePathInRecipe!!.startsWith("pics/")) {
                        Log.d("AddEditRecipeScreen", "No currentImageUri, initial user image was removed. Path: $initialImagePathInRecipe")
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Bild entfernt", style = MaterialTheme.typography.bodyMedium) }
                    }
                    else {
                        Log.d("AddEditRecipeScreen", "No currentImageUri and no initial asset image to display as fallback.")
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Kein Bild ausgewählt", style = MaterialTheme.typography.bodyMedium) }
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))

                Text("Zutaten", style = MaterialTheme.typography.titleMedium)
                ingredients.forEachIndexed { index, ingredient ->
                    IngredientInputRow(
                        ingredient = ingredient,
                        onIngredientChange = { updatedIngredient -> ingredients[index] = updatedIngredient },
                        onDelete = {
                            if (ingredients.size > 1) ingredients.removeAt(index)
                            else ingredients[index] = Ingredient(name = "") // KORREKTUR
                        }
                    )
                }
                Button(onClick = { ingredients.add(Ingredient(name = "")) }, modifier = Modifier.fillMaxWidth()) { // KORREKTUR
                    Icon(Icons.Filled.AddCircleOutline, "Zutat hinzufügen")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Zutat hinzufügen")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text("Zubereitung", style = MaterialTheme.typography.titleMedium)
                instructions.forEachIndexed { index, step ->
                    InstructionInputRow(
                        step = step,
                        onStepChange = { updatedStep -> instructions[index] = updatedStep },
                        onDelete = {
                            if (instructions.size > 1) instructions.removeAt(index)
                            else instructions[index] = ""
                        }
                    )
                }
                Button(onClick = { instructions.add("") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AddCircleOutline, "Schritt hinzufügen")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schritt hinzufügen")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun IngredientInputRow(
    ingredient: Ingredient,
    onIngredientChange: (Ingredient) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = ingredient.quantity ?: "",
            onValueChange = { onIngredientChange(ingredient.copy(quantity = it.takeIf { s -> s.isNotBlank() })) },
            label = { Text("Menge") },
            modifier = Modifier.weight(1.5f).padding(end = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = ingredient.unit ?: "",
            onValueChange = { onIngredientChange(ingredient.copy(unit = it.takeIf { s -> s.isNotBlank() })) },
            label = { Text("Einheit") },
            modifier = Modifier.weight(2f).padding(end = 4.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        OutlinedTextField(
            value = ingredient.name, // Sollte jetzt nie null sein, wenn Ingredient(name="") verwendet wird
            onValueChange = { onIngredientChange(ingredient.copy(name = it)) },
            label = { Text("Zutat*") },
            modifier = Modifier.weight(3f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, "Zutat löschen", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun InstructionInputRow(
    step: String,
    onStepChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = step,
            onValueChange = onStepChange,
            label = { Text("Schrittbeschreibung*") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLines = 5
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, "Schritt löschen", tint = MaterialTheme.colorScheme.error)
        }
    }
}

fun isRecipeLoadAttempted(): Boolean {
    return true
}
