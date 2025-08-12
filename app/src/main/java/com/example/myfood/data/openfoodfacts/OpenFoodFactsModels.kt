package com.example.myfood.data.openfoodfacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OFFSearchResponse(
    val count: Int,
    @SerialName("page_size") val pageSize: String, // Kann auch Int sein, API ist etwas inkonsistent
    val products: List<OFFProduct>,
    val page: Int
)

@Serializable
data class OFFProduct(
    // === Fields from your existing OFFProduct in OpenFoodFactsModels.kt ===
    @SerialName("_id") val id: String, // Produkt-Barcode (Beibehalten, da _id oft der primäre Identifikator ist)
    // Wenn die API 'code' als Barcode sendet und nicht '_id', ändere dies zu:
    // @SerialName("code") val id: String,

    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_de") val productNameDE: String? = null, // Spezifisch für Deutsch
    @SerialName("product_name_en") val productNameEN: String? = null,
    val brands: String? = null,
    @SerialName("image_small_url") val imageSmallUrl: String? = null,

    // === NEW Fields from the OFFProduct definition you want to integrate ===
    @SerialName("generic_name")
    val genericName: String? = null, // Generischer Name

    @SerialName("generic_name_de")
    val genericNameDE: String? = null, // Generischer Name auf Deutsch

    @SerialName("_keywords")
    val keywords: List<String>? = null, // Keywords sind normalerweise eine Liste

    // Füge hier weitere Felder hinzu, die du von der API benötigst, z.B.:
    // @SerialName("image_url")
    // val imageUrl: String? = null, // Größeres Bild, falls image_small_url nicht reicht
    //
    // @SerialName("nutriments")
    // val nutriments: Nutriments? = null, // Wenn du Nährwertinformationen brauchst (erfordert eine weitere Nutriments-Datenklasse)
    //
    // @SerialName("ingredients_text_de")
    // val ingredientsTextDE: String? = null,

    // Weitere Felder von OpenFoodFacts, die nützlich sein könnten:
    // @SerialName("quantity") val quantity: String? = null, // z.B. "500 g"
    // @SerialName("nutriscore_grade") val nutriscoreGrade: String? = null, // z.B. "a", "b", ...
    // @SerialName("nova_group") val novaGroup: Int? = null, // z.B. 1, 2, 3, 4
    // @SerialName("ecoscore_grade") val ecoscoreGrade: String? = null,
    // @SerialName("categories_tags") val categoriesTags: List<String>? = null, // z.B. ["en:plant-based-foods-and-beverages", "de:getraenke"]
    // @SerialName("labels_tags") val labelsTags: List<String>? = null, // z.B. ["en:organic", "en:eu-organic"]
    // @SerialName("stores_tags") val storesTags: List<String>? = null, // z.B. ["rewe", "edeka"]

) {
    // === UPDATED Hilfsfunktion, um den besten verfügbaren Produktnamen zu bekommen ===
    fun getDisplayName(): String {
        return productNameDE?.takeIf { it.isNotBlank() }
            ?: productNameEN?.takeIf { it.isNotBlank() } // Hinzugefügt für bessere Abdeckung
            ?: productName?.takeIf { it.isNotBlank() }
            ?: genericNameDE?.takeIf { it.isNotBlank() }
            ?: genericName?.takeIf { it.isNotBlank() }
            ?: keywords?.firstOrNull { it.isNotBlank() }
                ?.replace("-", " ") // Leerzeichen statt Bindestriche
                ?.replace("_", " ") // Leerzeichen statt Unterstriche
                ?.let { keyword -> // Ersten Buchstaben groß schreiben für bessere Lesbarkeit
                    keyword.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            ?: id
    }
}

// Optional: Wenn du die Nutriments-Struktur verwenden möchtest, definiere sie hier
// @Serializable
// data class Nutriments(
//     @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
//     @SerialName("fat_100g") val fat100g: Double? = null,
//     @SerialName("saturated-fat_100g") val saturatedFat100g: Double? = null,
//     @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
//     @SerialName("sugars_100g") val sugars100g: Double? = null,
//     @SerialName("fiber_100g") val fiber100g: Double? = null,
//     @SerialName("proteins_100g") val proteins100g: Double? = null,
//     @SerialName("salt_100g") val salt100g: Double? = null,
//     // ... weitere Nährwerte nach Bedarf
// )