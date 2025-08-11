package com.example.myfood.ui.recipe

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear // Für den Löschen-Button im Suchfeld
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search // Für das Such-Icon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.myfood.R
import com.example.myfood.data.model.Recipe
import com.example.myfood.navigation.Screen

const val UNCATEGORIZED_TEXT = "Weitere Rezepte" // Konstante für nicht kategorisierte Rezepte

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    navController: NavController,
    recipeViewModel: RecipeViewModel = hiltViewModel()
) {
    val allRecipes by recipeViewModel.recipes.collectAsState()
    var searchText by remember { mutableStateOf("") }
    // Hält die Namen der Kategorien, die aktuell ausgeklappt sind
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

    // 1. Filtern basierend auf Suchtext
    val filteredRecipes = remember(allRecipes, searchText) {
        if (searchText.isBlank()) {
            allRecipes
        } else {
            allRecipes.filter { recipe ->
                recipe.title.contains(searchText, ignoreCase = true) ||
                        (recipe.category?.contains(searchText, ignoreCase = true) == true)
            }
        }
    }

    // 2. Gruppieren der gefilterten Rezepte nach Kategorie
    val recipesByCategory = remember(filteredRecipes) {
        filteredRecipes
            .groupBy { it.category?.takeIf { cat -> cat.isNotBlank() } ?: UNCATEGORIZED_TEXT }
            .toSortedMap() // Sortiert die Kategorienamen alphabetisch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rezepte") },
                windowInsets = WindowInsets(0.dp) // Behebt potenziellen Inset-Konflikt
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate(Screen.AddEditRecipe.createRoute(null))
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Neues Rezept hinzufügen")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Suchleiste
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Rezepte durchsuchen...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Suchen") },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Suche löschen")
                        }
                    }
                }
            )

            if (filteredRecipes.isEmpty() && searchText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keine Rezepte für '$searchText' gefunden.")
                }
            } else if (allRecipes.isEmpty() && searchText.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keine Rezepte gefunden. Füge eigene hinzu oder der Import läuft noch.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize() // Füllt den verfügbaren Platz in der Column
                        .padding(horizontal = 8.dp), // Horizontales Padding für die gesamte Liste
                    contentPadding = PaddingValues(bottom = 72.dp, top = 8.dp) // Platz für FAB und Suchleiste
                ) {
                    recipesByCategory.forEach { (category, recipesInCategory) ->
                        // Kategorie Header
                        item(key = "header_$category") {
                            CategoryHeader(
                                categoryName = category,
                                isExpanded = category in expandedCategories,
                                recipeCount = recipesInCategory.size,
                                onClick = {
                                    expandedCategories = if (category in expandedCategories) {
                                        expandedCategories - category
                                    } else {
                                        expandedCategories + category
                                    }
                                }
                            )
                        }

                        // Rezepte innerhalb der Kategorie (ausklappbar)
                        // Wichtig: items benötigt einen eindeutigen Key für jedes Element
                        if (category in expandedCategories) {
                            items(recipesInCategory, key = { recipe -> "recipe_${recipe.id}" }) { recipe ->
                                Box(modifier = Modifier.padding(bottom = 12.dp, start = 8.dp, end = 8.dp)) { // Padding für jedes Rezeptitem
                                    RecipeListItem(
                                        recipe = recipe,
                                        onClick = {
                                            navController.navigate(Screen.RecipeDetail.createRoute(recipe.id.toString()))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    recipeCount: Int,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "expansion_arrow_rotation")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp), // Angepasstes Padding für den Header
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$categoryName ($recipeCount)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore, // Pfeil zeigt immer nach unten, Rotation erledigt den Rest
            contentDescription = if (isExpanded) "Kategorie einklappen" else "Kategorie ausklappen",
            modifier = Modifier.rotate(rotationAngle)
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListItem(recipe: Recipe, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (!recipe.imagePath.isNullOrBlank()) {
                val imagePathFromRecipe = recipe.imagePath!!
                val modelDataForCoil: Any

                if (imagePathFromRecipe.startsWith("pics/") &&
                    !imagePathFromRecipe.startsWith("/") &&
                    !imagePathFromRecipe.contains("://")
                ) {
                    modelDataForCoil = "file:///android_asset/$imagePathFromRecipe"
                    // Log.d("RecipeListItem", "Image for '${recipe.title}' is ASSET. Path for Coil: '$modelDataForCoil'")
                } else {
                    modelDataForCoil = imagePathFromRecipe
                    // Log.d("RecipeListItem", "Image for '${recipe.title}' is USER-GENERATED/FILE/URI. Path for Coil: '$modelDataForCoil'")
                }

                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(modelDataForCoil)
                            .crossfade(true)
                            .placeholder(R.drawable.ic_placeholder_image)
                            .error(R.drawable.ic_error_image)
                            .listener(
                                onError = { _, result ->
                                    Log.e("RecipeListItem", "Coil Error loading image for '${recipe.title}', path '$modelDataForCoil': ${result.throwable}")
                                }
                            )
                            .build()
                    ),
                    contentDescription = recipe.title ?: "Rezeptbild",
                    modifier = Modifier
                        .height(180.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Kategorie wird jetzt im Header angezeigt, kann hier optional weggelassen oder anders dargestellt werden
                // recipe.category?.let { category ->
                //     if (category.isNotBlank()) {
                //         Text(
                //             text = "Kategorie: $category",
                //             style = MaterialTheme.typography.bodyMedium,
                //             color = MaterialTheme.colorScheme.onSurfaceVariant
                //         )
                //         Spacer(modifier = Modifier.height(4.dp))
                //     }
                // }

                Text(
                    text = if (recipe.source == "user" || recipe.source == "Eigenes Rezept") "Eigenes Rezept" else recipe.source ?: "Rezeptsammlung",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
