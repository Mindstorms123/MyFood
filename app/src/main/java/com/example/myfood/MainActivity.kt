package com.example.myfood

// --- WICHTIGE IMPORTE - BITTE PFADE ÜBERPRÜFEN UND ANPASSEN ---
// Wenn dein Screen für das Bearbeiten von Food-Items 'EditItemScreen' heißt:
// Wenn dein Screen 'EditFoodItemScreen' heißt (passend zu Navigation.kt):
// import com.example.myfood.ui.EditFoodItemScreen // Alternativer Import
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myfood.navigation.Screen
import com.example.myfood.ui.EditItemScreen
import com.example.myfood.ui.recipe.AddEditRecipeScreen
import com.example.myfood.ui.recipe.RecipeDetailScreen
import com.example.myfood.ui.recipe.RecipeListScreen
import com.example.myfood.ui.recipe.RecipeViewModel
import com.example.myfood.ui.settings.SettingsScreen
import com.example.myfood.ui.shoppinglist.ShoppingListScreen
import com.example.myfood.ui.theme.MyFoodTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission granted.")
            } else {
                Log.w("MainActivity", "POST_NOTIFICATIONS permission denied.")
            }
        }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("MainActivity", "POST_NOTIFICATIONS permission already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.i("MainActivity", "Showing rationale for POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.d("MainActivity", "Requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        val foodViewModelFactory = FoodViewModelFactory(application)
        val foodViewModel = ViewModelProvider(this, foodViewModelFactory)[FoodViewModel::class.java]

        setContent {
            MyFoodTheme {
                AppNavigation(
                    foodViewModel = foodViewModel,
                    recipeViewModel = hiltViewModel()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    foodViewModel: FoodViewModel,
    recipeViewModel: RecipeViewModel
) {
    val navController = rememberNavController()
    // --- ANPASSUNG HIER ---
    // Verwende die Namen aus deiner Navigation.kt (z.B. Screen.RecipeList statt Screen.Recipes)
    val items = listOf(
        Screen.FoodList,
        Screen.RecipeList, // Geändert von Screen.Recipes
        Screen.ShoppingList,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon ?: Icons.Filled.BrokenImage,
                                contentDescription = screen.title
                            )
                        },
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
                FoodScreen( // Stelle sicher, dass FoodScreen korrekt importiert ist
                    navController = navController,
                    viewModel = foodViewModel
                )
            }

            // --- ANPASSUNG HIER ---
            // Verwende die Route aus deiner Navigation.kt (z.B. Screen.RecipeList.route)
            composable(Screen.RecipeList.route) { // Geändert von Screen.Recipes.route
                RecipeListScreen(
                    navController = navController,
                    // foodViewModel = foodViewModel, // Entfernt, da Fehler "No parameter with name 'foodViewModel' found"
                    // Füge es hinzu, wenn dein RecipeListScreen diesen Parameter hat.
                    recipeViewModel = recipeViewModel
                )
            }

            composable(
                route = Screen.RecipeDetail.route,
                arguments = listOf(navArgument(Screen.RecipeDetail.NAV_ARGUMENT_RECIPE_ID) { // "recipeId"
                    type = NavType.StringType // Beibehaltung von StringType für RecipeDetail
                })
            ) { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getString(Screen.RecipeDetail.NAV_ARGUMENT_RECIPE_ID)
                if (recipeId != null) {
                    RecipeDetailScreen(
                        recipeId = recipeId, // Übergib String
                        navController = navController,
                        recipeViewModel = recipeViewModel
                    )
                } else {
                    Text("Fehler: Rezept-ID nicht gefunden.")
                }
            }

            composable(
                route = Screen.AddEditRecipe.route,
                arguments = listOf(
                    navArgument(Screen.AddEditRecipe.NAV_ARGUMENT_RECIPE_ID) { // "recipeId"
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { backStackEntry ->
                val recipeIdArg = backStackEntry.arguments?.getLong(Screen.AddEditRecipe.NAV_ARGUMENT_RECIPE_ID)
                AddEditRecipeScreen(
                    navController = navController,
                    recipeId = if (recipeIdArg == 0L) null else recipeIdArg,
                    viewModel = recipeViewModel
                )
            }

            composable(Screen.ShoppingList.route) {
                ShoppingListScreen()
            }

            // --- ANPASSUNG HIER ---
            // Verwende die Route und den Screen-Namen aus deiner Navigation.kt
            // Wenn Navigation.kt Screen.EditFoodItem hat, dann Screen.EditFoodItem.route
            // und der Composable-Aufruf sollte EditFoodItemScreen sein (oder wie auch immer dein Composable heißt)
            composable(Screen.EditFoodItem.route) { // Angenommen, es ist Screen.EditFoodItem in Navigation.kt
                // und dein Composable heißt EditItemScreen
                EditItemScreen( // Stelle sicher, dass EditItemScreen korrekt importiert ist
                    // Falls dein Composable EditFoodItemScreen heißt, ändere den Namen hier.
                    foodViewModel = foodViewModel,
                    onItemSaved = {
                        navController.popBackStack()
                    }
                )
                // Wenn Screen.EditFoodItem Argumente benötigt (z.B. foodItemId), musst du hier
                // den arguments-Block hinzufügen, ähnlich wie bei AddEditRecipeScreen.
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

