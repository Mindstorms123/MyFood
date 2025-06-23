package com.example.myfood.data.pantry // Or your preferred package name

import com.example.myfood.FoodItem
import kotlinx.coroutines.flow.Flow

interface PantryRepository {
    fun getFoodItemsStream(): Flow<List<FoodItem>>
    suspend fun getFoodItems(): List<FoodItem> // For one-time fetch
    suspend fun addFoodItem(foodItem: FoodItem)
    suspend fun updateFoodItem(foodItem: FoodItem)
    suspend fun deleteFoodItem(foodItemId: String)
}