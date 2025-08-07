package com.example.myfood.data.pantry // Or your preferred package name for DAOs

import androidx.room.*
import com.example.myfood.FoodItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {

    @Query("SELECT * FROM food_items ORDER BY name ASC") // Assuming your table is 'food_items'
    fun getAllItemsStream(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items ORDER BY name ASC")
    suspend fun getAllItems(): List<FoodItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(foodItem: FoodItem)

    // NEU: Methode zum Einfügen einer Liste von FoodItems
    @Insert(onConflict = OnConflictStrategy.REPLACE) // Oder eine andere passende Strategie
    suspend fun insertItems(items: List<FoodItem>) // Erwartet eine Liste von FoodItem-Objekten

    @Update
    suspend fun update(foodItem: FoodItem)

    @Delete
    suspend fun delete(foodItem: FoodItem) // Standard delete by entity

    // Example method to get by a specific field (like openFoodFactsId if it's unique)
    // Ensure 'openFoodFactsId' is a column name in your FoodItem entity
    @Query("SELECT * FROM food_items WHERE openFoodFactsId = :openFoodFactsId LIMIT 1")
    suspend fun getItemByOpenFoodFactsId(openFoodFactsId: String): FoodItem?

    // If you prefer to delete by a String ID directly (assuming 'id' is your primary key and is a String)
    // @Query("DELETE FROM food_items WHERE id = :foodItemId")
    // suspend fun deleteById(foodItemId: String)

}
