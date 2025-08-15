package com.example.myfood.data.pantry

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.myfood.FoodItem
// import com.example.myfood.FoodStore // Nicht unbedingt hier benötigt, wenn Key hier definiert ist
import com.example.myfood.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
//import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val INTERNAL_FOOD_ITEM_LIST_KEY = androidx.datastore.preferences.core.stringPreferencesKey("food_item_objects_list")

@Singleton
class DataStorePantryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PantryRepository { // Stelle sicher, dass PantryRepository "addFoodItems" deklariert

    override fun getFoodItemsStream(): Flow<List<FoodItem>> {
        return context.dataStore.data.map { preferences ->
            val jsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            if (jsonString.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(jsonString)
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in stream: ${e.message}")
                    emptyList()
                }
            }
        }
    }

    override suspend fun getFoodItems(): List<FoodItem> {
        return getFoodItemsStream().firstOrNull() ?: emptyList()
    }

    override suspend fun addFoodItem(foodItem: FoodItem) {
        context.dataStore.edit { preferences ->
            val currentJsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            val currentList = if (currentJsonString.isNullOrEmpty()) {
                mutableListOf()
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(currentJsonString).toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in addFoodItem: ${e.message}")
                    mutableListOf()
                }
            }
            currentList.add(foodItem)
            try {
                preferences[INTERNAL_FOOD_ITEM_LIST_KEY] = Json.encodeToString(currentList)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error encoding food item list in addFoodItem: ${e.message}")
            }
        }
    }

    // <<< NEUE METHODE HIER IMPLEMENTIERT <<<
    override suspend fun addFoodItems(items: List<FoodItem>) {
        if (items.isEmpty()) return // Nichts zu tun, wenn die Liste leer ist

        context.dataStore.edit { preferences ->
            val currentJsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            val currentList = if (currentJsonString.isNullOrEmpty()) {
                mutableListOf()
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(currentJsonString).toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in addFoodItems: ${e.message}")
                    mutableListOf() // Fallback bei Fehler, fügt zu einer neuen Liste hinzu
                }
            }

            currentList.addAll(items) // Füge alle neuen Items hinzu

            try {
                preferences[INTERNAL_FOOD_ITEM_LIST_KEY] = Json.encodeToString(currentList)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error encoding food item list in addFoodItems: ${e.message}")
                // Optional: Fehlerbehandlung, z.B. Transaktion nicht übernehmen
            }
        }
    }

    override suspend fun updateFoodItem(foodItem: FoodItem) {
        context.dataStore.edit { preferences ->
            val currentJsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            val currentList = if (currentJsonString.isNullOrEmpty()) {
                mutableListOf() // Sollte eigentlich nicht passieren, wenn Item zum Updaten existieren soll
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(currentJsonString).toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in updateFoodItem: ${e.message}")
                    mutableListOf() // Fallback
                }
            }

            val index = currentList.indexOfFirst { it.id == foodItem.id }
            if (index != -1) {
                currentList[index] = foodItem
            } else {
                println("DataStorePantryRepository: Item mit ID ${foodItem.id} nicht zum Aktualisieren gefunden.")
                // Optional: Hier das Item hinzufügen, wenn es nicht existiert und das gewünscht ist.
                // currentList.add(foodItem)
            }

            try {
                preferences[INTERNAL_FOOD_ITEM_LIST_KEY] = Json.encodeToString(currentList)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error encoding food item list in updateFoodItem: ${e.message}")
            }
        }
    }

    override suspend fun deleteFoodItem(foodItemId: String) {
        context.dataStore.edit { preferences ->
            val currentJsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            val currentList = if (currentJsonString.isNullOrEmpty()) {
                return@edit // Nichts zu löschen
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(currentJsonString).toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in deleteFoodItem: ${e.message}")
                    return@edit // Fehler beim Dekodieren, nichts ändern
                }
            }

            val removed = currentList.removeAll { it.id == foodItemId }
            if (removed) {
                println("DataStorePantryRepository: Item mit ID $foodItemId gelöscht.")
            } else {
                println("DataStorePantryRepository: Item mit ID $foodItemId nicht zum Löschen gefunden.")
            }

            try {
                preferences[INTERNAL_FOOD_ITEM_LIST_KEY] = Json.encodeToString(currentList)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error encoding food item list in deleteFoodItem: ${e.message}")
            }
        }
    }
}

