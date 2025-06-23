package com.example.myfood.data.pantry

import android.content.Context
import com.example.myfood.FoodItem
import com.example.myfood.FoodStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStorePantryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PantryRepository {

    override fun getFoodItemsStream(): Flow<List<FoodItem>> {
        return FoodStore.getFoodList(context)
    }

    override suspend fun getFoodItems(): List<FoodItem> {
        return FoodStore.getFoodList(context).firstOrNull() ?: emptyList()
    }

    override suspend fun addFoodItem(foodItem: FoodItem) {
        val currentList = getFoodItems().toMutableList()
        currentList.add(foodItem)
        FoodStore.saveFoodList(context, currentList)
    }

    override suspend fun updateFoodItem(foodItem: FoodItem) {
        val currentList = getFoodItems().toMutableList()
        val index = currentList.indexOfFirst { it.id == foodItem.id } // Update basiert auf FoodItem.id (UUID)
        if (index != -1) {
            currentList[index] = foodItem
            FoodStore.saveFoodList(context, currentList)
        } else {
            // Fällt zurück zum Hinzufügen, wenn es aus irgendeinem Grund nicht zum Aktualisieren gefunden wurde
            // (sollte nicht passieren, wenn die ID korrekt ist)
            println("DataStorePantryRepository: Item mit ID ${foodItem.id} nicht zum Aktualisieren gefunden, stattdessen hinzugefügt.")
            addFoodItem(foodItem) // Hinzufügen als Fallback
        }
    }

    // --- IMPLEMENTIERUNG HINZUGEFÜGT ---
    override suspend fun deleteFoodItem(foodItemId: String) {
        val currentList = getFoodItems().toMutableList()
        val removed = currentList.removeAll { it.id == foodItemId } // Entfernt alle Items mit dieser ID
        if (removed) {
            FoodStore.saveFoodList(context, currentList)
            println("DataStorePantryRepository: Item mit ID $foodItemId gelöscht.")
        } else {
            println("DataStorePantryRepository: Item mit ID $foodItemId nicht zum Löschen gefunden.")
        }
    }
}