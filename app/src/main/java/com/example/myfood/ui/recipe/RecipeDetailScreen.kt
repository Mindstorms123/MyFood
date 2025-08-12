package com.example.myfood.ui.recipe

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myfood.FoodItem
import com.example.myfood.FoodViewModel
import com.example.myfood.R
import com.example.myfood.data.model.Ingredient
import com.example.myfood.data.model.Recipe
import com.example.myfood.navigation.Screen
import com.example.myfood.ui.shoppinglist.ShoppingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    navController: NavController,
    recipeViewModel: RecipeViewModel = hiltViewModel(),
    foodViewModel: FoodViewModel = hiltViewModel(), // Injiziere FoodViewModel
    shoppingListViewModel: ShoppingListViewModel = hiltViewModel() // Injiziere ShoppingListViewModel
) {
    LaunchedEffect(recipeId) {
        recipeViewModel.loadRecipeDetails(recipeId, translateToGerman = true)
    }

    val recipeUiState by recipeViewModel.recipeDetailUiState.collectAsState()
    val currentPantryItems by foodViewModel.foodItemsStateFlow.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showComparisonDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentTitle = if (recipeUiState is RecipeDetailUiState.Success) {
                        (recipeUiState as RecipeDetailUiState.Success).recipe.title
                    } else {
                        "Rezeptdetails"
                    }
                    Text(currentTitle, maxLines = 1)
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (recipeUiState is RecipeDetailUiState.Success) {
                        val currentRecipe = (recipeUiState as RecipeDetailUiState.Success).recipe
                        IconButton(onClick = {
                            if (currentRecipe.ingredients.isNotEmpty()) {
                                showComparisonDialog = true
                            } else {
                                Toast.makeText(context, "Rezept hat keine Zutaten.", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = "Mit Vorrat abgleichen")
                        }
                        IconButton(onClick = {
                            val idForEditScreen: Long? = currentRecipe.id
                            navController.navigate(Screen.AddEditRecipe.createRoute(idForEditScreen))
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
        if (showDeleteDialog && recipeUiState is RecipeDetailUiState.Success) {
            val recipeToDelete = (recipeUiState as RecipeDetailUiState.Success).recipe
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

        if (showComparisonDialog && recipeUiState is RecipeDetailUiState.Success) {
            val currentRecipe = (recipeUiState as RecipeDetailUiState.Success).recipe
            RecipePantryComparisonDialog(
                recipeTitle = currentRecipe.title,
                recipeIngredients = currentRecipe.ingredients,
                pantryItems = currentPantryItems,
                onDismiss = { showComparisonDialog = false },
                onConfirm = { ingredientsToAdd ->
                    showComparisonDialog = false
                    if (ingredientsToAdd.isNotEmpty()) {
                        shoppingListViewModel.addIngredientsToShoppingList(
                            ingredients = ingredientsToAdd,
                            recipeName = currentRecipe.title
                        )
                        Toast.makeText(
                            context,
                            "${ingredientsToAdd.size} Zutat(en) zur Einkaufsliste hinzugefügt.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Keine Zutaten zur Einkaufsliste hinzugefügt.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        when (val currentState = recipeUiState) {
            is RecipeDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is RecipeDetailUiState.Success -> {
                RecipeDetailContent(recipe = currentState.recipe, modifier = Modifier.padding(paddingValues))
            }
            is RecipeDetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
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
    val debugRecipeTitleForSpecificLogging = "Eiersalat"

    if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
        Log.d("RecipeDetailContent", "Displaying Recipe: ${recipe.title}")
        Log.d("RecipeDetailContent", "Raw imagePath from Recipe object: '${recipe.imagePath}'")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        if (!recipe.imagePath.isNullOrBlank()) {
            item {
                val imagePathFromRecipe = recipe.imagePath!!
                val modelDataForCoil: Any = if (imagePathFromRecipe.startsWith("pics/") &&
                    !imagePathFromRecipe.startsWith("/") &&
                    !imagePathFromRecipe.contains("://")
                ) {
                    "file:///android_asset/$imagePathFromRecipe"
                } else {
                    imagePathFromRecipe
                }

                if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
                    Log.i("RecipeDetailContent", "Path for Coil: '$modelDataForCoil'")
                }

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(modelDataForCoil)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_error_image)
                        .listener(
                            onError = { _, result ->
                                if (recipe.title.contains(debugRecipeTitleForSpecificLogging, ignoreCase = true)) {
                                    Log.e("RecipeDetailContent", "Coil: Error loading '$modelDataForCoil': ${result.throwable}")
                                }
                            }
                        )
                        .build(),
                    contentDescription = recipe.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

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
                        Text("Quelle: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                recipe.category?.let { category ->
                    if (category.isNotBlank()) {
                        Text("Kategorie: $category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                recipe.tags?.let { tags ->
                    if (tags.isNotEmpty()) {
                        Text("Tags: ${tags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

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

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

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
            }.trim(),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

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
            modifier = Modifier.width(32.dp).padding(end = 4.dp)
        )
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

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
            ) { Text("Löschen") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

data class ComparisonItemState(
    val recipeIngredient: Ingredient,
    var suggestedPantryItems: List<FoodItem> = emptyList(),
    var selectedPantryItem: FoodItem? = null,
    var userConfirmedEnoughInPantry: Boolean = false,
    var needsToBeAddedToShoppingList: Boolean = true // Default to true, will be adjusted in RecipePantryComparisonDialog
) {
    fun getRecipeIngredientDisplayString(): String {
        val q = recipeIngredient.quantity?.takeIf { it.isNotBlank() }
        val u = recipeIngredient.unit?.takeIf { it.isNotBlank() }
        val n = recipeIngredient.name
        return listOfNotNull(q, u, n).joinToString(" ").trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePantryComparisonDialog(
    recipeTitle: String,
    recipeIngredients: List<Ingredient>,
    pantryItems: List<FoodItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<Ingredient>) -> Unit
) {
    val comparisonStates = remember {
        recipeIngredients.map { ingredient ->
            val suggestedItems = pantryItems.filter { pantryItem ->
                pantryItem.name.contains(ingredient.name, ignoreCase = true) ||
                        ingredient.name.contains(pantryItem.name, ignoreCase = true)
            }
            ComparisonItemState(
                recipeIngredient = ingredient,
                suggestedPantryItems = suggestedItems,
                needsToBeAddedToShoppingList = suggestedItems.isEmpty()
            )
        }.toMutableStateList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "'$recipeTitle' mit Vorrat abgleichen",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(comparisonStates.size) { index ->
                        val itemState = comparisonStates[index]
                        ComparisonItemRow(
                            itemState = itemState,
                            onPantryItemSelected = { pantryItem ->
                                val confirmedEnough = itemState.userConfirmedEnoughInPantry

                                comparisonStates[index] = itemState.copy(
                                    selectedPantryItem = pantryItem,
                                    // If a pantry item is selected, it's not needed for shopping list UNLESS
                                    // userConfirmedEnoughInPantry is false (meaning they specifically need more of it)
                                    // OR if pantryItem is null (meaning "none of these / manually needed")
                                    needsToBeAddedToShoppingList = if (pantryItem != null) {
                                        !confirmedEnough // If confirmedEnough is false, it means they need it
                                    } else {
                                        true // "none of these" always means add to shopping list
                                    },
                                    // userConfirmedEnoughInPantry reset if new item is selected, unless it's null
                                    userConfirmedEnoughInPantry = if (pantryItem != null) confirmedEnough else false
                                )
                            },
                            onConfirmEnoughInPantryChange = { isEnough ->
                                comparisonStates[index] = itemState.copy(
                                    userConfirmedEnoughInPantry = isEnough,
                                    // If an item is selected from pantry:
                                    // - If isEnough is true, then DO NOT add to shopping list.
                                    // - If isEnough is false, then DO add to shopping list.
                                    // If no item is selected (selectedPantryItem is null), this checkbox doesn't directly
                                    // control needsToBeAddedToShoppingList, which should already be true.
                                    needsToBeAddedToShoppingList = if (itemState.selectedPantryItem != null) {
                                        !isEnough
                                    } else {
                                        // If no pantry item is selected, it should generally be on the shopping list.
                                        // This path implies the "add to shopping list" checkbox was unchecked,
                                        // then "enough in pantry" was toggled, which is a bit of an edge case.
                                        // Defaulting to true if no item is selected.
                                        true
                                    }
                                )
                            },
                            onAddToShoppingListChange = { shouldAdd ->
                                comparisonStates[index] = itemState.copy(
                                    needsToBeAddedToShoppingList = shouldAdd,
                                    // If adding to shopping list, other states become less relevant or reset
                                    userConfirmedEnoughInPantry = if (shouldAdd) false else itemState.userConfirmedEnoughInPantry,
                                    selectedPantryItem = if (shouldAdd) null else itemState.selectedPantryItem
                                )
                            }
                        )
                        if (index < comparisonStates.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                thickness = DividerDefaults.Thickness,
                                color = DividerDefaults.color
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val itemsToAdd = comparisonStates
                            .filter {
                                // Add to shopping list if the checkbox is checked OR
                                // if a pantry item is selected but "enough in pantry" is NOT checked.
                                it.needsToBeAddedToShoppingList || (it.selectedPantryItem != null && !it.userConfirmedEnoughInPantry)
                            }
                            .map { it.recipeIngredient }
                            .distinctBy { it.name } // Ensure each ingredient is added only once
                        onConfirm(itemsToAdd)
                    }) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Hinzufügen")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonItemRow(
    itemState: ComparisonItemState,
    onPantryItemSelected: (FoodItem?) -> Unit,
    onConfirmEnoughInPantryChange: (Boolean) -> Unit,
    onAddToShoppingListChange: (Boolean) -> Unit
) {
    var expandedPantryDropdown by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = itemState.needsToBeAddedToShoppingList || (itemState.selectedPantryItem != null && !itemState.userConfirmedEnoughInPantry),
                onCheckedChange = { isChecked ->
                    onAddToShoppingListChange(isChecked)
                    // If unchecking, and an item was selected, we might need to imply 'enough in pantry' is true
                    // or handle this more explicitly. For now, onAddToShoppingListChange handles the primary logic.
                    if (!isChecked && itemState.selectedPantryItem != null) {
                        // If the main checkbox is unchecked, and an item is selected,
                        // it implies the user thinks they have enough of that selected item.
                        if (!itemState.userConfirmedEnoughInPantry) {
                            onConfirmEnoughInPantryChange(true)
                        }
                    }
                }
            )
            Text(
                text = itemState.getRecipeIngredientDisplayString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }

        // Show dropdown and "enough in pantry" only if:
        // 1. There are suggested pantry items
        // AND
        // 2. EITHER the item is NOT marked for shopping list (main checkbox is off)
        //    OR a pantry item is already selected (even if the main checkbox is on because "not enough" is implied)
        AnimatedVisibility(
            visible = itemState.suggestedPantryItems.isNotEmpty() &&
                    (!itemState.needsToBeAddedToShoppingList || itemState.selectedPantryItem != null)
        ) {
            Column(modifier = Modifier.padding(start = 32.dp, top = 8.dp, end = 16.dp)) {
                Text("Aus Vorrat:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedPantryDropdown,
                    onExpandedChange = { expandedPantryDropdown = !expandedPantryDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = itemState.selectedPantryItem?.name ?: "Wähle Vorratsitem...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPantryDropdown) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = expandedPantryDropdown,
                        onDismissRequest = { expandedPantryDropdown = false }
                    ) {
                        itemState.suggestedPantryItems.forEach { pantryItem ->
                            DropdownMenuItem(
                                text = { Text("${pantryItem.name} (Vorrat: ${pantryItem.quantity})") },
                                onClick = {
                                    onPantryItemSelected(pantryItem)
                                    expandedPantryDropdown = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Keines davon / Manuell benötigt") },
                            onClick = {
                                onPantryItemSelected(null)
                                // onAddToShoppingListChange(true) // This is handled by onPantryItemSelected
                                expandedPantryDropdown = false
                            }
                        )
                    }
                }

                // Show "Ausreichend vorhanden?" checkbox only if a pantry item is actually selected
                AnimatedVisibility(visible = itemState.selectedPantryItem != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { onConfirmEnoughInPantryChange(!itemState.userConfirmedEnoughInPantry) }
                    ) {
                        Checkbox(
                            checked = itemState.userConfirmedEnoughInPantry,
                            onCheckedChange = onConfirmEnoughInPantryChange
                        )
                        Text("Ausreichend von diesem Vorratsitem vorhanden?", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
