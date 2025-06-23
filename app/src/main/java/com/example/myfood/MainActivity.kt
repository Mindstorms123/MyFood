package com.example.myfood

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel // Für hiltViewModel in Screens
import androidx.lifecycle.ViewModelProvider
// import androidx.lifecycle.viewmodel.compose.viewModel // Wird für RecipeViewModel verwendet
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.myfood.navigation.Screen
import com.example.myfood.ui.recipe.RecipeDetailScreen
import com.example.myfood.ui.recipe.RecipeListScreen
import com.example.myfood.ui.recipe.RecipeViewModel
import com.example.myfood.ui.shoppinglist.ShoppingListScreen // *** IMPORT FÜR SHOPPING LIST SCREEN ***
// import com.example.myfood.ui.shoppinglist.ShoppingListViewModel // Importieren, wenn nicht per Hilt im Screen geholt
import com.example.myfood.ui.theme.MyFoodTheme
import dagger.hilt.android.AndroidEntryPoint // *** FÜR HILT BENÖTIGT ***

@AndroidEntryPoint // *** HINZUFÜGEN, WENN DU HILT VERWENDEST ***
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FoodViewModel wird hier instanziiert, da es eine Factory verwendet.
        // Wenn ShoppingListViewModel auch eine Factory bräuchte, müsste es ähnlich gemacht werden.
        // Ansonsten kann es mit hiltViewModel() direkt im Screen oder hier mit viewModel() geholt werden.
        val foodViewModelFactory = FoodViewModelFactory(application)
        val foodViewModel = ViewModelProvider(this, foodViewModelFactory)[FoodViewModel::class.java]

        setContent {
            MyFoodTheme {
                // RecipeViewModel wird hier mit viewModel() geholt.
                // ShoppingListViewModel könnte hier auch geholt werden, wenn es nicht per Hilt im Screen selbst geschieht.
                val recipeViewModel: RecipeViewModel = hiltViewModel() // Oder viewModel() wenn nicht Hilt-spezifisch für die Activity

                AppNavigation(
                    foodViewModel = foodViewModel,
                    recipeViewModel = recipeViewModel
                    // shoppingListViewModel = shoppingListViewModel // Übergeben, wenn hier instanziiert
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// AppNavigation muss die ViewModels akzeptieren, die es an die Screens weitergibt.
// Wenn ShoppingListViewModel direkt im ShoppingListScreen mit hiltViewModel() geholt wird,
// muss es hier nicht übergeben werden.
fun AppNavigation(
    foodViewModel: FoodViewModel,
    recipeViewModel: RecipeViewModel
    // shoppingListViewModel: ShoppingListViewModel // Parameter hinzufügen, falls benötigt
) {
    val navController = rememberNavController()
    // *** SHOPPINGLIST ZUR LISTE DER NAVIGATIONSELEMENTE HINZUFÜGEN ***
    val items = listOf(
        Screen.FoodList,
        Screen.Recipes,
        Screen.ShoppingList // NEU
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
                FoodScreen(viewModel = foodViewModel)
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

            // *** NEUE ROUTE FÜR DEN SHOPPINGLISTSCREEN HINZUFÜGEN ***
            composable(Screen.ShoppingList.route) {
                // Wenn ShoppingListViewModel per Hilt direkt im Screen geholt wird:
                ShoppingListScreen()

                // Wenn ShoppingListViewModel hier übergeben werden soll:
                // ShoppingListScreen(
                //     navController = navController,
                //     viewModel = shoppingListViewModel // shoppingListViewModel müsste an AppNavigation übergeben werden
                // )
            }
        }
    }
}