// In RecipeData.kt (oder wie auch immer deine Datei heißt)
package com.example.myfood.data.recipe // Stelle sicher, dass das Package stimmt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wrapper für die API-Antwort, da TheMealDB Rezepte in einem "meals" Array liefert
@Serializable
data class MealDBResponse(
    val meals: List<MealDBRecipe>? // Kann null sein, wenn nichts gefunden wird
)

@Serializable
data class MealDBRecipe(
    val idMeal: String, // Eindeutige ID
    val strMeal: String, // Rezeptname
    val strCategory: String? = null,
    val strArea: String? = null, // Region
    val strInstructions: String? = null,
    val strMealThumb: String? = null, // URL zum Bild
    val strTags: String? = null,
    val strYoutube: String? = null, // YouTube Link

    // Zutaten und Mengenangaben (TheMealDB hat bis zu 20 davon)
    // Wir fassen sie später in eine Liste von Ingredients zusammen
    val strIngredient1: String? = null,
    val strIngredient2: String? = null,
    // ... bis strIngredient20
    val strIngredient20: String? = null,

    val strMeasure1: String? = null,
    val strMeasure2: String? = null,
    // ... bis strMeasure20
    val strMeasure20: String? = null,

    val strSource: String? = null, // URL zur Originalquelle
    val dateModified: String? = null
) {
    // Hilfsfunktion, um Zutaten und Maße in eine handlichere Liste umzuwandeln
    fun getIngredients(): List<IngredientPresentation> {
        val ingredients = mutableListOf<IngredientPresentation>()
        for (i in 1..20) {
            val ingredient = getPropertyValue("strIngredient$i")?.toString()
            val measure = getPropertyValue("strMeasure$i")?.toString()

            if (!ingredient.isNullOrBlank()) {
                ingredients.add(IngredientPresentation(ingredient, measure ?: ""))
            } else {
                // Sobald eine Zutat leer ist, hören wir auf, da sie sequenziell sind
                break
            }
        }
        return ingredients
    }

    // Kleine Hilfsfunktion, um dynamisch auf Properties zuzugreifen (vereinfacht)
    private fun getPropertyValue(propertyName: String): Any? {
        return this::class.members.find { it.name == propertyName }?.call(this)
    }
}

// Eine einfachere Klasse für die Darstellung von Zutaten in der UI
data class IngredientPresentation(
    val name: String,
    val measure: String
)

// Wir mappen MealDBRecipe auf unsere generischen UI-Datenklassen
// Das macht die UI unabhängiger von der spezifischen API-Struktur

fun MealDBRecipe.toRecipeSummary(): RecipeSummary {
    return RecipeSummary(
        id = this.idMeal,
        title = this.strMeal,
        imageUrl = this.strMealThumb,
        sourceName = this.strArea // oder strCategory, je nachdem was du anzeigen willst
    )
}

fun MealDBRecipe.toRecipeDetail(): RecipeDetail {
    return RecipeDetail(
        id = this.idMeal,
        title = this.strMeal,
        imageUrl = this.strMealThumb,
        sourceName = this.strSource ?: this.strArea,
        instructions = this.strInstructions ?: "Keine Anleitung verfügbar.",
        extendedIngredients = this.getIngredients().map {
            // Konvertiere IngredientPresentation zu unserer generischen Ingredient-Klasse,
            // falls du die Unterscheidung von Name, Menge, Einheit beibehalten willst.
            // Für TheMealDB ist IngredientPresentation oft ausreichend für die Anzeige.
            // Hier ein vereinfachtes Mapping zu unserer bestehenden `Ingredient`-Klasse:
            Ingredient(
                id = 0, // TheMealDB gibt keine separaten Zutat-IDs
                original = "${it.measure} ${it.name}".trim(),
                name = it.name,
                amount = parseAmount(it.measure), // Einfache Parsing-Logik (kann verbessert werden)
                unit = parseUnit(it.measure) // Einfache Parsing-Logik
            )
        },
        summary = "Kategorie: ${this.strCategory ?: "N/A"}, Region: ${this.strArea ?: "N/A"}" // Beispiel
    )
}


// Hilfsfunktionen zum Parsen von Menge und Einheit (sehr rudimentär, kann verbessert werden)
private fun parseAmount(measure: String): Double {
    return measure.split(" ").firstOrNull()?.toDoubleOrNull() ?: 1.0 // Default 1.0 wenn nicht parsbar
}

private fun parseUnit(measure: String): String {
    return measure.split(" ").drop(1).joinToString(" ")
}


// Unsere generischen Datenklassen für die UI (aus dem vorherigen Schritt, leicht angepasst)
// Diese bleiben größtenteils gleich, damit die UI nicht bei jedem API-Wechsel umgebaut werden muss.
@Serializable
data class RecipeSummary( // Für die Listenansicht
    val id: String,
    val title: String,
    val imageUrl: String?,
    val sourceName: String? = null,
    // TheMealDB liefert diese nicht direkt für die Übersicht, also optional machen oder entfernen
    // val readyInMinutes: Int? = null,
    // val servings: Int? = null
)

@Serializable
data class RecipeDetail( // Für die Detailansicht
    val id: String,
    val title: String,
    val imageUrl: String?,
    val sourceName: String?,
    val instructions: String,
    val extendedIngredients: List<Ingredient>, // Unsere generische Zutat
    // TheMealDB liefert diese nicht direkt, also optional
    // val readyInMinutes: Int? = null,
    // val servings: Int? = null,
    val summary: String? = null
)

@Serializable
data class Ingredient( // Unsere generische Zutat für die UI
    val id: Int,
    val original: String,
    val name: String,
    val amount: Double,
    val unit: String
)