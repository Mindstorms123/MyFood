package com.example.myfood.ui.recipe

// Importe für Composable-Funktionen und UI-Elemente
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Korrekter Import für items in LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect // Für LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myfood.FoodViewModel // Für pantryItems
import com.example.myfood.data.recipe.RecipeSummary // Für das Datenmodell der Rezeptkarte
import com.example.myfood.navigation.Screen // Für die Navigation

// Die Definition von RecipeListUiState wird im RecipeViewModel.kt erwartet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    navController: NavController,
    foodViewModel: FoodViewModel, // FoodViewModel liefert die pantryItems
    recipeViewModel: RecipeViewModel = viewModel()
) {
    // Direkter Zugriff auf die States vom RecipeViewModel
    val recipeListState = recipeViewModel.recipeListUiState
    val suggestedRecipesState = recipeViewModel.suggestedRecipesUiState
    val pantryItems = foodViewModel.foodItems

    // Lade Vorschläge, wenn sich pantryItems ändert oder beim ersten Mal
    LaunchedEffect(pantryItems) {
        if (pantryItems.isNotEmpty()) {
            recipeViewModel.loadSuggestedRecipes(pantryItems)
        } else {
            // Optional: explizit den suggestedRecipesUiState auf Success(emptyList()) setzen,
            // wenn der Vorrat leer ist.
            // recipeViewModel.clearSuggestedRecipes() // Methode im ViewModel erstellen, falls benötigt
        }
    }

    // Die "Random Recipes" werden initial im ViewModel geladen.

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Rezepte") })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Abschnitt für vorgeschlagene Rezepte
            item {
                Text(
                    "Vorschläge für dich",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // Behandlung des suggestedRecipesState
            val currentSuggestedState = suggestedRecipesState // Für Smart Cast
            when (currentSuggestedState) {
                is RecipeListUiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is RecipeListUiState.Success -> {
                    if (currentSuggestedState.recipes.isEmpty()) {
                        item {
                            Text(
                                text = if (pantryItems.isEmpty()) "Füge Lebensmittel zu deinem Vorrat hinzu, um Rezeptvorschläge zu erhalten."
                                else "Keine passenden Rezepte für deinen Vorrat gefunden.",
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(currentSuggestedState.recipes, key = { recipe -> "suggested_${recipe.id}" }) { recipe ->
                            RecipeCard(recipe = recipe, onClick = {
                                navController.navigate(Screen.RecipeDetail.createRoute(recipe.id))
                            })
                        }
                    }
                }
                is RecipeListUiState.Error -> {
                    item {
                        Text(
                            "Fehler beim Laden der Vorschläge: ${currentSuggestedState.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                // 'else -> {}' ist hier nicht mehr nötig, wenn RecipeListUiState ein sealed interface ist
                // und alle Fälle abgedeckt sind.
            }

            // Trenner und Titel für die andere Rezeptliste (z.B. zufällige/beliebte Rezepte)
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Entdecke neue Rezepte",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = {
                        // Annahme: Dein RecipeViewModel hat eine Methode loadRandomRecipes()
                        // oder eine ähnlich benannte Methode zum Neuladen dieser Liste.
                        recipeViewModel.loadRandomRecipes()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Neue Rezepte laden"
                        )
                    }
                }
                // Optional: Ein kleiner Abstand unter dem Titel/Button
                // Spacer(modifier = Modifier.height(8.dp))
            }

            // Behandlung des recipeListState (z.B. für zufällige oder beliebte Rezepte)
            val currentRecipeListState = recipeListState // Für Smart Cast
            when (currentRecipeListState) {
                is RecipeListUiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is RecipeListUiState.Success -> {
                    if (currentRecipeListState.recipes.isEmpty()) {
                        item {
                            Text(
                                "Keine Rezepte zum Entdecken gefunden.",
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(currentRecipeListState.recipes, key = { recipe -> "list_${recipe.id}" }) { recipe ->
                            RecipeCard(recipe = recipe, onClick = {
                                navController.navigate(Screen.RecipeDetail.createRoute(recipe.id))
                            })
                        }
                    }
                }
                is RecipeListUiState.Error -> {
                    item {
                        Text(
                            "Fehler beim Laden der Rezepte: ${currentRecipeListState.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                // 'else -> {}' ist hier nicht mehr nötig
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCard(
    recipe: RecipeSummary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(recipe.thumbnailUrl)
                    .crossfade(true)
                    // .placeholder(R.drawable.placeholder_image) // Optional
                    // .error(R.drawable.error_image)           // Optional
                    .build(),
                contentDescription = recipe.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
            }
        }
    }
}