package com.example.myfood.data.recipe

import kotlinx.serialization.Serializable

// --- Datenmodelle für die App-interne Verwendung ---

@Serializable
data class Ingredient(
    val name: String,
    val measure: String? = null // Menge/Einheit
)


// --- Datenmodelle für die TheMealDB API-Antwort ---
// Diese bleiben so, wie sie von der API kommen.


// --- Erweiterungsfunktionen für das Mapping und die Übersetzung ---

