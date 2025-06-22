package com.example.myfood.ui.recipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.FoodItem // Stelle sicher, dass dies der korrekte Import für FoodItem ist
import com.example.myfood.data.recipe.RecipeApiService
import com.example.myfood.data.recipe.RecipeDetail
import com.example.myfood.data.recipe.RecipeSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// UI State sealed interfaces (Single Source of Truth)
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

// Datenklasse für die Rezeptbewertung
data class RecipeWithScore(
    val summary: RecipeSummary, // Sollte englische Titel haben, wenn von loadSuggestedRecipes kommend
    val detail: RecipeDetail,   // Wird in Englisch geholt für Scoring (enthält IngredientPresentation)
    val score: Int,
    val matchCount: Int,
    val missingCount: Int,
    val matchedPantryIngredients: List<String>, // Englische Namen der Vorratszutaten
    val missingRecipeIngredients: List<String>  // Englische Namen der fehlenden Rezeptzutaten
)

class RecipeViewModel(
    private val recipeApiService: RecipeApiService = RecipeApiService // Standardinstanz
) : ViewModel() {

    var recipeListUiState: RecipeListUiState by mutableStateOf(RecipeListUiState.Loading)
        private set
    var recipeDetailUiState: RecipeDetailUiState by mutableStateOf(RecipeDetailUiState.Loading)
        private set
    var suggestedRecipesUiState: RecipeListUiState by mutableStateOf(RecipeListUiState.Loading)
        private set

    init {
        // Diese Methode wird beim Erstellen des ViewModels aufgerufen (initiales Laden)
        loadRandomRecipes(translateToGerman = true) // Für die "Entdecken"-Liste
        println("DEBUG_VM: RecipeViewModel initialized.")
    }

    /**
     * Lädt eine Liste von zufälligen Rezepten.
     * Diese Methode wird sowohl initial vom ViewModel aufgerufen als auch
     * durch Benutzerinteraktion (z.B. Klick auf einen "Aktualisieren"-Button)
     * um die "Entdecke neue Rezepte"-Liste neu zu laden.
     */
    fun loadRandomRecipes(translateToGerman: Boolean = true) {
        println("DEBUG_VM: loadRandomRecipes called, translateToGerman: $translateToGerman")
        viewModelScope.launch {
            // Setzt den UI-State auf Loading, was in der UI einen Ladeindikator anzeigt
            recipeListUiState = RecipeListUiState.Loading
            val result = recipeApiService.getRandomRecipes(targetLang = if (translateToGerman) "de" else "en")

            recipeListUiState = result.fold(
                onSuccess = { recipes ->
                    if (translateToGerman && recipes.any {
                            val currentTitle = it.title
                            val currentOriginalTitle = it.originalTitle
                            currentTitle == currentOriginalTitle && currentOriginalTitle != null
                        }) {
                        println("DEBUG_VM: loadRandomRecipes - Some titles might still be EN, attempting explicit translation.")
                        try {
                            val translatedSummaries = withContext(Dispatchers.IO) {
                                recipes.map { summary ->
                                    val currentSummaryTitle = summary.title
                                    val currentOriginalTitle = summary.originalTitle
                                    if (currentSummaryTitle == currentOriginalTitle && currentOriginalTitle != null) {
                                        summary.copy(
                                            title = recipeApiService.translateText(currentOriginalTitle, sourceLang = "en", targetLang = "de"),
                                        )
                                    } else {
                                        summary
                                    }
                                }
                            }
                            println("DEBUG_VM: loadRandomRecipes success (explicitly translated), count: ${translatedSummaries.size}")
                            RecipeListUiState.Success(translatedSummaries)
                        } catch (e: Exception) {
                            println("DEBUG_VM_ERROR: loadRandomRecipes explicit translation failed: ${e.message}. Using recipes as is.")
                            RecipeListUiState.Success(recipes) // Fallback
                        }
                    } else {
                        println("DEBUG_VM: loadRandomRecipes success (as received or no translation needed), count: ${recipes.size}")
                        RecipeListUiState.Success(recipes)
                    }
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: loadRandomRecipes API fetch failed: ${it.message}")
                    RecipeListUiState.Error(it.message ?: "Unknown error loading random recipes")
                }
            )
        }
    }

    fun loadRecipeDetails(recipeId: String, translateToGerman: Boolean = true) {
        println("DEBUG_VM: loadRecipeDetails called for ID: $recipeId, Translate UI to German: $translateToGerman")
        viewModelScope.launch {
            recipeDetailUiState = RecipeDetailUiState.Loading
            val result = recipeApiService.getRecipeDetails(recipeId = recipeId, targetLang = if (translateToGerman) "de" else "en")

            recipeDetailUiState = result.fold(
                onSuccess = { recipeDetail ->
                    val currentTitle = recipeDetail.title
                    val currentOriginalTitle = recipeDetail.originalTitle
                    if (translateToGerman && (currentTitle == currentOriginalTitle && currentOriginalTitle != null)) {
                        println("DEBUG_VM: loadRecipeDetails - Title ('$currentTitle') seems to be original EN, explicitly translating for display.")
                        val displayRecipe = translateRecipeDetailForDisplay(recipeDetail, targetLang = "de")
                        println("DEBUG_VM: loadRecipeDetails success (display translated) for ID: $recipeId, Title: ${displayRecipe.title}")
                        RecipeDetailUiState.Success(displayRecipe)
                    } else {
                        println("DEBUG_VM: loadRecipeDetails success (as received or already target lang) for ID: $recipeId, Title: $currentTitle")
                        RecipeDetailUiState.Success(recipeDetail)
                    }
                },
                onFailure = {
                    println("DEBUG_VM_ERROR: loadRecipeDetails failed for ID $recipeId: ${it.message}")
                    RecipeDetailUiState.Error(it.message ?: "Unknown error loading recipe details")
                }
            )
        }
    }

    private suspend fun translateRecipeDetailForDisplay(recipeDetail: RecipeDetail, targetLang: String = "de"): RecipeDetail {
        val currentDetailTitle = recipeDetail.title
        val currentDetailOriginalTitle = recipeDetail.originalTitle
        val currentDetailInstructions = recipeDetail.instructions
        val currentDetailOriginalInstructions = recipeDetail.originalInstructions
        // ... (andere properties)

        val needsTranslation = targetLang == "de" && (
                (currentDetailTitle == currentDetailOriginalTitle && currentDetailOriginalTitle != null) ||
                        (currentDetailInstructions == currentDetailOriginalInstructions && currentDetailOriginalInstructions != null) ||
                        recipeDetail.ingredients.any { ingPresentation ->
                            val ingOriginalName = ingPresentation.originalName
                            val ingTranslatedName = ingPresentation.translatedName
                            ingTranslatedName == ingOriginalName && ingOriginalName.isNotBlank()
                        })

        if (!needsTranslation) {
            println("DEBUG_VM: translateRecipeDetailForDisplay - No further display translation needed for '$currentDetailTitle' to $targetLang.")
            return recipeDetail
        }

        return withContext(Dispatchers.IO) {
            try {
                println("DEBUG_VM: Translating RecipeDetail for display: '$currentDetailTitle' from potential EN to $targetLang")

                val titleToTranslate = currentDetailOriginalTitle ?: currentDetailTitle
                val translatedTitle = recipeApiService.translateText(titleToTranslate, sourceLang = "en", targetLang = targetLang)

                val instructionsToTranslate = currentDetailOriginalInstructions ?: currentDetailInstructions
                val translatedInstructions = instructionsToTranslate?.let {
                    recipeApiService.translateText(it, sourceLang = "en", targetLang = targetLang)
                }
                // ... (übersetze weitere Felder wie category, area)

                val translatedIngredients = recipeDetail.ingredients.map { ingPresentation ->
                    val nameInSourceLang = ingPresentation.originalName // EN
                    val currentIngTranslatedName = ingPresentation.translatedName
                    if (targetLang == "de" && currentIngTranslatedName == nameInSourceLang && nameInSourceLang.isNotBlank()) {
                        val newTranslatedName = recipeApiService.translateIngredient(
                            ingredientName = nameInSourceLang,
                            sourceLang = "en", targetLang = targetLang,
                            useLibreTranslateAsFallback = true
                        )
                        ingPresentation.copy(translatedName = newTranslatedName)
                    } else {
                        ingPresentation
                    }
                }

                recipeDetail.copy(
                    title = translatedTitle,
                    instructions = translatedInstructions,
                    // category = translatedCategory, // EINFÜGEN
                    // area = translatedArea,       // EINFÜGEN
                    ingredients = translatedIngredients,
                    originalTitle = currentDetailOriginalTitle ?: currentDetailTitle,
                    originalInstructions = currentDetailOriginalInstructions ?: currentDetailInstructions
                    // originalCategory = currentDetailOriginalCategory ?: currentDetailCategory, // EINFÜGEN
                    // originalArea = currentDetailOriginalArea ?: currentDetailArea              // EINFÜGEN
                )
            } catch (e: Exception) {
                println("DEBUG_VM_ERROR: Failed to translate recipe detail contents for display for '$currentDetailTitle': ${e.message}")
                recipeDetail // Return original on error
            }
        }
    }


    fun loadSuggestedRecipes(
        pantryItems: List<FoodItem>,
        maxInitialRecipesToConsider: Int = 30,
        maxDetailCallsInParallel: Int = 5,
        numberOfSuggestionsToReturn: Int = 5
    ) {
        println("DEBUG_VM: loadSuggestedRecipes. Pantry: ${pantryItems.size}, MaxSummaries: $maxInitialRecipesToConsider, MaxDetailsParallel: $maxDetailCallsInParallel, SuggestionsToReturn: $numberOfSuggestionsToReturn")
        if (pantryItems.isEmpty()) {
            suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
            println("DEBUG_VM: Pantry is empty, no suggestions.")
            return
        }

        viewModelScope.launch {
            suggestedRecipesUiState = RecipeListUiState.Loading

            val germanPantryIngredientNames = pantryItems.mapNotNull {
                it.name?.lowercase()?.trim()?.takeIf(String::isNotBlank)
            }.distinct()

            if (germanPantryIngredientNames.isEmpty()) {
                suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                println("DEBUG_VM: No valid German pantry ingredient names.")
                return@launch
            }
            println("DEBUG_VM: Valid GERMAN PANTRY NAMES for lookup: $germanPantryIngredientNames")

            val pantryIngredientsENforScoring = try {
                withContext(Dispatchers.IO) {
                    germanPantryIngredientNames.map { germanName ->
                        async {
                            recipeApiService.translateIngredient(germanName, "de", "en", true)
                        }
                    }.awaitAll().filter(String::isNotBlank).distinct()
                }
            } catch (e: Exception) {
                println("DEBUG_VM_ERROR: Failed to translate pantry ingredients to EN (for scoring): ${e.message}")
                suggestedRecipesUiState = RecipeListUiState.Error("Could not prepare pantry for scoring.")
                return@launch
            }

            if (pantryIngredientsENforScoring.isEmpty()) {
                suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                println("DEBUG_VM: No EN pantry names after translation (for scoring).")
                return@launch
            }
            println("DEBUG_VM: TRANSLATED PANTRY NAMES FOR SCORING (EN): $pantryIngredientsENforScoring")

            val initialRecipeSummariesResult = recipeApiService.findRecipesContainingAnyOfIngredients(
                ingredientNamesDE = germanPantryIngredientNames,
                maxIngredientsToQuery = 3.coerceAtMost(germanPantryIngredientNames.size),
                maxResultsPerIngredient = (maxInitialRecipesToConsider / germanPantryIngredientNames.size.coerceAtLeast(1) + 1).coerceAtLeast(10),
                targetLang = "en" // Holen der Summaries auf Englisch für konsistentes Scoring
            )

            initialRecipeSummariesResult.fold(
                onSuccess = { recipeSummariesFromApi ->
                    println("DEBUG_VM: INITIAL SUMMARIES from API (count: ${recipeSummariesFromApi.size}, EN titles): ${recipeSummariesFromApi.take(5).map { it.title }}")
                    if (recipeSummariesFromApi.isEmpty()) {
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                        println("DEBUG_VM: No initial summaries from API.")
                        return@fold
                    }

                    val recipesToGetDetailsFor = recipeSummariesFromApi.shuffled().take(maxInitialRecipesToConsider.coerceAtMost(recipeSummariesFromApi.size))
                    println("DEBUG_VM: Will attempt EN details for up to ${recipesToGetDetailsFor.size} recipes.")

                    if (recipesToGetDetailsFor.isEmpty()){
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                        println("DEBUG_VM: No recipes selected for details.")
                        return@fold
                    }

                    val fetchedRecipeDetails = try {
                        withContext(Dispatchers.IO) {
                            recipesToGetDetailsFor.take(maxDetailCallsInParallel).map { summary ->
                                async {
                                    val summaryTitle = summary.title
                                    val summaryId = summary.id
                                    recipeApiService.getRecipeDetails(summaryId, targetLang = "en").fold(
                                        onSuccess = { detail -> detail },
                                        onFailure = { error ->
                                            println("DEBUG_VM_ERROR: Failed EN details for '$summaryTitle' (ID: $summaryId): ${error.message}")
                                            null
                                        }
                                    )
                                }
                            }.awaitAll().filterNotNull()
                        }
                    } catch (e: Exception) {
                        println("DEBUG_VM_ERROR: Error fetching multiple recipe details: ${e.message}")
                        suggestedRecipesUiState = RecipeListUiState.Error("Error fetching details for suggestions.")
                        return@fold
                    }

                    if (fetchedRecipeDetails.isEmpty()) {
                        suggestedRecipesUiState = RecipeListUiState.Success(emptyList())
                        println("DEBUG_VM: No details fetched successfully in EN.")
                        return@fold
                    }
                    println("DEBUG_VM: Successfully fetched EN details for ${fetchedRecipeDetails.size} recipes.")

                    val scoredRecipes = fetchedRecipeDetails.mapNotNull { detail ->
                        val detailTitle = detail.title
                        val detailId = detail.id

                        val recipeIngredientsENSource = detail.ingredients.mapNotNull { pres ->
                            pres.originalName?.lowercase()?.trim()?.takeIf(String::isNotBlank)
                        }.distinct()

                        if (recipeIngredientsENSource.isEmpty()) {
                            println("DEBUG_VM_SCORING: Recipe '$detailTitle' (EN) has no processable EN ingredients from detail.")
                            return@mapNotNull null
                        }

                        var matchCount = 0
                        val matchedPantryIngs = mutableListOf<String>()
                        pantryIngredientsENforScoring.forEach { pantryIngEN ->
                            if (recipeIngredientsENSource.any { recipeIngEN ->
                                    recipeIngEN == pantryIngEN || recipeIngEN.contains(pantryIngEN) || pantryIngEN.contains(recipeIngEN)
                                }) {
                                matchCount++
                                matchedPantryIngs.add(pantryIngEN)
                            }
                        }

                        val missingRecipeIngs = recipeIngredientsENSource.filterNot { recipeIngEN ->
                            pantryIngredientsENforScoring.any { pantryIngEN ->
                                recipeIngEN == pantryIngEN || recipeIngEN.contains(pantryIngEN) || pantryIngEN.contains(recipeIngEN)
                            }
                        }
                        val missingCount = missingRecipeIngs.size

                        val originalSummaryForScore = recipeSummariesFromApi.find { it.id == detailId }
                        if (originalSummaryForScore == null) {
                            println("DEBUG_VM_SCORING: Could not find original summary for detail '$detailTitle' (ID: $detailId). Skipping.")
                            return@mapNotNull null
                        }

                        if (matchCount > 0) {
                            val score = (matchCount * 10) - (missingCount * 2)
                            println("DEBUG_VM_SCORING: Recipe '$detailTitle' (EN), Matches: $matchCount, Missing: $missingCount, Score: $score")
                            RecipeWithScore(originalSummaryForScore, detail, score, matchCount, missingCount, matchedPantryIngs.distinct(), missingRecipeIngs)
                        } else {
                            println("DEBUG_VM_SCORING: Recipe '$detailTitle' (EN) has 0 matches. Not including.")
                            null
                        }
                    }
                        .sortedByDescending { it.score }
                        .take(numberOfSuggestionsToReturn)

                    println("DEBUG_VM: TOP SCORED RECIPES (count: ${scoredRecipes.size}, EN titles): ${scoredRecipes.joinToString { (it.summary.title ?: "N/A") + " (Score:" + it.score + ")" }}")

                    val finalSummariesToDisplay = try {
                        withContext(Dispatchers.IO) {
                            scoredRecipes.map { scoredRecipe ->
                                val summaryToTranslate = scoredRecipe.summary
                                val titleForTranslation = summaryToTranslate.title // This should be EN
                                if (titleForTranslation != null && titleForTranslation.isNotBlank()) {
                                    val translatedDisplayTitle = recipeApiService.translateText(titleForTranslation, sourceLang = "en", targetLang = "de")
                                    summaryToTranslate.copy(
                                        title = translatedDisplayTitle,
                                        originalTitle = titleForTranslation // Keep the EN title as original
                                    )
                                } else {
                                    summaryToTranslate
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("DEBUG_VM_ERROR: Failed to translate titles of scored recipes for display: ${e.message}. Using untranslated (EN) titles.")
                        scoredRecipes.map { it.summary } // Fallback to EN titles if translation fails
                    }

                    suggestedRecipesUiState = RecipeListUiState.Success(finalSummariesToDisplay)
                    println("DEBUG_VM: Successfully set suggestedRecipesUiState with ${finalSummariesToDisplay.size} recipes for display.")

                },
                onFailure = {
                    println("DEBUG_VM_ERROR: Failed in initialRecipeSummariesResult (API Call to findRecipes...): ${it.message}")
                    suggestedRecipesUiState = RecipeListUiState.Error(it.message ?: "Error loading suggested recipes")
                }
            )
        }
    }
}