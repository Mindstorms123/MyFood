package com.example.myfood.data.recipe

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient // Wichtig für Felder, die nicht von der API kommen

// --- Datenmodelle für die App-interne Verwendung ---

@Serializable
data class Ingredient(
    val name: String,
    val measure: String? = null // Menge/Einheit
)

@Serializable
data class IngredientPresentation(
    val originalName: String, // Der Name, wie er von der API kam (wahrscheinlich Englisch)
    val originalMeasure: String?,
    var translatedName: String, // Der übersetzte Name für die Anzeige
    var translatedMeasure: String?, // Die übersetzte Menge für die Anzeige
    @Transient var isUserAdded: Boolean = false, // Wenn der Benutzer diese Zutat manuell hinzugefügt hat
    @Transient var isAISuggestion: Boolean = false // Wenn die KI diese Zutat vorgeschlagen hat
) {
    // Hilfsfunktion, um den besten Namen für die Anzeige zu bekommen
    fun getDisplayName(): String = translatedName.ifBlank { originalName }
    fun getDisplayMeasure(): String? = translatedMeasure?.ifBlank { originalMeasure }
}


@Serializable
data class RecipeSummary(
    val id: String,
    var title: String, // Kann nach Übersetzung aktualisiert werden
    val thumbnailUrl: String?,
    @Transient var originalTitle: String? = null // Titel von der API, bevor er übersetzt wird
)

@Serializable
data class RecipeDetail(
    val id: String,
    var title: String, // Kann nach Übersetzung aktualisiert werden
    val thumbnailUrl: String?,
    var instructions: String?, // Kann nach Übersetzung aktualisiert werden
    var category: String?, // Kann nach Übersetzung aktualisiert werden
    var area: String?, // Kann nach Übersetzung aktualisiert werden
    val ingredients: List<IngredientPresentation>, // Wird direkt mit IngredientPresentation erstellt
    val sourceUrl: String?,
    val tags: List<String>? = null,
    val youtubeUrl: String?,
    @Transient var originalTitle: String? = null,
    @Transient var originalInstructions: String? = null,
    @Transient var originalCategory: String? = null,
    @Transient var originalArea: String? = null
)

// --- Datenmodelle für die TheMealDB API-Antwort ---
// Diese bleiben so, wie sie von der API kommen.

