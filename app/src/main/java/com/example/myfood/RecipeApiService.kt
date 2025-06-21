package com.example.myfood.data.recipe

// Importiere ALLES Notwendige aus RecipeData.kt
import com.example.myfood.data.recipe.MealDBRecipe // WICHTIG
import com.example.myfood.data.recipe.MealDBResponse // WICHTIG (die Version aus RecipeData.kt)
import com.example.myfood.data.recipe.RecipeDetail // WICHTIG
import com.example.myfood.data.recipe.RecipeSummary // WICHTIG
// Ingredient und IngredientPresentation werden indirekt über die oberen Klassen verwendet

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable // Bleibt für lokale Datenklassen wie LibreTranslateResponse
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

// Basis-URL für TheMealDB API
private const val BASE_URL_THEMEALDB = "https://www.themealdb.com/api/json/v1/1/"

// --- Statische lokale Übersetzungsmap ---
val GERMAN_TO_ENGLISH_INGREDIENT_MAP: Map<String, String> = mapOf(
    "mehl" to "flour",
    "zucker" to "sugar",
    "eier" to "eggs",
    "ei" to "egg",
    "milch" to "milk",
    "butter" to "butter",
    "paniermehl" to "breadcrumbs",
    "semmelbrösel" to "breadcrumbs",
    "kartoffel" to "potato",
    "kartoffeln" to "potatoes",
    "zwiebel" to "onion",
    "zwiebeln" to "onions",
    "knoblauch" to "garlic",
    "hähnchenbrust" to "chicken breast",
    "hähnchen" to "chicken",
    "schweinefleisch" to "pork",
    "schnitzel" to "schnitzel",
    "rindfleisch" to "beef",
    "salz" to "salt",
    "pfeffer" to "pepper",
    "öl" to "oil",
    "olivenöl" to "olive oil",
    "sonnenblumenöl" to "sunflower oil",
    "rotkohl" to "red cabbage",
    "apfel" to "apple",
    "äpfel" to "apples",
    "tomate" to "tomato",
    "tomaten" to "tomatoes",
    "gurke" to "cucumber",
    "paprika" to "bell pepper",
    "käse" to "cheese",
    "reis" to "rice",
    "nudeln" to "pasta",
    "wasser" to "water"
    // ... Erweitere diese Liste nach Bedarf
)

// --- Datenklassen für Übersetzungs-APIs (bleiben lokal in dieser Datei) ---
@Serializable
data class LibreTranslateResponse(
    val translatedText: String? = null,
    val error: String? = null
)

@Serializable
data class MyMemoryResponseData(
    val translatedText: String,
    val match: Float
)

@Serializable
data class MyMemoryResponse(
    val responseData: MyMemoryResponseData?,
    val responseStatus: Int,
    val responseDetails: String? = null
)

// ----- GELÖSCHT: Die folgenden Datenklassen sind jetzt in RecipeData.kt definiert und werden von dort importiert -----
// @Serializable
// data class Meal(...) { ... } // Alte Meal-Klasse komplett entfernt
//
// @Serializable
// data class MealDBResponse(val meals: List<Meal>?) // Alte MealDBResponse-Klasse entfernt
//
// data class RecipeSummary(...) // Alte RecipeSummary-Klasse entfernt
//
// data class RecipeDetail(...) { // Alte RecipeDetail-Klasse (und ihre innere Ingredient-Klasse) entfernt
//    data class Ingredient(...)
// }
// -------------------------------------------------------------------------------------------------------------


object RecipeApiService {

