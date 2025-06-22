package com.example.myfood.data.openfoodfacts

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import javax.inject.Inject // Wenn du Hilt/Dagger verwendest
import javax.inject.Singleton

private const val BASE_URL_OPENFOODFACTS = "https://world.openfoodfacts.org/cgi/" // Basis-URL

@Singleton // Wenn du Hilt/Dagger verwendest
class OpenFoodFactsApiService @Inject constructor() { // Oder mit manuellem Ktor Client

    private val client: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true // Hilfreich beim Debuggen
                })
            }
            // Füge ggf. HttpTimeout hinzu
        }
    }

    /**
     * Sucht nach Produkten in der Open Food Facts Datenbank.
     * @param searchTerm Der Suchbegriff.
     * @param page Die Seitenzahl für Paginierung.
     * @param pageSize Die Anzahl der Ergebnisse pro Seite.
     * @param languageCode Der Sprachcode für die Ergebnisse (z.B. "de" für Deutsch).
     * @return Ein Result-Objekt mit der Liste der gefundenen Produkte oder einem Fehler.
     */
    suspend fun searchProducts(
        searchTerm: String,
        page: Int = 1,
        pageSize: Int = 20,
        languageCode: String = "de" // Für deutsche Produktnamen, wenn verfügbar
    ): Result<List<OFFProduct>> {
        if (searchTerm.isBlank()) {
            return Result.success(emptyList())
        }
        return try {
            // Beispiel-URL: https://world.openfoodfacts.org/cgi/search.pl?search_terms=Apfel&search_simple=1&action=process&json=1&page=1&page_size=20&lc=de
            val response: OFFSearchResponse = client.get("${BASE_URL_OPENFOODFACTS}search.pl") {
                parameter("search_terms", searchTerm)
                parameter("search_simple", 1) // Einfache Suche aktivieren
                parameter("action", "process")
                parameter("json", 1) // JSON-Format anfordern
                parameter("page", page)
                parameter("page_size", pageSize)
                parameter("lc", languageCode) // Sprachcode für bevorzugte Sprache
                // User-Agent setzen ist gute Praxis
                header("User-Agent", "MyFoodApp/1.0 (Android; your.package.name; contact@example.com) - Ktor client")
            }.body()

            if (response.products.isNotEmpty()) {
                Result.success(response.products)
            } else {
                Result.success(emptyList()) // Keine Produkte gefunden, aber Anfrage war erfolgreich
            }
        } catch (e: Exception) {
            println("OFF_API_ERROR: searchProducts failed for '$searchTerm': ${e.message}")
            Result.failure(e)
        }
    }
}