@Serializable
data class MealDBRecipe(
    val idMeal: String,
    val strMeal: String?,
    val strDrinkAlternate: String? = null,
    val strCategory: String?,
    val strArea: String?,
    val strInstructions: String?,
    val strMealThumb: String?,
    val strTags: String? = null, // Oft kommasepariert oder null
    val strYoutube: String? = null,
    val strSource: String? = null,
    // Zutaten und Mengen - TheMealDB hat bis zu 20 davon
    val strIngredient1: String? = null,
    val strIngredient2: String? = null,
    val strIngredient3: String? = null,
    val strIngredient4: String? = null,
    val strIngredient5: String? = null,
    val strIngredient6: String? = null,
    val strIngredient7: String? = null,
    val strIngredient8: String? = null,
    val strIngredient9: String? = null,
    val strIngredient10: String? = null,
    val strIngredient11: String? = null,
    val strIngredient12: String? = null,
    val strIngredient13: String? = null,
    val strIngredient14: String? = null,
    val strIngredient15: String? = null,
    val strIngredient16: String? = null,
    val strIngredient17: String? = null,
    val strIngredient18: String? = null,
    val strIngredient19: String? = null,
    val strIngredient20: String? = null,
    val strMeasure1: String? = null,
    val strMeasure2: String? = null,
    val strMeasure3: String? = null,
    val strMeasure4: String? = null,
    val strMeasure5: String? = null,
    val strMeasure6: String? = null,
    val strMeasure7: String? = null,
    val strMeasure8: String? = null,
    val strMeasure9: String? = null,
    val strMeasure10: String? = null,
    val strMeasure11: String? = null,
    val strMeasure12: String? = null,
    val strMeasure13: String? = null,
    val strMeasure14: String? = null,
    val strMeasure15: String? = null,
    val strMeasure16: String? = null,
    val strMeasure17: String? = null,
    val strMeasure18: String? = null,
    val strMeasure19: String? = null,
    val strMeasure20: String? = null
) {
    // Hilfsfunktion, um Zutaten und Mengen als Liste von Paaren zu extrahieren
    fun getIngredientsWithMeasures(): List<Pair<String, String?>> {
        val ingredients = mutableListOf<Pair<String, String?>>()
        val allStrIngredients = listOfNotNull(
            strIngredient1, strIngredient2, strIngredient3, strIngredient4, strIngredient5,
            strIngredient6, strIngredient7, strIngredient8, strIngredient9, strIngredient10,
            strIngredient11, strIngredient12, strIngredient13, strIngredient14, strIngredient15,
            strIngredient16, strIngredient17, strIngredient18, strIngredient19, strIngredient20
        )
        val allStrMeasures = listOf( // listOf, nicht listOfNotNull, da die Indizes übereinstimmen müssen
            strMeasure1, strMeasure2, strMeasure3, strMeasure4, strMeasure5,
            strMeasure6, strMeasure7, strMeasure8, strMeasure9, strMeasure10,
            strMeasure11, strMeasure12, strMeasure13, strMeasure14, strMeasure15,
            strMeasure16, strMeasure17, strMeasure18, strMeasure19, strMeasure20
        )

        for (i in allStrIngredients.indices) {
            val ingredient = allStrIngredients[i]?.trim()
            val measure = allStrMeasures.getOrNull(i)?.trim()
            if (!ingredient.isNullOrBlank()) {
                ingredients.add(ingredient to (if (measure.isNullOrBlank()) null else measure))
            }
        }
        return ingredients
    }
}

@Serializable
data class MealDBResponse(
    val meals: List<MealDBRecipe>? // Kann null sein, wenn nichts gefunden wird
)


// --- Erweiterungsfunktionen für das Mapping und die Übersetzung ---

/**
 * Konvertiert ein MealDBRecipe in ein RecipeSummary und übersetzt den Titel.
 * Benötigt den RecipeApiService für die Übersetzung.
 */
suspend fun MealDBRecipe.toRecipeSummary(
    apiService: RecipeApiService,
    targetLang: String = "de", // Zielsprache für die Übersetzung
    sourceLang: String = "en"  // Annahme, dass API-Daten primär Englisch sind
): RecipeSummary {
    val originalTitle = this.strMeal ?: "Unbekanntes Rezept"
    var translatedTitle = originalTitle

    if (targetLang.isNotBlank() && targetLang != sourceLang && originalTitle.isNotBlank()) {
        translatedTitle = apiService.translateText(
            textToTranslate = originalTitle,
            sourceLang = sourceLang,
            targetLang = targetLang
        )
        println("DEBUG_MAP_SUMMARY: Original Title: '$originalTitle', Translated ($sourceLang->$targetLang): '$translatedTitle'")
    }

    return RecipeSummary(
        id = this.idMeal,
        title = translatedTitle,
        thumbnailUrl = this.strMealThumb,
        originalTitle = if (translatedTitle != originalTitle) originalTitle else null // Speichere Original nur bei Änderung
    )
}

/**
 * Konvertiert ein MealDBRecipe in ein RecipeDetail und übersetzt relevante Felder.
 * Benötigt den RecipeApiService für die Übersetzung.
 */
