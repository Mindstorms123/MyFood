package com.example.myfood.data.pantry

import android.content.Context
import androidx.datastore.preferences.core.edit // Sicherstellen, dass dieser Import da ist
import com.example.myfood.FoodItem
import com.example.myfood.FoodStore // Wird für getFoodItemsStream weiterhin benötigt
import com.example.myfood.dataStore // Direkter Zugriff auf die DataStore-Extension-Property
import com.example.myfood.FoodStore.FOOD_ITEM_LIST_KEY // Key aus FoodStore.kt (oder hier neu definieren)
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Den Key hier definieren oder aus FoodStore importieren, wenn er public ist.
// Für Klarheit hier explizit:
private val INTERNAL_FOOD_ITEM_LIST_KEY = androidx.datastore.preferences.core.stringPreferencesKey("food_item_objects_list")


@Singleton
class DataStorePantryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PantryRepository {

    override fun getFoodItemsStream(): Flow<List<FoodItem>> {
        // Diese Methode bleibt, da sie den Flow direkt vom DataStore nimmt
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

    // Diese Hilfsmethode wird nicht mehr direkt für Schreiboperationen verwendet,
    // kann aber intern nützlich sein, wenn man wirklich einmalig den aktuellen Stand braucht.
    override suspend fun getFoodItems(): List<FoodItem> {
        return getFoodItemsStream().firstOrNull() ?: emptyList()
    }

    override suspend fun addFoodItem(foodItem: FoodItem) {
        context.dataStore.edit { preferences -> // Lese-Modifiziere-Schreib ATOMAR
            val currentJsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            val currentList = if (currentJsonString.isNullOrEmpty()) {
                mutableListOf()
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(currentJsonString).toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in addFoodItem: ${e.message}")
                    mutableListOf() // Fallback bei Fehler
                }
            }

            currentList.add(foodItem)

            try {
                preferences[INTERNAL_FOOD_ITEM_LIST_KEY] = Json.encodeToString(currentList)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error encoding food item list in addFoodItem: ${e.message}")
                // Optional: Die Änderung nicht übernehmen, wenn das Encoding fehlschlägt,
                // indem man den Key nicht setzt oder eine Exception wirft, um die Transaktion abzubrechen.
            }
        }
    }

    override suspend fun updateFoodItem(foodItem: FoodItem) {
        context.dataStore.edit { preferences -> // Lese-Modifiziere-Schreib ATOMAR
            val currentJsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            val currentList = if (currentJsonString.isNullOrEmpty()) {
                mutableListOf()
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(currentJsonString).toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in updateFoodItem: ${e.message}")
                    mutableListOf()
                }
            }

            val index = currentList.indexOfFirst { it.id == foodItem.id }
            if (index != -1) {
                currentList[index] = foodItem
            } else {
                // WICHTIG: Wenn das Item nicht gefunden wurde, solltest du es hier NICHT einfach hinzufügen,
                // da das die Erwartungshaltung einer "update"-Operation verletzt.
                // Logge einen Fehler oder handle es spezifisch. Fürs Erste lassen wir es so,
                // dass keine Aktion passiert, wenn die ID nicht existiert.
                println("DataStorePantryRepository (atomic): Item mit ID ${foodItem.id} nicht zum Aktualisieren gefunden.")
                // currentList.add(foodItem) // Entferne den Fallback zum Hinzufügen hier für eine saubere Update-Logik
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
        context.dataStore.edit { preferences -> // Lese-Modifiziere-Schreib ATOMAR
            val currentJsonString = preferences[INTERNAL_FOOD_ITEM_LIST_KEY]
            val currentList = if (currentJsonString.isNullOrEmpty()) {
                mutableListOf()
            } else {
                try {
                    Json.decodeFromString<List<FoodItem>>(currentJsonString).toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error decoding food item list in deleteFoodItem: ${e.message}")
                    mutableListOf()
                }
            }

            val removed = currentList.removeAll { it.id == foodItemId }
            if (removed) {
                println("DataStorePantryRepository (atomic): Item mit ID $foodItemId gelöscht.")
            } else {
                println("DataStorePantryRepository (atomic): Item mit ID $foodItemId nicht zum Löschen gefunden.")
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
