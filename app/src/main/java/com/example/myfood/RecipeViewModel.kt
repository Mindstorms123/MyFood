package com.example.myfood.ui.recipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.FoodItem
import com.example.myfood.data.recipe.RecipeApiService // Stellt sicher, dass ApiService hier referenziert wird
import com.example.myfood.data.recipe.RecipeDetail
import com.example.myfood.data.recipe.RecipeSummary
// Importiere deine Ingredient-Klasse, wenn sie separat definiert ist (falls nicht Teil von RecipeDetail)
// import com.example.myfood.data.recipe.Ingredient // Nur wenn Ingredient eine Top-Level-Klasse ist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// UI State sealed interfaces
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

data class RecipeWithScore(
    val summary: RecipeSummary,
    val detail: RecipeDetail,
    val score: Int,
    val matchCount: Int,
    val missingCount: Int,
    val matchedPantryIngredients: List<String>,
    val missingRecipeIngredients: List<String>
)

class RecipeViewModel(
    // Es ist gute Praxis, Abhängigkeiten über den Konstruktor zu injizieren.
    // Für dieses Beispiel wird eine Standardinstanz des Objekts RecipeApiService verwendet.
    private val recipeApiService: RecipeApiService = RecipeApiService
) : ViewModel() {

    var recipeListUiState: RecipeListUiState by mutableStateOf(RecipeListUiState.Loading)
        private set
    var recipeDetailUiState: RecipeDetailUiState by mutableStateOf(RecipeDetailUiState.Loading)
        private set
    var suggestedRecipesUiState: RecipeListUiState by mutableStateOf(RecipeListUiState.Loading)
        private set

    init {
        loadRandomRecipes(translateToGerman = true) // Du kannst steuern, ob die initialen Rezepte übersetzt werden sollen
        println("DEBUG_VM: RecipeViewModel initialized.")
    }

    fun loadRandomRecipes(translateToGerman: Boolean = false) {
        println("DEBUG_VM: loadRandomRecipes called, translateToGerman: $translateToGerman")
        viewModelScope.launch {
            recipeListUiState = RecipeListUiState.Loading
            // recipeApiService.getRandomRecipes erwartet keinen 'translateToGerman' Parameter.
            // Die Übersetzung erfolgt nach dem Abruf, falls gewünscht.
            val result = recipeApiService.getRandomRecipes() // Holt immer die (wahrscheinlich englischen) Originale

            recipeListUiState = result.fold(
                onSuccess = { originalRecipeSummaries ->
                    if (translateToGerman) {
                        println("DEBUG_VM: loadRandomRecipes - Attempting to translate ${originalRecipeSummaries.size} summaries to German.")
                        val translatedSummaries = withContext(Dispatchers.IO) {
                            originalRecipeSummaries.map { summary ->
                                summary.copy(
                                    // Annahme: RecipeSummary hat ein 'title' Feld, das übersetzt werden kann.
                                    // Dein RecipeApiService.translateText kann hier verwendet werden.
                                    title = recipeApiService.translateText(summary.title, sourceLang = "en", targetLang = "de")
                                    // Ggf. andere Felder im Summary übersetzen, falls vorhanden und nötig
                                )
                            }
                        }
                        println("DEBUG_VM: loadRandomRecipes success (translated), count: ${translatedSummaries.size}")
                        RecipeListUiState.Success(translatedSummaries)
                    } else {
                        println("DEBUG_VM: loadRandomRecipes success (original), count: ${originalRecipeSummaries.size}")
                        RecipeListUiState.Success(originalRecipeSummaries)
                    }
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: loadRandomRecipes failed: ${it.message}")
                    RecipeListUiState.Error(it.message ?: "Unknown error loading random recipes")
                }
            )
        }
    }

    fun loadRecipeDetails(recipeId: String, translateToGerman: Boolean = true) {
        println("DEBUG_VM: loadRecipeDetails called for ID: $recipeId, Translate: $translateToGerman")
        viewModelScope.launch {
            recipeDetailUiState = RecipeDetailUiState.Loading
            val result = recipeApiService.getRecipeDetails(recipeId) // Holt das ursprüngliche Detail

            recipeDetailUiState = result.fold(
                onSuccess = { originalRecipeDetail ->
                    if (translateToGerman) {
                        println("DEBUG_VM: loadRecipeDetails - Attempting to translate recipe '${originalRecipeDetail.title}' to German.")
                        val translatedRecipeDetail = translateRecipeDetailForDisplay(originalRecipeDetail)
                        println("DEBUG_VM: loadRecipeDetails success (translated) for ID: $recipeId, Title: ${translatedRecipeDetail.title}")
                        RecipeDetailUiState.Success(translatedRecipeDetail)
                    } else {
                        println("DEBUG_VM: loadRecipeDetails success (original) for ID: $recipeId, Title: ${originalRecipeDetail.title}")
                        RecipeDetailUiState.Success(originalRecipeDetail)
                    }
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: loadRecipeDetails failed for ID $recipeId: ${it.message}")
                    RecipeDetailUiState.Error(it.message ?: "Unknown error loading recipe details")
                }
            )
        }
    }

    private suspend fun translateRecipeDetailForDisplay(recipeDetail: RecipeDetail): RecipeDetail {
        return withContext(Dispatchers.IO) {
            try {
                // Annahme: RecipeDetail hat 'title' und 'instructions'
                val translatedTitle = recipeApiService.translateText(recipeDetail.title, sourceLang = "en", targetLang = "de")
                val translatedInstructions = recipeApiService.translateText(recipeDetail.instructions, sourceLang = "en", targetLang = "de")

                // Annahme: RecipeDetail hat 'category' und 'area' als nullable Strings
                val translatedCategory = recipeDetail.category?.let { recipeApiService.translateText(it, sourceLang = "en", targetLang = "de") }
                val translatedArea = recipeDetail.area?.let { recipeApiService.translateText(it, sourceLang = "en", targetLang = "de") }

                // Annahme: RecipeDetail.extendedIngredients ist List<Ingredient>
                // und Ingredient hat 'name' und 'originalName: String?'
                val translatedIngredients = recipeDetail.extendedIngredients.map { ingredient ->
                    // Wir übersetzen 'ingredient.name', falls es noch nicht übersetzt ist, oder den 'originalName', falls vorhanden.
                    // Die Logik hier geht davon aus, dass 'originalName' die EN Version ist und 'name' die anzuzeigende Version.
                    // Wenn 'name' bereits DE ist (durch toRecipeDetail), ist das ok.
                    // Wenn 'name' EN ist und 'originalName' nicht existiert, wird 'name' als Quelle für die Übersetzung genommen.
                    val nameToTranslate = ingredient.originalName ?: ingredient.name
                    ingredient.copy(
                        name = recipeApiService.translateIngredient(
                            ingredientName = nameToTranslate,
                            sourceLang = "en", // Explizit, da originalName als EN angenommen wird
                            targetLang = "de",
                            useLibreTranslateAsFallback = true
                        )
                        // Ggf. auch die Mengenangaben übersetzen, falls 'ingredient.measure' Text enthält, der übersetzt werden soll.
                        // measure = recipeApiService.translateText(ingredient.measure, sourceLang = "en", targetLang = "de")
                    )
                }

                recipeDetail.copy(
                    title = translatedTitle,
                    instructions = translatedInstructions,
                    category = translatedCategory,
                    area = translatedArea,
                    extendedIngredients = translatedIngredients,
                    // originalTitle wird gesetzt, wenn eine Übersetzung stattgefunden hat
                    originalTitle = if (recipeDetail.title != translatedTitle) recipeDetail.title else recipeDetail.originalTitle
                )
            } catch (e: Exception) {
                println("DEBUG_VM_ERROR: Failed to translate recipe detail contents for '${recipeDetail.title}': ${e.message}")
                recipeDetail // Im Fehlerfall das Original zurückgeben
            }
        }
    }

    fun loadSuggestedRecipes(
        pantryItems: List<FoodItem>,
        maxInitialRecipesToConsider: Int = 20,
        maxDetailCalls: Int = 10,
        numberOfSuggestionsToReturn: Int = 5
    ) {
        println("DEBUG_VM: loadSuggestedRecipes. Pantry: ${pantryItems.size}, MaxSummaries: $maxInitialRecipesToConsider, MaxDetailCalls: $maxDetailCalls, SuggestionsToReturn: $numberOfSuggestionsToReturn")
        if (pantryItems.isEmpty()) {
            suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
            return
        }

        viewModelScope.launch {
            suggestedRecipesUiState = RecipeListUiState.Loading

            val germanPantryIngredientNames = pantryItems.mapNotNull {
                it.name?.lowercase()?.trim()?.takeIf(String::isNotBlank)
            }.distinct()
            println("DEBUG_VM: GERMAN PANTRY NAMES: $germanPantryIngredientNames")
            if (germanPantryIngredientNames.isEmpty()) {
                suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                return@launch
            }

            val pantryIngredientsENDeferred = germanPantryIngredientNames.map { germanName ->
                async(Dispatchers.IO) {
                    recipeApiService.translateIngredient(
                        ingredientName = germanName,
                        sourceLang = "de", targetLang = "en",
                        useLibreTranslateAsFallback = true
                    )
                }
            }
            val pantryIngredientsEN = pantryIngredientsENDeferred.awaitAll()
                .filter(String::isNotBlank).distinct()
            println("DEBUG_VM: TRANSLATED PANTRY NAMES FOR API (EN): $pantryIngredientsEN")
            if (pantryIngredientsEN.isEmpty()) {
                suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                return@launch
            }

            val initialRecipeSummariesResult = recipeApiService.findRecipesContainingAnyOfIngredients(
                ingredientNames = pantryIngredientsEN,
                maxIngredientsToQuery = 3.coerceAtMost(pantryIngredientsEN.size),
                maxResultsPerIngredient = (maxInitialRecipesToConsider / pantryIngredientsEN.size.coerceAtLeast(1) + 1).coerceAtLeast(5)
            )

            initialRecipeSummariesResult.fold(
                onSuccess = { recipeSummaries ->
                    println("DEBUG_VM: INITIAL SUMMARIES from TheMealDB (count: ${recipeSummaries.size}): ${recipeSummaries.map { it.title }}")
                    if (recipeSummaries.isEmpty()) {
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                        return@fold
                    }

                    val recipesToGetDetailsFor = recipeSummaries.shuffled().take(maxInitialRecipesToConsider.coerceAtMost(maxDetailCalls))
                    if (recipesToGetDetailsFor.isEmpty()) {
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                        return@fold
                    }

                    val recipeDetailsDeferred = recipesToGetDetailsFor.map { summary ->
                        async(Dispatchers.IO) {
                            recipeApiService.getRecipeDetails(summary.id).fold(
                                onSuccess = { detail -> Pair(summary, detail) },
                                onFailure = {
                                    println("DEBUG_VM_ERROR: Failed to get details for ${summary.title}: ${it.message}")
                                    null
                                }
                            )
                        }
                    }
                    val fetchedRecipePairs = recipeDetailsDeferred.awaitAll().filterNotNull()
                    if (fetchedRecipePairs.isEmpty()) {
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList()) // Oder Fallback auf Summaries
                        return@fold
                    }

                    val scoredRecipes = fetchedRecipePairs.mapNotNull { (summary, detail) ->
                        // Für das Scoring: Vergleiche pantryIngredientsEN mit den ENGLISCHEN Namen der Rezeptzutaten.
                        // Annahme: `ingredient.originalName` ist der englische Name, oder `ingredient.name` falls `originalName` null ist
                        // und `ingredient.name` noch nicht übersetzt wurde (was unwahrscheinlich ist, wenn `toRecipeDetail` übersetzt).
                        // Es ist entscheidend, dass die Namen hier auf derselben Sprache (Englisch) für den Vergleich sind.
                        val recipeIngredientsEN = detail.extendedIngredients.mapNotNull { ingredient ->
                            (ingredient.originalName ?: ingredient.name) // Bevorzuge originalName für EN
                                .lowercase().trim().takeIf(String::isNotBlank)
                        }.distinct()

                        if (recipeIngredientsEN.isEmpty()) {
                            println("DEBUG_VM_SCORING: Recipe '${detail.title}' has no processable EN ingredients for scoring (originalName or name).")
                            return@mapNotNull null
                        }

                        var matchCount = 0
                        val matchedPantryIngs = mutableListOf<String>()
                        pantryIngredientsEN.forEach { pantryIngEN ->
                            if (recipeIngredientsEN.any { recipeIngEN ->
                                    recipeIngEN == pantryIngEN || recipeIngEN.contains(pantryIngEN) || pantryIngEN.contains(recipeIngEN)
                                }) {
                                matchCount++
                                matchedPantryIngs.add(pantryIngEN)
                            }
                        }

                        val missingRecipeIngs = recipeIngredientsEN.filterNot { recipeIngEN ->
                            pantryIngredientsEN.any { pantryIngEN ->
                                recipeIngEN == pantryIngEN || recipeIngEN.contains(pantryIngEN) || pantryIngEN.contains(recipeIngEN)
                            }
                        }
                        val missingCount = missingRecipeIngs.size

                        if (matchCount > 0) {
                            val score = (matchCount * 10) - (missingCount * 1)
                            RecipeWithScore(summary, detail, score, matchCount, missingCount, matchedPantryIngs.distinct(), missingRecipeIngs)
                        } else {
                            null
                        }
                    }
                        .sortedByDescending { it.score }
                        .take(numberOfSuggestionsToReturn)

                    println("DEBUG_VM: TOP SCORED RECIPES (count: ${scoredRecipes.size}): ${scoredRecipes.map { it.summary.title }}")
                    suggestedRecipesUiState = RecipeListUiState.Success(scoredRecipes.map { it.summary })
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: Failed in initialRecipeSummariesResult (TheMealDB): ${it.message}")
                    suggestedRecipesUiState = RecipeListUiState.Error(it.message ?: "Error loading suggested recipes")
                }
            )
        }
    }
}