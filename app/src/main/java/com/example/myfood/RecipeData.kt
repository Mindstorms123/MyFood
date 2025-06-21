// In RecipeData.kt
package com.example.myfood.data.recipe

import kotlinx.serialization.Serializable

// Wrapper
@Serializable
data class MealDBResponse(
    val meals: List<MealDBRecipe>?
)

// API-spezifische Datenklasse
@Serializable
data class MealDBRecipe(
    val idMeal: String,
    val strMeal: String,
    val strCategory: String? = null,
    val strArea: String? = null,
    val strInstructions: String? = null,
    val strMealThumb: String? = null,
    val strTags: String? = null,
    val strYoutube: String? = null,
    val strIngredient1: String? = null,
    val strIngredient2: String? = null,
    // ... bis strIngredient20
    val strIngredient20: String? = null,
    val strMeasure1: String? = null,
    val strMeasure2: String? = null,
    // ... bis strMeasure20
    val strMeasure20: String? = null,
    val strSource: String? = null,
    val dateModified: String? = null
) {
    fun getIngredients(): List<IngredientPresentation> {
        val ingredients = mutableListOf<IngredientPresentation>()
        for (i in 1..20) {
            val ingredient = getPropertyValue("strIngredient$i")?.toString()
            val measure = getPropertyValue("strMeasure$i")?.toString()
            if (!ingredient.isNullOrBlank()) {
                ingredients.add(IngredientPresentation(ingredient, measure ?: ""))
            } else {
                break
            }
        }
        return ingredients
    }

    private fun getPropertyValue(propertyName: String): Any? {
        return this::class.members.find { it.name == propertyName }?.call(this)
    }
}

// Hilfsklasse für geparste Zutaten von der API
data class IngredientPresentation(
    val name: String, // Der reine Name der Zutat von der API
    val measure: String
)

// --- Unsere generischen UI-Datenklassen ---

@Serializable
data class RecipeSummary(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val sourceName: String? = null
    // optional: val originalTitle: String? = null // Falls du auch hier das Original speichern willst
)

@Serializable
data class RecipeDetail(
    val id: String,
    val title: String, // Wird übersetzt
    val imageUrl: String?,
    val sourceName: String?, // Wird potenziell übersetzt
    val instructions: String, // Wird übersetzt
    val extendedIngredients: List<Ingredient>, // Enthält übersetzte und originale Namen
    val summary: String?, // Wird potenziell übersetzt (oder aus category/area generiert)
    val category: String?, // Hinzugefügt, wird potenziell übersetzt
    val area: String?,     // Hinzugefügt, wird potenziell übersetzt
    val originalTitle: String? = null, // Hinzugefügt, speichert den nicht-übersetzten Titel
    val originalSourceName: String? = null, // Optional: um das Original zu speichern
    val originalCategory: String? = null, // Optional: um das Original zu speichern
    val originalArea: String? = null,     // Optional: um das Original zu speichern
    val originalSummary: String? = null   // Optional: um das Original zu speichern

    // val readyInMinutes: Int? = null, // TheMealDB liefert dies nicht direkt
    // val servings: Int? = null      // TheMealDB liefert dies nicht direkt
)

@Serializable
data class Ingredient(
    val id: Int, // Dummy-ID für TheMealDB
    val original: String,      // Die komplette "original" Angabe von der API, z.B. "1 cup flour"
    val name: String,          // Der *angezeigte* Name der Zutat (kann übersetzt sein)
    val originalName: String,  // Der *reine, ursprüngliche* Name der Zutat von der API (z.B. "flour")
    val amount: Double,
    val unit: String
)

// --- Mapping Funktionen ---

fun MealDBRecipe.toRecipeSummary(): RecipeSummary {
    return RecipeSummary(
        id = this.idMeal,
        title = this.strMeal, // Wird im ViewModel übersetzt, falls nötig
        imageUrl = this.strMealThumb,
        sourceName = this.strArea // oder strCategory, wird ggf. im VM übersetzt
    )
}

fun MealDBRecipe.toRecipeDetail(): RecipeDetail {
    val ingredientsList = this.getIngredients().map { presentation ->
        Ingredient(
            id = 0, // TheMealDB gibt keine separaten Zutat-IDs
            original = "${presentation.measure} ${presentation.name}".trim(), // z.B. "1 cup Flour"
            name = presentation.name, // Initial der API-Name, z.B. "Flour". Wird im ViewModel übersetzt.
            originalName = presentation.name, // Der reine API-Name, z.B. "Flour"
            amount = parseAmount(presentation.measure),
            unit = parseUnit(presentation.measure)
        )
    }

    return RecipeDetail(
        id = this.idMeal,
        title = this.strMeal, // Wird im ViewModel übersetzt
        imageUrl = this.strMealThumb,
        sourceName = this.strSource ?: this.strArea, // Wird ggf. im VM übersetzt
        instructions = this.strInstructions ?: "Keine Anleitung verfügbar.", // Wird im VM übersetzt
        extendedIngredients = ingredientsList,
        summary = "Kategorie: ${this.strCategory ?: "N/A"}, Region: ${this.strArea ?: "N/A"}", // Wird ggf. im VM übersetzt
        category = this.strCategory, // Hinzugefügt, wird ggf. im VM übersetzt
        area = this.strArea,         // Hinzugefügt, wird ggf. im VM übersetzt
        originalTitle = this.strMeal // Speichere hier schon mal das Original
        // originalSourceName, originalCategory etc. könnten hier auch direkt befüllt werden
    )
}

// Hilfsfunktionen zum Parsen
private fun parseAmount(measure: String): Double {
    return measure.split(" ").firstOrNull()?.toDoubleOrNull() ?: 1.0
}

private fun parseUnit(measure: String): String {
    return measure.split(" ").drop(1).joinToString(" ")
}