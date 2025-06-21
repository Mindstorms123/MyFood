package com.example.myfood

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel // Korrekter Import für viewModel()
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.myfood.navigation.Screen // *** KORRIGIERTER IMPORT FÜR SCREEN ***
import com.example.myfood.ui.recipe.RecipeDetailScreen
import com.example.myfood.ui.recipe.RecipeListScreen
import com.example.myfood.ui.recipe.RecipeViewModel // *** KORREKTER IMPORT FÜR RECIPE VIEW MODEL ***
import com.example.myfood.ui.theme.MyFoodTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val foodViewModelFactory = FoodViewModelFactory(application)
        val foodViewModel = ViewModelProvider(this, foodViewModelFactory)[FoodViewModel::class.java]
        // RecipeViewModel wird jetzt INNERHALB von setContent instanziiert

        setContent {
            MyFoodTheme {
                // Instanziiere RecipeViewModel HIER, im @Composable Kontext
                val recipeViewModel: RecipeViewModel = viewModel()

                AppNavigation(
                    foodViewModel = foodViewModel,
                    recipeViewModel = recipeViewModel // *** RECIPE VIEW MODEL ÜBERGEBEN ***
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// AppNavigation muss recipeViewModel als Parameter akzeptieren
fun AppNavigation(foodViewModel: FoodViewModel, recipeViewModel: RecipeViewModel) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.FoodList,
        Screen.Recipes,
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
                    recipeViewModel = recipeViewModel // *** RECIPE VIEW MODEL WEITERGEBEN ***
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
                        recipeViewModel = recipeViewModel // *** RECIPE VIEW MODEL WEITERGEBEN ***
                    )
                } else {
                    Text("Fehler: Rezept-ID nicht gefunden.")
                }
            }
        }
    }
}