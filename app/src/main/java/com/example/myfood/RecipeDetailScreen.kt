package com.example.myfood.ui.recipe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.* // Benötigt für LaunchedEffect,Composable,getValue etc.
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Für viewModel()
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myfood.data.recipe.IngredientPresentation // Dein Import für IngredientPresentation
import com.example.myfood.data.recipe.RecipeDetail // Dein Import für RecipeDetail
// Die RecipeDetailUiState Definition wurde entfernt, da sie jetzt im RecipeViewModel ist.
// Der Screen greift auf die Instanz aus dem ViewModel zu.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    navController: NavController,
    recipeViewModel: RecipeViewModel = viewModel() // ViewModel Instanz holen
) {
    // Lade Details, wenn sich recipeId ändert oder beim ersten Aufruf
    LaunchedEffect(recipeId) {
        // Rufe loadRecipeDetails mit translateToGerman = true auf,
        // um sicherzustellen, dass die Anzeige auf Deutsch erfolgt.
        recipeViewModel.loadRecipeDetails(recipeId, translateToGerman = true)
    }

    // Direkter Zugriff auf den State vom ViewModel, da es `var ... by mutableStateOf` verwendet
    val state = recipeViewModel.recipeDetailUiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentTitle = if (state is RecipeDetailUiState.Success) {
                        (state as RecipeDetailUiState.Success).recipe.title // Smart Cast
                    } else {
                        "Rezeptdetails"
                    }
                    Text(currentTitle, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Verwende 'currentState' um Smart Casting im when-Block sicherzustellen
        // und um Recomposition nur bei tatsächlicher State-Änderung auszulösen.
        val currentState = state
        when (currentState) {
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
                val recipe = currentState.recipe // recipe ist jetzt vom Typ RecipeDetail
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(recipe.thumbnailUrl) // Aus RecipeDetail
                                .crossfade(true)
                                // .placeholder(R.drawable.placeholder_image) // Optional
                                // .error(R.drawable.error_image)           // Optional
                                .build(),
                            contentDescription = recipe.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
                            recipe.sourceUrl?.let { srcUrl ->
                                Text(
                                    "Quelle: $srcUrl",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            recipe.category?.let { category ->
                                Text(
                                    "Kategorie: $category",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            recipe.area?.let { area ->
                                Text(
                                    "Region: $area",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            // "summary" ist nicht direkt in RecipeDetail.
                            // Verwende instructions oder einen Teil davon.
                            recipe.instructions?.let { instructionText ->
                                val summaryText = instructionText
                                    .replace("<br>", "\n") // HTML Zeilenumbrüche ersetzen
                                    .replace(Regex("<[^>]*>"), "") // Andere HTML-Tags entfernen
                                    .take(250) + if (instructionText.length > 250) "..." else ""

                                Text(
                                    "Beschreibung:",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                                Text(
                                    summaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    item {
                        Text(
                            "Zutaten:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    // recipe.ingredients ist List<IngredientPresentation>
                    items(recipe.ingredients) { ingredientPresentation ->
                        IngredientRow(
                            ingredient = ingredientPresentation,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    item {
                        recipe.instructions?.let { instructionText ->
                            Text(
                                "Anleitung:",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                            Text(
                                instructionText
                                    .replace("<br>", "\n") // HTML Zeilenumbrüche ersetzen
                                    .replace(Regex("<[^>]*>"), ""), // Andere HTML-Tags entfernen
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
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
fun IngredientRow(ingredient: IngredientPresentation, modifier: Modifier = Modifier) {
    // IngredientPresentation hat getDisplayName() und getDisplayMeasure()
    val name = ingredient.getDisplayName()
    val measure = ingredient.getDisplayMeasure()

    val text = if (!measure.isNullOrBlank()) {
        "- $measure $name"
    } else {
        "- $name"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}