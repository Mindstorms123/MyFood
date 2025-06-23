package com.example.myfood.data // Or your appropriate package

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myfood.data.shopping.ShoppingListDao
import com.example.myfood.data.shopping.ShoppingListItem

@Database(entities = [ShoppingListItem::class], version = 1, exportSchema = false) // Add other entities if you have them
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    // Add other DAOs here if you have them
}