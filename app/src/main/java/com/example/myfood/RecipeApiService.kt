package com.example.myfood.data.recipe

// Importiere ALLES Notwendige aus RecipeData.kt
import com.example.myfood.data.recipe.MealDBRecipe // WICHTIG
import com.example.myfood.data.recipe.MealDBResponse // WICHTIG (die Version aus RecipeData.kt)
import com.example.myfood.data.recipe.RecipeDetail // WICHTIG
import com.example.myfood.data.recipe.RecipeSummary // WICHTIG
// Ingredient und IngredientPresentation werden indirekt über die oberen Klassen verwendet
// und die Mapping-Funktionen toRecipeSummary/toRecipeDetail sind als suspend Erweiterungen in RecipeData.kt

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope // Import CoroutineScope
import kotlinx.coroutines.Deferred      // Import Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

// Basis-URL für TheMealDB API
private const val BASE_URL_THEMEALDB = "https://www.themealdb.com/api/json/v1/1/"

// --- Statische lokale Übersetzungsmap ---
val GERMAN_TO_ENGLISH_INGREDIENT_MAP: Map<String, String> = mapOf(
    "mehl" to "flour",
    "weizenmehl" to "wheat flour",
    // ... (deine umfangreiche Map bleibt hier)
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
                requestTimeoutMillis = 20000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }
    }

    private val translationCache = ConcurrentHashMap<String, String>()
    private const val MYMEMORY_API_URL = "https://api.mymemory.translated.net/get"
    private const val LIBRETRANSLATE_API_URL = "https://translate.argosopentech.com/translate"

    suspend fun translateIngredient(
        ingredientName: String,
        sourceLang: String = "de",
        targetLang: String = "en",
        useLibreTranslateAsFallback: Boolean = true,
        myMemoryEmail: String? = null
    ): String {
        val normalizedOriginal = ingredientName.lowercase().trim()
        if (normalizedOriginal.isEmpty() || sourceLang == targetLang) return ingredientName

        val cacheKey = "$sourceLang-$targetLang:$normalizedOriginal"
        translationCache[cacheKey]?.let {
            println("DEBUG_API_TRANSLATE_INGREDIENT: Cache HIT for '$cacheKey' -> '$it'")
            return it
        }
        println("DEBUG_API_TRANSLATE_INGREDIENT: Cache MISS for '$cacheKey'.")

        if (sourceLang == "de" && targetLang == "en") {
            GERMAN_TO_ENGLISH_INGREDIENT_MAP[normalizedOriginal]?.let { localTranslation ->
                if (localTranslation != normalizedOriginal) {
                    println("DEBUG_API_TRANSLATE_INGREDIENT: Local Map HIT for '$normalizedOriginal' -> '$localTranslation'")
                    translationCache[cacheKey] = localTranslation
                    return localTranslation
                } else {
                    println("DEBUG_API_TRANSLATE_INGREDIENT: Local Map HIT for '$normalizedOriginal' but translation is same as original. Proceeding to online translation.")
                }
            } ?: println("DEBUG_API_TRANSLATE_INGREDIENT: Local Map MISS for DE->EN '$normalizedOriginal'.")
        }

        var translatedName = translateViaMyMemory(normalizedOriginal, sourceLang, targetLang, myMemoryEmail)

        if (translatedName == normalizedOriginal && useLibreTranslateAsFallback) {
            println("DEBUG_API_TRANSLATE_INGREDIENT: MyMemory returned original or failed for '$normalizedOriginal'. Attempting LibreTranslate...")
            translatedName = translateViaLibreTranslate(normalizedOriginal, sourceLang, targetLang)
        }

        if (translatedName.isNotBlank() && translatedName != normalizedOriginal) {
            println("DEBUG_API_TRANSLATE_INGREDIENT: Final translation for '$normalizedOriginal' ($sourceLang->$targetLang) -> '$translatedName'. Caching.")
            translationCache[cacheKey] = translatedName
        } else {
            println("DEBUG_API_TRANSLATE_INGREDIENT: All translation attempts failed or returned original for '$normalizedOriginal'. Using original.")
            if (normalizedOriginal.isNotBlank()){
                translationCache[cacheKey] = normalizedOriginal
            }
        }
        return translatedName.ifBlank { normalizedOriginal }
    }

    suspend fun translateText(
        textToTranslate: String,
        sourceLang: String = "en",
        targetLang: String = "de",
        myMemoryEmail: String? = null
    ): String {
        val trimmedText = textToTranslate.trim()
        if (trimmedText.isBlank() || sourceLang == targetLang) {
            return textToTranslate
        }

        val cacheKey = "$sourceLang-$targetLang:${trimmedText.hashCode()}"
        translationCache[cacheKey]?.let {
            println("DEBUG_API_TRANSLATE_TEXT: Cache HIT for text hash '$cacheKey' -> '${it.take(50)}...'")
            return it
        }
        println("DEBUG_API_TRANSLATE_TEXT: Cache MISS for text hash '$cacheKey'. Text: '${trimmedText.take(50)}...'")

        var translatedText = translateViaLibreTranslate(trimmedText, sourceLang, targetLang)

        if (translatedText == trimmedText) {
            println("DEBUG_API_TRANSLATE_TEXT: LibreTranslate returned original or failed for text. Attempting MyMemory...")
            translatedText = translateViaMyMemory(trimmedText, sourceLang, targetLang, myMemoryEmail)
        }

        if (translatedText.isNotBlank() && translatedText != trimmedText) {
            println("DEBUG_API_TRANSLATE_TEXT: Final translation for text hash '$cacheKey' -> '${translatedText.take(50)}...'. Caching.")
            translationCache[cacheKey] = translatedText
        } else {
            println("DEBUG_API_TRANSLATE_TEXT: All translation attempts failed or returned original. Using original. Caching original.")
            translationCache[cacheKey] = trimmedText
        }
        return translatedText.ifBlank { trimmedText }
    }

    private suspend fun translateViaMyMemory(
        text: String, sourceLang: String, targetLang: String, email: String?
    ): String {
        println("DEBUG_API_TRANSLATE_MYMEMORY: Calling MyMemory for '$text' ($sourceLang->$targetLang)...")
        return try {
            val response: MyMemoryResponse = client.get(MYMEMORY_API_URL) {
                parameter("q", text)
                parameter("langpair", "$sourceLang|$targetLang")
                email?.let { parameter("de", it) }
            }.body()

            if (response.responseStatus == 200 && response.responseData != null && response.responseData.translatedText.isNotBlank()) {
                val translated = response.responseData.translatedText.lowercase().trim()
                if (response.responseData.match < 0.7 && translated == text) {
                    println("DEBUG_API_TRANSLATE_MYMEMORY: Low match (${response.responseData.match}) and original returned for '$text'. Returning original.")
                    text
                } else {
                    println("DEBUG_API_TRANSLATE_MYMEMORY: Success: '$text' -> '$translated' (Match: ${response.responseData.match})")
                    translated
                }
            } else {
                println("DEBUG_API_TRANSLATE_MYMEMORY_ERROR: Failed for '$text'. Status: ${response.responseStatus}, Details: ${response.responseDetails}")
                text
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_MYMEMORY_EXCEPTION: for '$text': ${e.message}")
            text
        }
    }

    private suspend fun translateViaLibreTranslate(
        text: String, sourceLang: String, targetLang: String
    ): String {
        println("DEBUG_API_TRANSLATE_LIBRE: Calling LibreTranslate for '${text.take(50)}...' ($sourceLang->$targetLang)...")
        return try {
            val requestBody = mapOf("q" to text, "source" to sourceLang, "target" to targetLang, "format" to "text")
            val response: LibreTranslateResponse = client.post(LIBRETRANSLATE_API_URL) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            if (response.translatedText != null && response.translatedText.isNotBlank()) {
                val translated = if (text.length > 20) response.translatedText.trim() else response.translatedText.lowercase().trim()
                println("DEBUG_API_TRANSLATE_LIBRE: Success for '${text.take(50)}...' -> '${translated.take(50)}...'")
                translated
            } else {
                println("DEBUG_API_TRANSLATE_LIBRE_ERROR: Failed for '${text.take(50)}...'. API Error: ${response.error}")
                text
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_LIBRE_EXCEPTION: for '${text.take(50)}...': ${e.message}")
            text
        }
    }

    suspend fun getRandomRecipes(count: Int = 10, targetLang: String = "de"): Result<List<RecipeSummary>> {
        println("DEBUG_API_THEMEALDB: getRandomRecipes called for $count recipes, targetLang: $targetLang")
        // Führe Netzwerkanfragen im IO-Dispatcher aus
        return withContext(Dispatchers.IO) {
            try {
                val fetchedMealDBRecipes = mutableListOf<MealDBRecipe>()

                // Erstelle eine Liste von Deferred<MealDBRecipe?> für parallele API-Aufrufe
                // 'this' innerhalb des async-Blocks ist jetzt CoroutineScope
                val jobs: List<Deferred<MealDBRecipe?>> = List(count) {
                    async { // 'async' wird hier im Kontext von 'withContext(Dispatchers.IO)' aufgerufen
                        try {
                            println("DEBUG_API_THEMEALDB: Fetching one random recipe (attempt ${it + 1}/$count)")
                            val randomMealResponse: MealDBResponse = client.get("${BASE_URL_THEMEALDB}random.php").body()
                            randomMealResponse.meals?.firstOrNull()
                        } catch (e: Exception) {
                            println("DEBUG_API_THEMEALDB_WARN: Single random.php call (attempt ${it + 1}) failed: ${e.message}")
                            null
                        }
                    }
                }

                jobs.awaitAll().forEach { mealDBRecipe ->
                    mealDBRecipe?.let { fetchedMealDBRecipes.add(it) }
                }

                println("DEBUG_API_THEMEALDB: Fetched ${fetchedMealDBRecipes.size} recipes from random.php calls.")
                val uniqueMealDBRecipes = fetchedMealDBRecipes.distinctBy { it.idMeal }
                println("DEBUG_API_THEMEALDB: After distinctBy idMeal, ${uniqueMealDBRecipes.size} unique recipes remaining.")

                val recipeSummaries = uniqueMealDBRecipes.mapNotNull { mealDBRecipe ->
                    try {
                        // Wichtig: 'this@RecipeApiService' verwenden, um auf das äußere Objekt zuzugreifen
                        mealDBRecipe.toRecipeSummary(this@RecipeApiService, targetLang)
                    } catch (e: Exception) {
                        println("DEBUG_API_THEMEALDB_ERROR: Failed to convert MealDBRecipe (ID: ${mealDBRecipe.idMeal}) to RecipeSummary: ${e.message}")
                        null
                    }
                }

                println("DEBUG_API_THEMEALDB: Successfully converted ${recipeSummaries.size} recipes to RecipeSummary for targetLang '$targetLang'.")

                if (recipeSummaries.isEmpty() && count > 0 && uniqueMealDBRecipes.isNotEmpty()) {
                    println("DEBUG_API_THEMEALDB_WARN: All fetched unique MealDBRecipes failed to convert to RecipeSummary.")
                } else if (recipeSummaries.isEmpty() && count > 0) {
                    println("DEBUG_API_THEMEALDB_WARN: No recipes could be successfully fetched and converted after $count attempts.")
                }
                Result.success(recipeSummaries)
            } catch (e: Exception) {
                println("DEBUG_API_THEMEALDB_ERROR: Overall getRandomRecipes failed: ${e.message}")
                Result.failure(e)
            }
        }
    }


    suspend fun getRecipeDetails(recipeId: String, targetLang: String = "de"): Result<RecipeDetail> {
        println("DEBUG_API_THEMEALDB: getRecipeDetails called for ID: $recipeId, targetLang: $targetLang")
        return withContext(Dispatchers.IO) { // Netzwerk im IO-Dispatcher
            try {
                val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}lookup.php") {
                    parameter("i", recipeId)
                }.body()
                val mealDBRecipe = response.meals?.firstOrNull()
                if (mealDBRecipe != null) {
                    println("DEBUG_API_THEMEALDB: getRecipeDetails success for ID: $recipeId, Title: ${mealDBRecipe.strMeal}")
                    // Wichtig: 'this@RecipeApiService' verwenden
                    Result.success(mealDBRecipe.toRecipeDetail(this@RecipeApiService, targetLang))
                } else {
                    println("DEBUG_API_THEMEALDB_WARN: getRecipeDetails recipe not found for ID: $recipeId")
                    Result.failure(Exception("Rezept nicht gefunden (ID: $recipeId) oder API-Antwort fehlerhaft."))
                }
            } catch (e: Exception) {
                println("DEBUG_API_THEMEALDB_ERROR: getRecipeDetails failed for ID $recipeId: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun findRecipesContainingAnyOfIngredients(
        ingredientNamesDE: List<String>,
        maxIngredientsToQuery: Int = 1,
        maxResultsPerIngredient: Int = 5,
        targetLang: String = "de"
    ): Result<List<RecipeSummary>> {
        println("DEBUG_API_THEMEALDB: findRecipes... called with DE ingredients: $ingredientNamesDE, targetLang: $targetLang")
        if (ingredientNamesDE.isEmpty()) {
            return Result.success(emptyList())
        }

        // Die Übersetzung der Zutaten kann hier bleiben oder in den withContext-Block verschoben werden,
        // da translateIngredient selbst suspend ist und intern Dispatcher handhaben könnte.
        // Zur Konsistenz verschieben wir es in den IO-Kontext.
        return withContext(Dispatchers.IO) {
            try {
                val allFoundRecipes = mutableSetOf<RecipeSummary>()

                val ingredientNamesEN = ingredientNamesDE.take(maxIngredientsToQuery).mapNotNull { deName ->
                    // Wichtig: 'this@RecipeApiService' für den Aufruf der Methode des Objekts
                    val enName = this@RecipeApiService.translateIngredient(deName, sourceLang = "de", targetLang = "en")
                    if (enName != deName && enName.isNotBlank()) enName.replace(" ", "_").trim() else null
                }.filter { it.isNotBlank() }

                if (ingredientNamesEN.isEmpty()) {
                    println("DEBUG_API_THEMEALDB: findRecipes... - No valid English translations for ingredients found.")
                    return@withContext Result.success(emptyList()) // Frühzeitiger Ausstieg aus withContext
                }

                println("DEBUG_API_THEMEALDB: Querying with EN ingredients: $ingredientNamesEN")

                val jobs: List<Deferred<List<RecipeSummary>>> = ingredientNamesEN.map { formattedIngredientNameEN ->
                    async { // 'async' wird hier im Kontext von 'withContext(Dispatchers.IO)' aufgerufen
                        try {
                            println("DEBUG_API_THEMEALDB: Querying filter.php for EN ingredient: '$formattedIngredientNameEN'")
                            val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}filter.php") {
                                parameter("i", formattedIngredientNameEN)
                            }.body()
                            println("DEBUG_API_THEMEALDB: Response for EN '$formattedIngredientNameEN' meals count: ${response.meals?.size ?: 0}")

                            response.meals?.take(maxResultsPerIngredient)?.mapNotNull { mealDBRecipe ->
                                try {
                                    // Wichtig: 'this@RecipeApiService'
                                    mealDBRecipe.toRecipeSummary(this@RecipeApiService, targetLang)
                                } catch (e: Exception) {
                                    println("DEBUG_API_THEMEALDB_ERROR: Failed to convert MealDBRecipe from filter.php (ID: ${mealDBRecipe.idMeal}) to RecipeSummary: ${e.message}")
                                    null
                                }
                            } ?: emptyList()
                        } catch (e: Exception) {
                            println("DEBUG_API_THEMEALDB_ERROR: findRecipes... - Error for EN ingredient '$formattedIngredientNameEN': ${e.message}")
                            emptyList<RecipeSummary>()
                        }
                    }
                }
                jobs.awaitAll().forEach { recipeList ->
                    allFoundRecipes.addAll(recipeList)
                }

                println("DEBUG_API_THEMEALDB: findRecipes... - Total unique recipes found after filtering: ${allFoundRecipes.size}")
                Result.success(allFoundRecipes.toList().distinctBy { it.id })
            } catch (e: Exception) {
                println("DEBUG_API_THEMEALDB_ERROR: Overall findRecipes... failed: ${e.message}")
                Result.failure(e)
            }
        }
    }
}