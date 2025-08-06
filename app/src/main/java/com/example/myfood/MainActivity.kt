package com.example.myfood

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.myfood.navigation.Screen
import com.example.myfood.ui.recipe.RecipeDetailScreen
import com.example.myfood.ui.recipe.RecipeListScreen
import com.example.myfood.ui.recipe.RecipeViewModel
import com.example.myfood.ui.shoppinglist.ShoppingListScreen
// *** WICHTIG: Importiere hier deinen Screen für die Bearbeitung nach dem Scan ***
// Beispiel: Annahme, dein Screen heißt EditItemScreen und liegt im ui.edititem Paket
import com.example.myfood.ui.EditItemScreen // <<< PASSE DIESEN IMPORT AN DEINEN SCREEN AN
import com.example.myfood.ui.theme.MyFoodTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val foodViewModelFactory = FoodViewModelFactory(application)
        val foodViewModel = ViewModelProvider(this, foodViewModelFactory)[FoodViewModel::class.java]

        setContent {
            MyFoodTheme {
                val recipeViewModel: RecipeViewModel = hiltViewModel()

                AppNavigation(
                    foodViewModel = foodViewModel,
                    recipeViewModel = recipeViewModel
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    foodViewModel: FoodViewModel,
    recipeViewModel: RecipeViewModel
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.FoodList,
        Screen.Recipes,
        Screen.ShoppingList
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.FoodList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.FoodList.route) {
                FoodScreen(
                    navController = navController,
                    viewModel = foodViewModel
                )
            }
            composable(Screen.Recipes.route) {
                RecipeListScreen(
                    navController = navController,
                    foodViewModel = foodViewModel,
                    recipeViewModel = recipeViewModel
                )
            }
            composable(
                route = Screen.RecipeDetail.route,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getString("recipeId")
                if (recipeId != null) {
                    RecipeDetailScreen(
                        recipeId = recipeId,
                        navController = navController,
                        recipeViewModel = recipeViewModel
                    )
                } else {
                    Text("Fehler: Rezept-ID nicht gefunden.")
                }
            }

            composable(Screen.ShoppingList.route) {
                ShoppingListScreen()
                // Falls ShoppingListScreen doch einen NavController braucht:
                // ShoppingListScreen(navController = navController)
            }

            // Route für den EditItemScreen
            composable(Screen.EditItemScreen.route) { // Diese Route wird von FoodScreen aufgerufen
                // *** HIER DEINEN BILDSCHIRM FÜR DIE BEARBEITUNG EINFÜGEN ***
                // Stelle sicher, dass der Screen (z.B. EditItemScreen oder EditScannedItemScreen)
                // importiert wurde und die erwarteten Parameter erhält.
                EditItemScreen( // <<< PASSE DEN NAMEN UND DIE PARAMETER AN DEINEN SCREEN AN
                    foodViewModel = foodViewModel,
                    onItemSaved = {
                        navController.popBackStack() // Navigiert zurück nach dem Speichern
                    }
                )
            }
        }
    }
}