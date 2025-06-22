package com.example.myfood.data.shopping

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface ShoppingListDao {
    @androidx.room.Query("SELECT * FROM shopping_list_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<ShoppingListItem>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingListItem)

    @androidx.room.Update
    suspend fun updateItem(item: ShoppingListItem)

    @androidx.room.Delete
    suspend fun deleteItem(item: ShoppingListItem)

    @androidx.room.Query("DELETE FROM shopping_list_items WHERE isChecked = 1")
    suspend fun deleteCheckedItems()

    @androidx.room.Query("SELECT * FROM shopping_list_items WHERE id = :id")
    suspend fun getItemById(id: Int): ShoppingListItem?
}