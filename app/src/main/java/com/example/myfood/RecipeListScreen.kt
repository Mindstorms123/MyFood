package com.example.myfood.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myfood.FoodViewModel
import com.example.myfood.data.recipe.RecipeSummary
import com.example.myfood.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    navController: NavController,
    foodViewModel: FoodViewModel,
    recipeViewModel: RecipeViewModel = viewModel()
) {
    val recipeListState = recipeViewModel.recipeListUiState
    val suggestedRecipesState = recipeViewModel.suggestedRecipesUiState
    val pantryItems = foodViewModel.foodItems

    // Lade Rezeptvorschläge basierend auf Vorratsgegenständen
    LaunchedEffect(pantryItems) {
        // Übergebe die tatsächlichen FoodItem-Objekte, falls dein ViewModel das erwartet
        // oder eine Liste von Strings, falls es nur die Namen braucht.
        // In unserem aktuellen RecipeViewModel -> loadSuggestedRecipes erwartet es List<FoodItem>
        recipeViewModel.loadSuggestedRecipes(pantryItems)
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp) // Etwas mehr Abstand zwischen Karten
        ) {
            item {
                Text(
                    "Vorschläge für dich",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            when (val state = suggestedRecipesState) {
                is RecipeListUiState.Loading -> {
                    item { CircularProgressIndicator(modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)) }
                }
                is RecipeListUiState.Success -> {
                    if (state.recipes.isEmpty()) {
                        item {
                            Text(
                                if (pantryItems.isEmpty()) "Füge Lebensmittel zu deinem Vorrat hinzu, um Rezeptvorschläge zu erhalten."
                                else "Keine passenden Rezepte für deinen Vorrat gefunden."
                            )
                        }
                    } else {
                        items(state.recipes, key = { recipe -> "suggested_${recipe.id}" }) { recipe ->
                            RecipeCard(recipe = recipe, onClick = {
                                navController.navigate(Screen.RecipeDetail.createRoute(recipe.id.toString()))
                            })
                        }
                    }
                }
                is RecipeListUiState.Error -> {
                    item { Text("Fehler beim Laden der Vorschläge: ${state.message}") }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Beliebte Rezepte",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            when (val state = recipeListState) {
                is RecipeListUiState.Loading -> {
                    item { CircularProgressIndicator(modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)) }
                }
                is RecipeListUiState.Success -> {
                    if (state.recipes.isEmpty()) {
                        item { Text("Keine beliebten Rezepte gefunden.") }
                    } else {
                        items(state.recipes, key = { recipe -> "random_${recipe.id}" }) { recipe ->
                            RecipeCard(recipe = recipe, onClick = {
                                navController.navigate(Screen.RecipeDetail.createRoute(recipe.id.toString()))
                            })
                        }
                    }
                }
                is RecipeListUiState.Error -> {
                    item { Text("Fehler: ${state.message}") }
                }
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
                    .data(recipe.imageUrl)
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
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                recipe.sourceName?.let { srcName -> // Expliziter Name für 'it'
                    Text(
                        "Quelle: $srcName",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // ANPASSUNG HIER:
                // Da TheMealDB diese Infos nicht für die Übersicht liefert,
                // entfernen wir die Row, die readyInMinutes und servings anzeigt.
                // Alternativ könntest du hier andere Infos aus RecipeSummary anzeigen, falls vorhanden.
                /*
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    recipe.readyInMinutes?.let { minutes -> // Sicherer Aufruf
                        Text(
                            "Zeit: $minutes Min.",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    recipe.servings?.let { numServings -> // Sicherer Aufruf
                        Text(
                            "Portionen: $numServings",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                */
            }
        }
    }
}