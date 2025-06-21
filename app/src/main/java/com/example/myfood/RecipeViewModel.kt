package com.example.myfood.ui.recipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
// import androidx.compose.ui.geometry.isEmpty // Entfernt, da nicht verwendet
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.FoodItem // Sicherstellen, dass der Import korrekt ist
import com.example.myfood.data.recipe.RecipeApiService
import com.example.myfood.data.recipe.RecipeDetail // Deine RecipeDetail-Klasse aus RecipeData.kt
import com.example.myfood.data.recipe.RecipeSummary // Deine RecipeSummary-Klasse aus RecipeData.kt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
// kotlin.text.*-Importe sind oft nicht nötig, wenn sie direkt verwendet werden, aber schaden auch nicht.
// import kotlin.text.any // Standard-Kotlin, expliziter Import oft nicht nötig
// import kotlin.text.contains // Standard-Kotlin
// import kotlin.text.lowercase // Standard-Kotlin
// import kotlin.text.mapNotNull // Standard-Kotlin

// UI State sealed interfaces (RecipeListUiState, RecipeDetailUiState)
sealed interface RecipeListUiState {
    object Loading : RecipeListUiState
    data class Success(val recipes: List<RecipeSummary>) : RecipeListUiState
    data class Error(val message: String) : RecipeListUiState
}

sealed interface RecipeDetailUiState {
    object Loading : RecipeDetailUiState
    data class Success(val recipe: RecipeDetail) : RecipeDetailUiState
    data class Error(val message: String) : RecipeDetailUiState
}

class RecipeViewModel : ViewModel() {

    var recipeListUiState: RecipeListUiState by mutableStateOf(RecipeListUiState.Loading)
        private set
    var recipeDetailUiState: RecipeDetailUiState by mutableStateOf(RecipeDetailUiState.Loading)
        private set
    var suggestedRecipesUiState: RecipeListUiState by mutableStateOf(RecipeListUiState.Loading)
        private set

    init {
        loadRandomRecipes() // Lade initiale "populäre" Rezepte
        println("DEBUG_VM: RecipeViewModel initialized.")
    }

    fun loadRandomRecipes() {
        println("DEBUG_VM: loadRandomRecipes called")
        viewModelScope.launch {
            recipeListUiState = RecipeListUiState.Loading
            val result = RecipeApiService.getRandomRecipes()
            recipeListUiState = result.fold(
                onSuccess = {
                    println("DEBUG_VM: loadRandomRecipes success, count: ${it.size}")
                    RecipeListUiState.Success(it)
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: loadRandomRecipes failed: ${it.message}")
                    RecipeListUiState.Error(it.message ?: "Unbekannter Fehler beim Laden zufälliger Rezepte")
                }
            )
        }
    }

    fun loadRecipeDetails(recipeId: String) {
        println("DEBUG_VM: loadRecipeDetails called for ID: $recipeId")
        viewModelScope.launch {
            recipeDetailUiState = RecipeDetailUiState.Loading
            val result = RecipeApiService.getRecipeDetails(recipeId)
            recipeDetailUiState = result.fold(
                onSuccess = {
                    println("DEBUG_VM: loadRecipeDetails success for ID: $recipeId, Title: ${it.title}")
                    RecipeDetailUiState.Success(it)
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: loadRecipeDetails failed for ID $recipeId: ${it.message}")
                    RecipeDetailUiState.Error(it.message ?: "Unbekannter Fehler beim Laden der Rezeptdetails")
                }
            )
        }
    }

