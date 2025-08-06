package com.example.myfood

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "food_store_v2") // Neuer Name für den DataStore, um Konflikte zu vermeiden

object FoodStore {
    // Neuer Key-Name, da sich die Datenstruktur ändert (von List<String> zu List<FoodItem>)
    public val FOOD_ITEM_LIST_KEY = stringPreferencesKey("food_item_objects_list")

    // Gibt jetzt Flow<List<FoodItem>> zurück
    fun getFoodList(context: Context): Flow<List<FoodItem>> {
        return context.dataStore.data.map { preferences ->
            val jsonString = preferences[FOOD_ITEM_LIST_KEY]
            if (jsonString.isNullOrEmpty()) {
                emptyList<FoodItem>() // Wichtig: Typisierte leere Liste
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(jsonString)
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list: ${e.message}")
                    emptyList<FoodItem>() // Fallback bei Fehler
                }
            }
        }
    }

    // Akzeptiert jetzt List<FoodItem>
    suspend fun saveFoodList(context: Context, foodItemList: List<FoodItem>) {
        context.dataStore.edit { preferences ->
            try {
                val jsonString = Json.encodeToString(foodItemList)
                preferences[FOOD_ITEM_LIST_KEY] = jsonString
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error encoding food item list: ${e.message}")
            }
        }
    }
}