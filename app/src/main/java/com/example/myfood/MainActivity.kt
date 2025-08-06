package com.example.myfood

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast // <<< HINZUGEFÜGT für Toast-Nachricht
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons // <<< HINZUGEFÜGT
import androidx.compose.material.icons.filled.PlayArrow // <<< HINZUGEFÜGT (Beispiel-Icon)
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // <<< HINZUGEFÜGT für Context im Composable
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.work.OneTimeWorkRequestBuilder // <<< HINZUGEFÜGT
import androidx.work.WorkManager // <<< HINZUGEFÜGT
import com.example.myfood.navigation.Screen
import com.example.myfood.ui.recipe.RecipeDetailScreen
import com.example.myfood.ui.recipe.RecipeListScreen
import com.example.myfood.ui.recipe.RecipeViewModel
import com.example.myfood.ui.shoppinglist.ShoppingListScreen
import com.example.myfood.ui.EditItemScreen
import com.example.myfood.ui.theme.MyFoodTheme
import com.example.myfood.workers.ExpiryCheckWorker // <<< HINZUGEFÜGT (Passe den Pfad ggf. an)
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
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.FoodList,
        Screen.Recipes,
        Screen.ShoppingList
    )
    val context = LocalContext.current // <<< Context für WorkManager und Toast holen

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
        },
        // --- Start: Floating Action Button zum manuellen Triggern des Workers ---
        /*floatingActionButton = {
            FloatingActionButton(onClick = {
                Log.d("MANUAL_TRIGGER", "FAB clicked: Enqueuing ExpiryCheckWorker manually.")
                val oneTimeWorkRequest = OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
                    // Hier könntest du initiale Delays setzen oder Constraints, falls nötig für den Test.
                    // Für einen einfachen Test ohne spezielle Constraints:
                    .build()
                WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
                Toast.makeText(context, "ExpiryCheckWorker manuell gestartet (siehe Logcat)", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Trigger ExpiryCheckWorker")
            }
        }*/
        // --- Ende: Floating Action Button ---
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
            }
            composable(Screen.EditItemScreen.route) {
                EditItemScreen(
                    foodViewModel = foodViewModel,
                    onItemSaved = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

