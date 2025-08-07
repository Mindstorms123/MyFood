package com.example.myfood

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
// import android.widget.Toast // Nicht mehr direkt in AppNavigation für den FAB gebraucht
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage // <<< HINZUGEFÜGT für Icon-Fallback
// import androidx.compose.material.icons.filled.PlayArrow // Nicht mehr für den FAB gebraucht
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
// import androidx.compose.ui.platform.LocalContext // Nicht mehr direkt in AppNavigation für den FAB gebraucht
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
// import androidx.work.OneTimeWorkRequestBuilder // Nicht mehr für den FAB gebraucht
// import androidx.work.WorkManager // Nicht mehr für den FAB gebraucht
import com.example.myfood.navigation.Screen // Stelle sicher, dass Screen.Settings hier definiert ist
import com.example.myfood.ui.recipe.RecipeDetailScreen
import com.example.myfood.ui.recipe.RecipeListScreen
import com.example.myfood.ui.recipe.RecipeViewModel
import com.example.myfood.ui.shoppinglist.ShoppingListScreen
import com.example.myfood.ui.EditItemScreen
// Import für deinen neuen SettingsScreen und ggf. ViewModel
import com.example.myfood.ui.settings.SettingsScreen // <<< HINZUGEFÜGT (Passe Pfad an)
// import com.example.myfood.ui.settings.SettingsViewModel // Wird ggf. direkt im Composable geholt
import com.example.myfood.ui.theme.MyFoodTheme
// import com.example.myfood.workers.ExpiryCheckWorker // Nicht mehr für den FAB gebraucht
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // --- Start: Code für Benachrichtigungsberechtigung ---
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
    // --- Ende: Code für Benachrichtigungsberechtigung ---

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        // Beachte: FoodViewModelFactory ist nicht Hilt-konform.
        // Wenn du Hilt durchgängig nutzt, solltest du FoodViewModel auch über Hilt bereitstellen.
        // Für dieses Beispiel lasse ich es so, wie es war, aber zur Kenntnisnahme.
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

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    foodViewModel: FoodViewModel,
    recipeViewModel: RecipeViewModel
    // Das SettingsViewModel wird direkt im Composable-Block für Settings geholt, falls mit Hilt
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.FoodList,
        Screen.Recipes,
        Screen.ShoppingList,
        Screen.Settings // <<< HIER HINZUGEFÜGT
    )
    // val context = LocalContext.current // Nicht mehr hier benötigt, wenn FAB entfernt ist

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon ?: Icons.Filled.BrokenImage, // Fallback-Icon
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
        },
        // FAB entfernt, da er nicht mehr benötigt wird oder an anderer Stelle platziert werden kann.
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.FoodList.route, // Dein gewünschter Startbildschirm
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.FoodList.route) {
                FoodScreen(
                    navController = navController,
                    viewModel = foodViewModel // foodViewModel wird übergeben
                )
            }
            composable(Screen.Recipes.route) {
                RecipeListScreen(
                    navController = navController,
                    foodViewModel = foodViewModel, // foodViewModel wird übergeben
                    recipeViewModel = recipeViewModel // recipeViewModel wird übergeben
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
                        recipeViewModel = recipeViewModel // recipeViewModel wird übergeben
                    )
                } else {
                    Text("Fehler: Rezept-ID nicht gefunden.") // Fehlerbehandlung
                }
            }
            composable(Screen.ShoppingList.route) {
                ShoppingListScreen() // Annahme: ShoppingListScreen existiert und holt ggf. sein ViewModel selbst
            }
            composable(Screen.EditItemScreen.route) { // Sicherstellen, dass die Route mit Screen.EditItemScreen.route übereinstimmt
                EditItemScreen(
                    foodViewModel = foodViewModel, // foodViewModel wird übergeben
                    onItemSaved = {
                        navController.popBackStack() // Zurück zum vorherigen Bildschirm
                    }
                )
            }

            // --- NEUER COMPOSABLE-BLOCK FÜR EINSTELLUNGEN ---
            composable(Screen.Settings.route) {
                SettingsScreen(
                    // viewModel = hiltViewModel() // So würdest du es mit Hilt machen
                    // Wenn SettingsScreen sein ViewModel nicht über Parameter erwartet,
                    // sondern intern mit hiltViewModel() holt, dann reicht:
                    // SettingsScreen()
                )
            }
        }
    }
}

