package com.example.myfood.ui.recipe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Korrekter Import für LazyColumn items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Import für viewModel() Composable
import androidx.navigation.NavController
import coil.compose.AsyncImage // *** KORREKTER IMPORT FÜR COIL ***
import coil.request.ImageRequest // *** KORREKTER IMPORT FÜR COIL ***
import com.example.myfood.data.recipe.Ingredient // Stelle sicher, dass dieser Import passt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    navController: NavController,
    recipeViewModel: RecipeViewModel = viewModel()
) {
    LaunchedEffect(recipeId) {
        recipeViewModel.loadRecipeDetails(recipeId)
    }

    val state = recipeViewModel.recipeDetailUiState

    Scaffold(
        topBar = {
            TopAppBar( // Beispiel für eine TopAppBar, falls du sie noch nicht hast
                title = {
                    if (state is RecipeDetailUiState.Success) {
                        Text(state.recipe.title, maxLines = 1)
                    } else {
                        Text("Rezeptdetails")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (state) {
            is RecipeDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RecipeDetailUiState.Success -> {
                val recipe = state.recipe
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        // *** KORRIGIERTER AsyncImage-AUFRUF ***
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(recipe.imageUrl) // Die URL des Bildes aus deinem Recipe-Objekt
                                .crossfade(true)
                                // .placeholder(R.drawable.placeholder_image) // Optional: Platzhalterbild-Ressource
                                // .error(R.drawable.error_image)       // Optional: Fehlerbild-Ressource
                                .build(),
                            contentDescription = recipe.title, // Wichtig für Barrierefreiheit
                            contentScale = ContentScale.Crop,  // Wie das Bild skaliert werden soll
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp) // Beispielhöhe
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
                            recipe.sourceName?.let { srcName ->
                                Text("Quelle: $srcName", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                            }

                            // Row für readyInMinutes und servings (wie zuvor besprochen, wahrscheinlich entfernen für TheMealDB)
                            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                                // Lasse diesen Bereich leer oder kommentiere ihn aus,
                                // da TheMealDB diese Informationen nicht direkt liefert.
                            }

                            recipe.summary?.let { summaryText ->
                                Text("Information:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                                Text(summaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
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
                    items(recipe.extendedIngredients) { ingredient ->
                        IngredientRow(ingredient, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }

                    item {
                        Text(
                            "Anleitung:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                        Text(
                            recipe.instructions.replace("<[^>]*>".toRegex(), ""), // Entfernt HTML-Tags
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            is RecipeDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Fehler: ${state.message}")
                }
            }
        }
    }
}

@Composable
fun IngredientRow(ingredient: Ingredient, modifier: Modifier = Modifier) {
    Text(
        text = "- ${ingredient.original}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}