suspend fun MealDBRecipe.toRecipeDetail(
    apiService: RecipeApiService,
    targetLang: String = "de", // Zielsprache für die Übersetzung
    sourceLang: String = "en"  // Annahme, dass API-Daten primär Englisch sind
): RecipeDetail {
    val originalTitle = this.strMeal ?: "Unbekanntes Rezept"
    val originalInstructions = this.strInstructions
    val originalCategory = this.strCategory
    val originalArea = this.strArea

    var translatedTitle = originalTitle
    var translatedInstructions = originalInstructions
    var translatedCategory = originalCategory
    var translatedArea = originalArea

    // Übersetze Textfelder, wenn eine Zielsprache angegeben ist und sie sich von der Quellsprache unterscheidet
    if (targetLang.isNotBlank() && targetLang != sourceLang) {
        if (originalTitle.isNotBlank()) {
            translatedTitle = apiService.translateText(originalTitle, sourceLang, targetLang)
            println("DEBUG_MAP_DETAIL: Title: '$originalTitle' -> '$translatedTitle'")
        }
        if (!originalInstructions.isNullOrBlank()) {
            translatedInstructions = apiService.translateText(originalInstructions, sourceLang, targetLang)
            println("DEBUG_MAP_DETAIL: Instructions ('${originalInstructions.take(30)}...') -> '${translatedInstructions?.take(30)}...'")
        }
        if (!originalCategory.isNullOrBlank()) {
            translatedCategory = apiService.translateText(originalCategory, sourceLang, targetLang)
            println("DEBUG_MAP_DETAIL: Category: '$originalCategory' -> '$translatedCategory'")
        }
        if (!originalArea.isNullOrBlank()) {
            translatedArea = apiService.translateText(originalArea, sourceLang, targetLang)
            println("DEBUG_MAP_DETAIL: Area: '$originalArea' -> '$translatedArea'")
        }
    }

    // Übersetze Zutaten
    val ingredientsPresentation = this.getIngredientsWithMeasures().mapNotNull { (name, measure) ->
        if (name.isBlank()) return@mapNotNull null

        var translatedIngredientName = name
        var translatedMeasure = measure

        if (targetLang.isNotBlank() && targetLang != sourceLang) {
            // Zutaten werden typischerweise als 'Ingredient' übersetzt, nicht als allgemeiner Text
            translatedIngredientName = apiService.translateIngredient(
                ingredientName = name,
                sourceLang = sourceLang, // API-Zutaten sind meist Englisch
                targetLang = targetLang, // z.B. "de"
                useLibreTranslateAsFallback = true // Für Zutaten ist LibreTranslate oft besser
            )
            // Mengen/Maßeinheiten könnten komplexer sein und erfordern evtl. eine eigene Logik oder werden vorerst nicht übersetzt
            // Fürs Erste behalten wir das Originalmaß, da Maße oft sprachunabhängig sind oder schwer zu übersetzen.
            // translatedMeasure = measure // Belasse Measure vorerst unübersetzt oder implementiere spezifische Logik
            println("DEBUG_MAP_DETAIL: Ingredient: '$name' -> '$translatedIngredientName', Measure: '$measure' -> '$translatedMeasure'")
        }

        IngredientPresentation(
            originalName = name,
            originalMeasure = measure,
            translatedName = translatedIngredientName,
            translatedMeasure = translatedMeasure // Hier ggf. später eine Logik für Maßeinheiten-Übersetzung
        )
    }

    val tagsList = this.strTags?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }

    return RecipeDetail(
        id = this.idMeal,
        title = translatedTitle,
        thumbnailUrl = this.strMealThumb,
        instructions = translatedInstructions,
        category = translatedCategory,
        area = translatedArea,
        ingredients = ingredientsPresentation,
        sourceUrl = this.strSource,
        tags = tagsList,
        youtubeUrl = this.strYoutube,
        originalTitle = if (translatedTitle != originalTitle) originalTitle else null,
        originalInstructions = if (translatedInstructions != originalInstructions) originalInstructions else null,
        originalCategory = if (translatedCategory != originalCategory) originalCategory else null,
        originalArea = if (translatedArea != originalArea) originalArea else null
    )
}