package com.example.myfood.data.shopping

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject // Wenn du Hilt/Dagger verwendest

class ShoppingListRepository @Inject constructor( // Oder ohne @Inject, wenn du es manuell erstellst
    private val shoppingListDao: ShoppingListDao
) {
    fun getAllItems(): Flow<List<ShoppingListItem>> = shoppingListDao.getAllItems()

    suspend fun insertItem(item: ShoppingListItem) {
        shoppingListDao.insertItem(item)
    }

    suspend fun updateItem(item: ShoppingListItem) {
        shoppingListDao.updateItem(item)
    }

    suspend fun deleteItem(item: ShoppingListItem) {
        shoppingListDao.deleteItem(item)
    }

    suspend fun deleteCheckedItems() {
        shoppingListDao.deleteCheckedItems()
    }

    suspend fun getItemById(id: Int): ShoppingListItem? {
        return shoppingListDao.getItemById(id)
    }
}