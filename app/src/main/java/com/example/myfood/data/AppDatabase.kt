package com.example.myfood.data // Or your appropriate package

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // <<< NEUER IMPORT
import com.example.myfood.data.local.Converters // <<< NEUER IMPORT (passe den Pfad an, falls nötig)
import com.example.myfood.data.local.RecipeDao
import com.example.myfood.data.model.Recipe
import com.example.myfood.data.shopping.ShoppingListDao
import com.example.myfood.data.shopping.ShoppingListItem

@Database(
    entities = [
        ShoppingListItem::class,
        Recipe::class
        // Füge hier weitere Entitäten hinzu, falls vorhanden
    ],
    version = 2, // Behalte die Version bei oder erhöhe sie, falls nötig
    exportSchema = false
)
@TypeConverters(Converters::class) // <<< HIER DIE TYPECONVERTERS REGISTRIEREN
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun recipeDao(): RecipeDao

    // ... (der Rest deiner Klasse, wie das companion object, kann bleiben,
    //      aber wie besprochen, überlege dir dessen Notwendigkeit bei Verwendung von Hilt)

    companion object
}

