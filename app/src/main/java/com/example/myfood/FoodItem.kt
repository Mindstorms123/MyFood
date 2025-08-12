package com.example.myfood

// Importiere OFFProduct, wenn du es direkt in ProductResponse verwenden willst
import androidx.room.Entity
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

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