    private val client: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 10000
            }
        }
    }

    private val translationCache = ConcurrentHashMap<String, String>()
    private const val MYMEMORY_API_URL = "https://api.mymemory.translated.net/get"

    suspend fun translateIngredient(
        ingredientName: String,
        sourceLang: String = "de",
        targetLang: String = "en",
        useLibreTranslateAsFallback: Boolean = false,
        myMemoryEmail: String? = null
    ): String {
        val normalizedOriginal = ingredientName.lowercase().trim()
        if (normalizedOriginal.isEmpty()) return ""

        translationCache[normalizedOriginal]?.let {
            println("DEBUG_API_TRANSLATE_MAIN: Cache HIT for '$normalizedOriginal' -> '$it'")
            return it
        }
        println("DEBUG_API_TRANSLATE_MAIN: Cache MISS for '$normalizedOriginal'.")

        GERMAN_TO_ENGLISH_INGREDIENT_MAP[normalizedOriginal]?.let { localTranslation ->
            if (localTranslation != normalizedOriginal) {
                println("DEBUG_API_TRANSLATE_MAIN: Local Map HIT for '$normalizedOriginal' -> '$localTranslation'")
                translationCache[normalizedOriginal] = localTranslation
                return localTranslation
            }
        }
        println("DEBUG_API_TRANSLATE_MAIN: Local Map MISS for '$normalizedOriginal'.")

        var translatedName = translateIngredientViaMyMemoryInternal(normalizedOriginal, sourceLang, targetLang, myMemoryEmail)

        if (translatedName == normalizedOriginal && useLibreTranslateAsFallback) {
            println("DEBUG_API_TRANSLATE_MAIN: MyMemory returned original. Attempting LibreTranslate...")
            translatedName = translateIngredientViaLibreTranslateInternal(normalizedOriginal, sourceLang, targetLang)
        }

        if (translatedName != normalizedOriginal && translatedName.isNotBlank()) {
            println("DEBUG_API_TRANSLATE_MAIN: Final translation for '$normalizedOriginal' -> '$translatedName'. Caching.")
            translationCache[normalizedOriginal] = translatedName
        } else if (translatedName == normalizedOriginal) {
            println("DEBUG_API_TRANSLATE_MAIN: All translation attempts failed or returned original for '$normalizedOriginal'. Using original.")
        }
        return translatedName
    }

    private suspend fun translateIngredientViaMyMemoryInternal(
        normalizedOriginal: String, sourceLang: String, targetLang: String, email: String?
    ): String {
        println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL: Calling MyMemory for '$normalizedOriginal'...")
        return try {
            val response: MyMemoryResponse = client.get(MYMEMORY_API_URL) {
                parameter("q", normalizedOriginal)
                parameter("langpair", "$sourceLang|$targetLang")
                email?.let { parameter("de", it) }
            }.body()
            if (response.responseStatus == 200 && response.responseData != null && response.responseData.translatedText.isNotBlank()) {
                val translated = response.responseData.translatedText.lowercase().trim()
                if (response.responseData.match < 0.5 && translated == normalizedOriginal) {
                    normalizedOriginal
                } else {
                    println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL: Success: '$normalizedOriginal' -> '$translated'")
                    translated
                }
            } else {
                println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL_ERROR: Failed for '$normalizedOriginal'. Status: ${response.responseStatus}")
                normalizedOriginal
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL_EXCEPTION: for '$normalizedOriginal': ${e.message}")
            normalizedOriginal
        }
    }

    private suspend fun translateIngredientViaLibreTranslateInternal(
        normalizedOriginal: String, sourceLang: String, targetLang: String
    ): String {
        val libreTranslateApiUrl = "https://translate.argosopentech.com/translate"
        println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL: Calling LibreTranslate for '$normalizedOriginal'...")
        return try {
            val response: LibreTranslateResponse = client.post(libreTranslateApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("q" to normalizedOriginal, "source" to sourceLang, "target" to targetLang, "format" to "text"))
            }.body()
            if (response.translatedText != null && response.translatedText.isNotBlank()) {
                val translated = response.translatedText.lowercase().trim()
                println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL: Success: '$normalizedOriginal' -> '$translated'")
                translated
            } else {
                println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL_ERROR: Failed for '$normalizedOriginal'. API Error: ${response.error}")
                normalizedOriginal
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL_EXCEPTION: for '$normalizedOriginal': ${e.message}")
            normalizedOriginal
        }
    }

    suspend fun getRandomRecipes(count: Int = 10): Result<List<RecipeSummary>> {
        println("DEBUG_API_THEMEALDB: getRandomRecipes called")
        return try {
            // Verwende MealDBResponse und MealDBRecipe aus RecipeData.kt
            val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}filter.php") {
                parameter("c", "Seafood")
            }.body()
            println("DEBUG_API_THEMEALDB: getRandomRecipes response meals count: ${response.meals?.size ?: 0}")

            // .toRecipeSummary() ist jetzt eine Erweiterungsfunktion auf MealDBRecipe aus RecipeData.kt
            val recipes = response.meals?.take(count)?.mapNotNull { mealDBRecipe ->
                mealDBRecipe.toRecipeSummary()
            } ?: emptyList()
            Result.success(recipes)
        } catch (e: Exception) {
            println("DEBUG_API_THEMEALDB_ERROR: getRandomRecipes failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getRecipeDetails(recipeId: String): Result<RecipeDetail> {
        println("DEBUG_API_THEMEALDB: getRecipeDetails called for ID: $recipeId")
        return try {
            // Verwende MealDBResponse und MealDBRecipe aus RecipeData.kt
            val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}lookup.php") {
                parameter("i", recipeId)
            }.body()
            val mealDBRecipe = response.meals?.firstOrNull() // Ist jetzt vom Typ MealDBRecipe
            if (mealDBRecipe != null) {
                println("DEBUG_API_THEMEALDB: getRecipeDetails success for ID: $recipeId, Title: ${mealDBRecipe.strMeal}")
                // .toRecipeDetail() ist jetzt eine Erweiterungsfunktion auf MealDBRecipe aus RecipeData.kt
                Result.success(mealDBRecipe.toRecipeDetail())
            } else {
                println("DEBUG_API_THEMEALDB_WARN: getRecipeDetails recipe not found for ID: $recipeId")
                Result.failure(Exception("Rezept nicht gefunden (ID: $recipeId) oder API-Antwort fehlerhaft."))
            }
        } catch (e: Exception) {
            println("DEBUG_API_THEMEALDB_ERROR: getRecipeDetails failed for ID $recipeId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun findRecipesContainingAnyOfIngredients(
        ingredientNames: List<String>,
        maxIngredientsToQuery: Int = 3,
        maxResultsPerIngredient: Int = 5
    ): Result<List<RecipeSummary>> {
        println("DEBUG_API_THEMEALDB: findRecipesContainingAnyOfIngredients called with EN ingredients: $ingredientNames")
        if (ingredientNames.isEmpty()) {
            return Result.success(emptyList())
        }

        val allFoundRecipes = mutableSetOf<RecipeSummary>()

        ingredientNames.take(maxIngredientsToQuery).forEach { ingredientNameEN ->
            val formattedIngredientName = ingredientNameEN.replace(" ", "_").trim()
            if (formattedIngredientName.isEmpty()) return@forEach

            try {
                println("DEBUG_API_THEMEALDB: Querying filter.php for EN ingredient: '$formattedIngredientName'")
                // Verwende MealDBResponse und MealDBRecipe aus RecipeData.kt
                val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}filter.php") {
                    parameter("i", formattedIngredientName)
                }.body()
                println("DEBUG_API_THEMEALDB: Response for EN '$formattedIngredientName' meals count: ${response.meals?.size ?: 0}")

                response.meals?.take(maxResultsPerIngredient)?.forEach { mealDBRecipe ->
                    // .toRecipeSummary() ist jetzt eine Erweiterungsfunktion auf MealDBRecipe aus RecipeData.kt
                    allFoundRecipes.add(mealDBRecipe.toRecipeSummary())
                }
            } catch (e: Exception) {
                println("DEBUG_API_THEMEALDB_ERROR: findRecipes... - Error for EN ingredient '$formattedIngredientName': ${e.message}")
            }
        }
        println("DEBUG_API_THEMEALDB: findRecipes... - Total unique recipes found: ${allFoundRecipes.size}")
        return Result.success(allFoundRecipes.toList())
    }
}