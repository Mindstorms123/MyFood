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
    @SerialName("_id") val id: String, // Produkt-Barcode
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_de") val productNameDE: String? = null, // Spezifisch für Deutsch
    @SerialName("product_name_en") val productNameEN: String? = null,
    val brands: String? = null,
    @SerialName("image_small_url") val imageSmallUrl: String? = null,
    // Füge weitere Felder hinzu, die du anzeigen möchtest (z.B. quantity, nutriscore_grade etc.)
) {
    // Hilfsfunktion, um den besten verfügbaren Produktnamen zu bekommen
    fun getDisplayName(): String {
        return productNameDE?.takeIf { it.isNotBlank() }
            ?: productNameEN?.takeIf { it.isNotBlank() }
            ?: productName?.takeIf { it.isNotBlank() }
            ?: "Unbekanntes Produkt"
    }
}