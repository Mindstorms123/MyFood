package com.example.myfood.data.recipe

// Importiere ALLES Notwendige aus RecipeData.kt
// Ingredient und IngredientPresentation werden indirekt über die oberen Klassen verwendet
// und die Mapping-Funktionen toRecipeSummary/toRecipeDetail sind als suspend Erweiterungen in RecipeData.kt

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

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


}