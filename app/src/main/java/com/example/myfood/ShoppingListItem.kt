package com.example.myfood.data.shopping

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_list_items")
data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    var brand: String? = null,
    var quantity: String = "1", // Flexibel als String für "1 Packung", "500g", etc.
    var unit: String = "Stk.", // Stück, g, ml, Packung etc.
    var isChecked: Boolean = false,
    val openFoodFactsProductId: String? = null, // Optionale Produkt-ID von OFF
    val timestamp: Long = System.currentTimeMillis() // Für Sortierung oder spätere Features
)