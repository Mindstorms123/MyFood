package com.example.myfood.data.pantry // Or your preferred package name

import com.example.myfood.FoodItem
import kotlinx.coroutines.flow.Flow

interface PantryRepository {
    fun getFoodItemsStream(): Flow<List<FoodItem>>
    suspend fun getFoodItems(): List<FoodItem> // For one-time fetch
    suspend fun addFoodItem(foodItem: FoodItem)
    suspend fun addFoodItems(items: List<FoodItem>) // <<< NEU HINZUGEFÜGT
    suspend fun updateFoodItem(foodItem: FoodItem)
    suspend fun deleteFoodItem(foodItemId: String) // Beachte: Diese Methode erwartet eine String-ID
    // Wenn dein FoodItem eine Int ID hat, passe das hier und in der Implementierung an.
}

