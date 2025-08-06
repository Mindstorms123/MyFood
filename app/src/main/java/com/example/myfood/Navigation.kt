package com.example.myfood.navigation // Oder com.example.myfood.ui.theme, wenn sie dort liegt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List // Für Vorrat
import androidx.compose.material.icons.filled.RestaurantMenu // Für Rezepte
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String, // Titel kann für TopAppBar im jeweiligen Screen genutzt werden
    val icon: ImageVector? = null // Icon primär für BottomNavigation oder Drawer
) {
    object FoodList : Screen(
        route = "foodlist",
        title = "Vorrat",
        icon = Icons.Filled.List
    )

    object Recipes : Screen(
        route = "recipes",
        title = "Rezepte",
        icon = Icons.Filled.RestaurantMenu
    )

    object RecipeDetail : Screen(
        route = "recipe_detail/{recipeId}",
        title = "Rezeptdetails" // Titel für die TopAppBar des Detail-Screens
        // icon = null // Kein Icon für Detailseiten in BottomNav ist üblich
    ) {
        fun createRoute(recipeId: String) = "recipe_detail/$recipeId"
    }

    object ShoppingList : Screen(
        route = "shoppinglist",
        title = "Einkaufsliste",
        icon = Icons.Filled.ShoppingCart
    )

    // --- NEU: Route für den Bearbeitungsbildschirm ---
    object EditItemScreen : Screen(
        route = "edit_item", // Eindeutige Route
        title = "Produkt bearbeiten/hinzufügen" // Allgemeiner Titel für die TopAppBar dieses Screens
        // icon = null // Wird typischerweise nicht in einer Hauptnavigation angezeigt
    )
}