    fun loadSuggestedRecipes(
        pantryItems: List<FoodItem>,
        maxInitialRecipesToConsider: Int = 20,
        maxDetailCalls: Int = 10
    ) {
        println("DEBUG_VM: loadSuggestedRecipes called. Pantry size: ${pantryItems.size}")
        if (pantryItems.isEmpty()) {
            println("DEBUG_VM: loadSuggestedRecipes - Pantry is empty, returning empty suggestions.")
            suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
            return
        }

        viewModelScope.launch {
            suggestedRecipesUiState = RecipeListUiState.Loading

            // 1. Hole deutsche Zutatennamen aus dem Vorrat
            val germanPantryIngredientNames = pantryItems.mapNotNull {
                it.name?.lowercase()?.trim()?.takeIf { name -> name.isNotBlank() }
            }.distinct()
            println("DEBUG_VM: loadSuggestedRecipes - GERMAN PANTRY NAMES: $germanPantryIngredientNames")

            if (germanPantryIngredientNames.isEmpty()) {
                println("DEBUG_VM: loadSuggestedRecipes - No German pantry names to process.")
                suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                return@launch
            }

            // 2. Übersetze die deutschen Namen ins Englische (asynchron und parallel)
            //    unter Verwendung der neuen Kaskaden-Logik im RecipeApiService
            val pantryIngredientNamesForApiDeferred = germanPantryIngredientNames.map { germanName ->
                async(Dispatchers.IO) { // Wichtig: Netzwerk/CPU-intensive Aufgaben auf IO-Dispatcher
                    RecipeApiService.translateIngredient(
                        ingredientName = germanName,
                        useLibreTranslateAsFallback = true, // Hier entscheiden, ob LibreTranslate als 2. API-Fallback genutzt wird
                        myMemoryEmail = null // Optional: "deine.email@example.com" für MyMemory
                    )
                }
            }
            val pantryIngredientNamesForApi = pantryIngredientNamesForApiDeferred.awaitAll()
                .filter { it.isNotBlank() } // Entferne leere Strings
                .distinct() // Stelle sicher, dass jede Zutat nur einmal vorkommt
            println("DEBUG_VM: loadSuggestedRecipes - TRANSLATED PANTRY NAMES FOR API (via translateIngredient): $pantryIngredientNamesForApi")

            if (pantryIngredientNamesForApi.isEmpty()) {
                println("DEBUG_VM: loadSuggestedRecipes - Translated pantry ingredients for API are empty.")
                suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                return@launch
            }

            // 3. Hole initiale Rezept-Summaries von TheMealDB mit den übersetzten englischen Namen
            val initialRecipeSummariesResult = RecipeApiService.findRecipesContainingAnyOfIngredients(
                ingredientNames = pantryIngredientNamesForApi,
                maxIngredientsToQuery = 3, // Wie viele der Top-Vorratszutaten für die Suche verwenden
                maxResultsPerIngredient = 7  // Wie viele Rezepte pro Zutat von TheMealDB holen
            )

            initialRecipeSummariesResult.fold(
                onSuccess = { recipeSummaries ->
                    println("DEBUG_VM: loadSuggestedRecipes - INITIAL RECIPE SUMMARIES from TheMealDB (count: ${recipeSummaries.size}): ${recipeSummaries.map { it.title }}")
                    if (recipeSummaries.isEmpty()) {
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                        return@fold
                    }

                    // 4. Wähle eine Untermenge für Detailabfragen aus und hole Details (asynchron)
                    val recipesToGetDetailsFor = recipeSummaries.shuffled().take(maxInitialRecipesToConsider.coerceAtMost(maxDetailCalls))
                    println("DEBUG_VM: loadSuggestedRecipes - Recipes to get details for (count: ${recipesToGetDetailsFor.size}): ${recipesToGetDetailsFor.map { it.title }}")

                    if (recipesToGetDetailsFor.isEmpty()){
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                        return@fold
                    }

                    val recipesWithDetailsDeferred = recipesToGetDetailsFor.map { summary ->
                        async(Dispatchers.IO) { // Netzwerkaufruf auf IO-Dispatcher
                            RecipeApiService.getRecipeDetails(summary.id).fold(
                                onSuccess = { detail -> Pair(summary, detail) },
                                onFailure = {
                                    println("DEBUG_VM_ERROR: loadSuggestedRecipes - Failed to get details for ${summary.title}: ${it.message}")
                                    null // Erlaube null, um fehlerhafte Aufrufe zu überspringen
                                }
                            )
                        }
                    }
                    val detailedRecipesPairs = recipesWithDetailsDeferred.awaitAll().filterNotNull()
                    println("DEBUG_VM: loadSuggestedRecipes - DETAILED RECIPE PAIRS (count: ${detailedRecipesPairs.size}): ${detailedRecipesPairs.map { it.first.title }}")

                    if (detailedRecipesPairs.isEmpty()) {
                        println("DEBUG_VM: loadSuggestedRecipes - No details could be fetched. Fallback to initial summaries if any (count: ${recipesToGetDetailsFor.size})")
                        suggestedRecipesUiState = if (recipesToGetDetailsFor.isNotEmpty()) RecipeListUiState.Success(recipesToGetDetailsFor) else RecipeListUiState.Success(emptyList())
                        return@fold
                    }

                    // 5. Zähle Übereinstimmungen und sortiere (Englisch vs. Englisch)
                    val sortedRecipeSummaries = detailedRecipesPairs.mapNotNull { (summary, detail) ->
                        // Wichtig: countMatchingIngredients vergleicht die englischen Rezeptdetails
                        // mit den ins Englische übersetzten Vorratsnamen (pantryIngredientNamesForApi)
                        val matchCount = countMatchingIngredients(detail, pantryIngredientNamesForApi)
                        if (matchCount > 0) {
                            println("DEBUG_VM: loadSuggestedRecipes - Recipe: ${summary.title}, MatchCount: $matchCount")
                            Triple(summary, detail, matchCount) // Behalte summary, detail und matchCount für die Sortierung
                        } else {
                            println("DEBUG_VM: loadSuggestedRecipes - Recipe: ${summary.title}, MatchCount: 0, SKIPPING")
                            null // Entferne Rezepte ohne Übereinstimmung
                        }
                    }
                        .sortedByDescending { it.third } // Sortiere nach Anzahl der Übereinstimmungen
                        .map { it.first } // Nimm nur die RecipeSummary für die UI

                    println("DEBUG_VM: loadSuggestedRecipes - SORTED SUGGESTED RECIPES (count: ${sortedRecipeSummaries.size}): ${sortedRecipeSummaries.map { it.title }}")
                    suggestedRecipesUiState = RecipeListUiState.Success(sortedRecipeSummaries)
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: loadSuggestedRecipes - Failed in initialRecipeSummariesResult (TheMealDB): ${it.message}")
                    suggestedRecipesUiState = RecipeListUiState.Error(it.message ?: "Fehler beim Laden der Rezeptvorschläge")
                }
            )
        }
    }

