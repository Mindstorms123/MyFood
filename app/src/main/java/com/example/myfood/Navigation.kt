package com.example.myfood.navigation // Oder com.example.myfood.ui.theme, wenn sie dort liegt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List // Für Vorrat
import androidx.compose.material.icons.filled.RestaurantMenu // Für Rezepte
import androidx.compose.material.icons.filled.ShoppingCart // NEU: Icon für Einkaufsliste
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
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
        title = "Rezeptdetails"
        // icon = null // Kein Icon für Detailseiten ist üblich
    ) {
        fun createRoute(recipeId: String) = "recipe_detail/$recipeId"
    }

    // NEU: Eintrag für die Einkaufsliste
    object ShoppingList : Screen(
        route = "shoppinglist",
        title = "Einkaufsliste",
        icon = Icons.Filled.ShoppingCart // Verwende ein passendes Icon
    )
}
