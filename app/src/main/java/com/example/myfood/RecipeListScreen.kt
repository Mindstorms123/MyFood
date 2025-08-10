package com.example.myfood.ui.recipe

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.myfood.R
import com.example.myfood.data.model.Recipe
import com.example.myfood.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    navController: NavController,
    recipeViewModel: RecipeViewModel = hiltViewModel()
) {
    val recipes by recipeViewModel.recipes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rezepte") },
                windowInsets = WindowInsets(0.dp)
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
        if (recipes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Keine Rezepte gefunden. Füge eigene hinzu oder der Import läuft noch.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(recipes, key = { it.id }) { recipe ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListItem(recipe: Recipe, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // --- Bild im Listenelement ---
            // Nur anzeigen, wenn ein Bildpfad vorhanden ist
            if (!recipe.imagePath.isNullOrBlank()) {
                val imagePathFromRecipe = recipe.imagePath!!
                val modelDataForCoil: Any

                if (imagePathFromRecipe.startsWith("pics/") &&
                    !imagePathFromRecipe.startsWith("/") &&
                    !imagePathFromRecipe.contains("://")
                ) {
                    modelDataForCoil = "file:///android_asset/$imagePathFromRecipe"
                    Log.d("RecipeListItem", "Image for '${recipe.title}' is ASSET. Path for Coil: '$modelDataForCoil'")
                } else {
                    modelDataForCoil = imagePathFromRecipe
                    Log.d("RecipeListItem", "Image for '${recipe.title}' is USER-GENERATED/FILE/URI. Path for Coil: '$modelDataForCoil'")
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
                        .height(180.dp) // Behalte die Höhe bei, wenn das Bild angezeigt wird
                        .fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            }
            // KEIN ELSE-BLOCK HIER, um keinen Platz zu reservieren, wenn kein Bild da ist

            // --- Textuelle Informationen ---
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))

                recipe.category?.let { category ->
                    if (category.isNotBlank()) {
                        Text(
                            text = "Kategorie: $category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Text(
                    text = if (recipe.source == "user" || recipe.source == "Eigenes Rezept") "Eigenes Rezept" else recipe.source ?: "Rezeptsammlung",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
