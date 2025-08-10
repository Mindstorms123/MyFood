package com.example.myfood.ui.recipe

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.error
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
//import androidx.privacysandbox.tools.core.generator.build
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myfood.R
import com.example.myfood.data.model.Ingredient
import com.example.myfood.data.model.Recipe
import com.example.myfood.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    navController: NavController,
    recipeViewModel: RecipeViewModel = hiltViewModel()
) {
    LaunchedEffect(recipeId) {
        recipeViewModel.loadRecipeDetails(recipeId, translateToGerman = true)
    }

    val state by recipeViewModel.recipeDetailUiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentTitle = if (state is RecipeDetailUiState.Success) {
                        (state as RecipeDetailUiState.Success).recipe.title
                    } else {
                        "Rezeptdetails"
                    }
                    Text(currentTitle, maxLines = 1)
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (state is RecipeDetailUiState.Success) {
                        val currentRecipe = (state as RecipeDetailUiState.Success).recipe
                        IconButton(onClick = {
                            val idForEditScreen: Long? = currentRecipe.id
                            if (idForEditScreen != null) {
                                navController.navigate(Screen.AddEditRecipe.createRoute(idForEditScreen))
                            }
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rezept bearbeiten")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Rezept löschen")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showDeleteDialog && state is RecipeDetailUiState.Success) {
            val recipeToDelete = (state as RecipeDetailUiState.Success).recipe
            DeleteRecipeDialog(
                recipeTitle = recipeToDelete.title,
                onConfirm = {
                    showDeleteDialog = false
                    recipeViewModel.deleteRecipeById(recipeId) {
                        navController.popBackStack()
                    }
                },
                onDismiss = { showDeleteDialog = false }
            )
        }

        when (val currentState = state) {
            is RecipeDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is RecipeDetailUiState.Success -> {
                val recipe = currentState.recipe
                RecipeDetailContent(recipe = recipe, modifier = Modifier.padding(paddingValues))
            }
            is RecipeDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Fehler: ${currentState.message}")
                }
            }
        }
    }
}

@Composable
fun RecipeDetailContent(recipe: Recipe, modifier: Modifier = Modifier) {
    val debugRecipeTitleForSpecificLogging = "Eiersalat" // Anpassen für spezifisches Logging

    if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
        Log.d("RecipeDetailContent", "Displaying Recipe: ${recipe.title}")
        Log.d("RecipeDetailContent", "Raw imagePath from Recipe object: '${recipe.imagePath}'")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // --- Bild ---
        // Nur ein item für das Bild hinzufügen, wenn ein imagePath vorhanden ist
        if (!recipe.imagePath.isNullOrBlank()) {
            item { // Das Bild ist ein eigenes Item in der LazyColumn
                val imagePathFromRecipe = recipe.imagePath!!
                val modelDataForCoil: Any

                if (imagePathFromRecipe.startsWith("pics/") &&
                    !imagePathFromRecipe.startsWith("/") &&
                    !imagePathFromRecipe.contains("://")
                ) {
                    modelDataForCoil = "file:///android_asset/$imagePathFromRecipe"
                    if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
                        Log.i("RecipeDetailContent", "Image identified as ASSET. Path for Coil: '$modelDataForCoil'")
                    }
                } else {
                    modelDataForCoil = imagePathFromRecipe
                    if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
                        Log.i("RecipeDetailContent", "Image identified as USER-GENERATED/FILE/URI. Path for Coil: '$modelDataForCoil'")
                    }
                }

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(modelDataForCoil)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_error_image)
                        .listener(
                            onStart = { _ ->
                                if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
                                    Log.d("RecipeDetailContent", "Coil: Start loading '$modelDataForCoil'")
                                }
                            },
                            onError = { _, result ->
                                if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
                                    Log.e("RecipeDetailContent", "Coil: Error loading '$modelDataForCoil': ${result.throwable}")
                                    result.throwable.printStackTrace()
                                }
                            },
                            onSuccess = { _, _ ->
                                if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
                                    Log.i("RecipeDetailContent", "Coil: Success loading '$modelDataForCoil'")
                                }
                            }
                        )
                        .build(),
                    contentDescription = recipe.title ?: "Rezeptbild",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp) // Behalte die Höhe, wenn das Bild angezeigt wird
                )
                Spacer(modifier = Modifier.height(16.dp)) // Abstand nach dem Bild
            }
        }
        // KEIN ELSE-BLOCK HIER, um keinen Platz zu reservieren, wenn kein Bild da ist

        // --- Titel und Metadaten ---
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                recipe.source?.let {
                    if (it.isNotBlank()) {
                        Text(
                            "Quelle: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                recipe.category?.let { category ->
                    if (category.isNotBlank()) {
                        Text(
                            "Kategorie: $category",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                recipe.tags?.let { tags ->
                    if (tags.isNotEmpty()) {
                        Text(
                            "Tags: ${tags.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // --- Zutaten ---
        item {
            SectionTitle(title = "Zutaten", modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(recipe.ingredients) { ingredient ->
            IngredientRowModern(
                ingredient = ingredient,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Anleitung ---
        if (recipe.instructions.isNotEmpty()) {
            item {
                SectionTitle(title = "Anleitung", modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(12.dp))
            }

            val cleanedInstructions = recipe.instructions
                .joinToString("\n")
                .replace("<br>", "\n", ignoreCase = true)
                .replace(Regex("<[^>]*>"), "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            itemsIndexed(cleanedInstructions) { index, instructionStep ->
                InstructionStepRow(
                    stepNumber = index + 1,
                    instruction = instructionStep,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// Hilfs-Composable für Sektionstitel
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

// Überarbeitete IngredientRow
@Composable
fun IngredientRowModern(ingredient: Ingredient, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val quantityText = ingredient.quantity?.takeIf { it.isNotBlank() }
        val unitText = ingredient.unit?.takeIf { it.isNotBlank() }
        val nameText = ingredient.name.takeIf { it.isNotBlank() } ?: "Unbekannte Zutat"

        Text(
            text = buildString {
                quantityText?.let { append("$it ") }
                unitText?.let { append("$it ") }
                append(nameText)
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}


// Neue Composable für einen einzelnen nummerierten Anleitungsschritt
@Composable
fun InstructionStepRow(stepNumber: Int, instruction: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$stepNumber.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(32.dp)
                .padding(end = 4.dp)
        )
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

// DeleteRecipeDialog Composable (unverändert)
@Composable
fun DeleteRecipeDialog(
    recipeTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rezept löschen?") },
        text = { Text("Möchtest du das Rezept \"$recipeTitle\" wirklich unwiderruflich löschen?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Löschen")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