    /**
     * Vergleicht die (englischen) Zutaten eines Rezepts mit den (ins Englische übersetzten) Vorratszutaten.
     */
    private fun countMatchingIngredients(recipeDetail: RecipeDetail, translatedPantryIngredientsEN: List<String>): Int {
        if (translatedPantryIngredientsEN.isEmpty()) return 0

        // Extrahiere und normalisiere die englischen Zutatennamen aus dem Rezeptdetail
        // Korrektur: Verwende extendedIngredients gemäß deiner RecipeDetail-Definition
        val recipeIngredientsEN = recipeDetail.extendedIngredients.mapNotNull {
            it.name.lowercase().trim().takeIf { name -> name.isNotBlank() }
        }.distinct()

        if (recipeIngredientsEN.isEmpty()) {
            println("DEBUG_VM_MATCHING: Recipe '${recipeDetail.title}' has no processable ingredients from details (extendedIngredients was empty or all names were blank).")
            return 0
        }

        println("DEBUG_VM_MATCHING for Recipe '${recipeDetail.title}': Recipe Ingredients (EN from Detail's extendedIngredients): $recipeIngredientsEN, TRANSLATED Pantry Ingredients (EN): $translatedPantryIngredientsEN")

        var matchCount = 0
        for (pantryIngEN in translatedPantryIngredientsEN) { // Bereits übersetzte englische Vorratszutat
            if (recipeIngredientsEN.any { recipeIngEN ->    // Englische Zutat aus dem Rezeptdetail
                    recipeIngEN == pantryIngEN ||
                            recipeIngEN.contains(pantryIngEN) || // z.B. "chicken breast" (Rezept) enthält "chicken" (Vorrat)
                            pantryIngEN.contains(recipeIngEN)    // z.B. "chicken" (Vorrat) ist in "chicken breast" (Rezept)
                }) {
                matchCount++
            }
        }
        println("DEBUG_VM_MATCHING for Recipe '${recipeDetail.title}': MATCH COUNT FOUND: $matchCount")
        return matchCount
    }
}