package com.example.myfood

import android.Manifest // <<< HINZUGEFÜGT
import android.content.pm.PackageManager // <<< HINZUGEFÜGT
import android.os.Build
import android.os.Bundle
import android.util.Log // <<< HINZUGEFÜGT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts // <<< HINZUGEFÜGT
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat // <<< HINZUGEFÜGT
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
import com.example.myfood.ui.EditItemScreen
import com.example.myfood.ui.theme.MyFoodTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // --- Start: Code für Benachrichtigungsberechtigung ---
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission granted.")
                // Berechtigung erteilt. Du kannst jetzt Benachrichtigungen planen/senden.
                // Dein ExpiryCheckWorker wird nun auch Benachrichtigungen senden können.
                // Hier könntest du z.B. den Worker neu starten oder sicherstellen, dass er läuft,
                // falls er aufgrund fehlender Berechtigungen vorher nicht korrekt gestartet wurde.
            } else {
                Log.w("MainActivity", "POST_NOTIFICATIONS permission denied.")
                // Berechtigung verweigert. Informiere den Nutzer, dass Benachrichtigungen nicht funktionieren werden.
                // Du könntest hier einen Snackbar oder Toast anzeigen.
            }
        }

    private fun askNotificationPermission() {
        // Diese Funktion ist nur für Android 13 (API 33, TIRAMISU) und höher relevant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("MainActivity", "POST_NOTIFICATIONS permission already granted.")
                    // Berechtigung bereits vorhanden, alles gut.
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Zeige dem Nutzer eine informative UI, warum diese Berechtigung benötigt wird.
                    // Dies könnte ein Dialog sein, der erklärt, dass die App Benachrichtigungen
                    // für ablaufende Lebensmittel sendet. Nach Bestätigung des Dialogs
                    // rufst du dann requestPermissionLauncher.launch(...) auf.
                    // Für dieses Beispiel fordern wir die Berechtigung direkt an,
                    // aber in einer Produktiv-App ist eine Erklärung hier besser.
                    Log.i("MainActivity", "Showing rationale for POST_NOTIFICATIONS permission.")
                    // Hier könntest du einen Dialog anzeigen. Fürs Erste fordern wir direkt an:
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Die Berechtigung wurde noch nie angefordert oder der Nutzer hat
                    // "Nicht mehr fragen" ausgewählt, nachdem er sie zuvor verweigert hat.
                    Log.d("MainActivity", "Requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    // --- Ende: Code für Benachrichtigungsberechtigung ---

    @RequiresApi(Build.VERSION_CODES.O) // Diese Annotation ist hier aufgrund von FoodViewModelFactory etc.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fordere die Berechtigung für Benachrichtigungen an (für Android 13+)
        askNotificationPermission() // <<< HINZUGEFÜGT

        val foodViewModelFactory = FoodViewModelFactory(application)
        val foodViewModel = ViewModelProvider(this, foodViewModelFactory)[FoodViewModel::class.java]

        setContent {
            MyFoodTheme {
                // val recipeViewModel: RecipeViewModel = hiltViewModel() // Wird aktuell nicht direkt in AppNavigation verwendet
                // aber kann hier bleiben, falls später benötigt.

                AppNavigation(
                    foodViewModel = foodViewModel,
                    recipeViewModel = hiltViewModel() // Hilt ViewModel direkt hier holen
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O) // Diese Annotation kommt von der Verwendung von LocalDate etc. in ViewModels
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    foodViewModel: FoodViewModel,
    recipeViewModel: RecipeViewModel // Sicherstellen, dass dies auch übergeben wird
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
                // FoodScreen benötigt jetzt auch den recipeViewModel, wenn er dort verwendet wird.
                // Wenn FoodScreen den RecipeViewModel nicht benötigt, kannst du ihn hier weglassen.
                FoodScreen(
                    navController = navController,
                    viewModel = foodViewModel
                    // recipeViewModel = recipeViewModel // <<< Füge dies hinzu, falls FoodScreen es braucht
                )
            }
            composable(Screen.Recipes.route) {
                RecipeListScreen(
                    navController = navController,
                    foodViewModel = foodViewModel, // RecipeListScreen benötigt vielleicht auch FoodViewModel
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
                    // Besser ist es, hier eine Fehlerbehandlung zu haben oder
                    // sicherzustellen, dass recipeId nie null ist, wenn diese Route erreicht wird.
                    Text("Fehler: Rezept-ID nicht gefunden.")
                }
            }

            composable(Screen.ShoppingList.route) {
                ShoppingListScreen() // ShoppingListScreen benötigt aktuell keine ViewModels oder NavController hier
            }

            // Route für den EditItemScreen
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
