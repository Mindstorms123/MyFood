package com.example.myfood.data.model // Passe den Paketnamen an

import androidx.room.Entity
import androidx.room.PrimaryKey

// Datenklasse für eine Zutat
data class Ingredient(
    val name: String,
    val quantity: String? = null, // Menge kann optional sein oder als String flexibler
    val unit: String? = null
)

// Rezept-Entität für die Room-Datenbank
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var title: String = "",
    var ingredients: List<Ingredient> = emptyList(), // Wird durch Converters behandelt
    var instructions: List<String> = emptyList(),   // Wird durch Converters behandelt
    var category: String? = null,
    var imagePath: String? = null,
    var source: String? = null, // z.B. "user", "github"
    var isFavorite: Boolean = false,
    var owner: String? = null, // <<< NEU (optional, für den 'owner' aus YAML)
    var tags: List<String>? = null // <<< NEU (für die 'tags' aus YAML, wird durch Converters behandelt)
)

