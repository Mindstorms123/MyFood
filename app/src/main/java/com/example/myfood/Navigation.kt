package com.example.myfood.navigation // Oder com.example.myfood.ui.theme, wenn sie dort liegt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List // *** DIESER IMPORT IST WICHTIG ***
import androidx.compose.material.icons.filled.RestaurantMenu // *** DIESER IMPORT IST WICHTIG ***
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    object FoodList : Screen(
        route = "foodlist",
        title = "Vorrat",
        icon = Icons.Filled.List // Sollte jetzt aufgelöst werden
    )

    object Recipes : Screen(
        route = "recipes",
        title = "Rezepte",
        icon = Icons.Filled.RestaurantMenu // Sollte jetzt aufgelöst werden
    )

    object RecipeDetail : Screen(
        route = "recipe_detail/{recipeId}",
        title = "Rezeptdetails"
        // icon = null
    ) {
        fun createRoute(recipeId: String) = "recipe_detail/$recipeId"
    }
}