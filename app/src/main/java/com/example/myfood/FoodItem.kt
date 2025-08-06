package com.example.myfood

import androidx.room.Entity
import kotlinx.serialization.SerialName
import java.util.UUID // Für FoodItem ID
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlinx.serialization.Contextual
// Importiere OFFProduct, wenn du es direkt in ProductResponse verwenden willst
import com.example.myfood.data.openfoodfacts.OFFProduct

/* ProductFromAPI ist jetzt auskommentiert oder gelöscht - GUT SO!
@Serializable
data class ProductFromAPI(
    @SerialName("code") // Annahme: Das ID-Feld in JSON heißt "code"
    val id: String? = null, // ID des Produkts von OFF
    val product_name: String? = null,
    val brands: String? = null,
    @SerialName("_keywords")
    val _keywords: List<String>? = null
)*/

@Serializable // Wichtig für DataStore mit Json
@Entity(tableName = "food_items") // For Room
data class FoodItem(
    val id: String = UUID.randomUUID().toString(), // Eindeutige ID des Vorrats-Items
    var name: String,
    var brand: String? = null,
    var quantity: Int = 1,
    val openFoodFactsId: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val expiryDate: LocalDate? = null
)

@Serializable
data class ProductResponse( // Für die Ktor-Deserialisierung in FoodScreen.kt (z.B. Barcode-Scan)
    val status: Int,
    @SerialName("product")
    // ÄNDERUNG HIER: Verwende OFFProduct? anstelle von ProductFromAPI?
    val product: OFFProduct? = null
)