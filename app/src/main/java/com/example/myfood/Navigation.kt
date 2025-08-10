package com.example.myfood.navigation // Oder com.example.myfood.ui.theme, wenn sie dort liegt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List // Für Vorrat
import androidx.compose.material.icons.filled.RestaurantMenu // Für Rezepte
import androidx.compose.material.icons.filled.Settings
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

    // Dieser Screen repräsentiert deine Rezeptliste
    // In MainActivity.kt verwendest du "Screen.Recipes" in der BottomNav-Liste.
    // Wenn dieser Eintrag "RecipeList" heißt, musst du es auch in MainActivity.kt anpassen.
    // Ich lasse es hier bei RecipeList für Klarheit, aber stelle sicher, dass es konsistent ist.
    object RecipeList : Screen(
        route = "recipe_list", // Oder "recipes", wenn du das in MainActivity verwendest
        title = "Rezepte",
        icon = Icons.Filled.RestaurantMenu
    )

    // Screen zum Anzeigen der Details eines Rezepts
    object RecipeDetail : Screen(
        route = "recipe_detail/{recipeId}", // recipeId ist ein Pflichtparameter
        title = "Rezeptdetails"
    ) {
        // Erstellt die Route mit der gegebenen Rezept-ID.
        // MainActivity erwartet hier einen String, also passen wir das an.
        fun createRoute(recipeId: String): String = "recipe_detail/$recipeId"
        // Name des Navigationsarguments, das in NavHost verwendet wird (optional, aber gut für Konsistenz)
        const val NAV_ARGUMENT_RECIPE_ID = "recipeId"
    }

    // Screen zum Hinzufügen und Bearbeiten von Rezepten
    object AddEditRecipe : Screen(
        route = "add_edit_recipe?recipeId={recipeId}",
        title = "Rezept erstellen/bearbeiten"
    ) {
        // Erstellt die Route. Wenn recipeId null ist, wird die Route ohne ID-Parameter erstellt (für neue Rezepte).
        // Das Argument recipeId ist Long, da Datenbank-IDs typischerweise Long sind.
        fun createRoute(recipeId: Long? = null): String {
            return if (recipeId != null) {
                "add_edit_recipe?recipeId=$recipeId"
            } else {
                "add_edit_recipe"
            }
        }
        const val NAV_ARGUMENT_RECIPE_ID = "recipeId" // Name des Navigationsarguments
    }


    object ShoppingList : Screen(
        route = "shoppinglist",
        title = "Einkaufsliste",
        icon = Icons.Filled.ShoppingCart
    )

    // Route für den Bearbeitungsbildschirm von Vorrats-Items (nicht Rezepte)
    object EditFoodItem : Screen(
        route = "edit_food_item?foodItemId={foodItemId}",
        title = "Produkt bearbeiten/hinzufügen"
    ) {
        fun createRoute(foodItemId: Long? = null): String {
            return if (foodItemId != null) {
                "edit_food_item?foodItemId=$foodItemId"
            } else {
                "edit_food_item"
            }
        }
        const val NAV_ARGUMENT_FOOD_ITEM_ID = "foodItemId"
    }


    object Settings : Screen(
        route = "settings",
        title = "Einstellungen",
        icon = Icons.Filled.Settings
    )
}
