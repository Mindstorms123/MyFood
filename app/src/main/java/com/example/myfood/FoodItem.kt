package com.example.myfood

import kotlinx.serialization.SerialName
import java.util.UUID // Für FoodItem ID
import kotlinx.serialization.Serializable

@Serializable
data class ProductFromAPI( // Für die API-Antwort von OpenFoodFacts
    val product_name: String? = null,
    val brands: String? = null,
    @SerialName("_keywords")
    val _keywords: List<String>? = null
)

@Serializable // Wichtig für DataStore mit Json
data class FoodItem(
    val id: String = UUID.randomUUID().toString(), // Eindeutige ID
    var name: String,
    var brand: String? = null,
    var quantity: Int = 1 // Standardmenge ist 1
)

// Optional: Wenn du eine gemeinsame Datei für Netzwerk-Datenklassen möchtest,
// könnte ProductResponse auch hier stehen.
@Serializable
data class ProductResponse( // Für die Ktor-Deserialisierung in FoodScreen.kt
    val status: Int,
    @SerialName("product") // Stellt sicher, dass das JSON-Feld "product" korrekt zugeordnet wird
    val product: ProductFromAPI? = null
)