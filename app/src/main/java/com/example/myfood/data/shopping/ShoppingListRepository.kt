package com.example.myfood.data.shopping

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ShoppingListRepository @Inject constructor(
    private val shoppingListDao: ShoppingListDao
) {
    fun getAllItems(): Flow<List<ShoppingListItem>> = shoppingListDao.getAllItems()

    suspend fun insertItem(item: ShoppingListItem) {
        shoppingListDao.insertItem(item)
    }

    suspend fun insertItems(items: List<ShoppingListItem>) {
        shoppingListDao.insertItems(items)
    }

    suspend fun updateItem(item: ShoppingListItem) {
        shoppingListDao.updateItem(item)
    }

    // NEU: Methode zum Löschen eines Items anhand seiner ID
    suspend fun deleteItemById(itemId: Int) {
        shoppingListDao.deleteItemById(itemId) // Diese Methode muss im DAO existieren
    }

    // NEU: Methode zum Löschen einer Liste von Items
    suspend fun deleteItems(items: List<ShoppingListItem>) {
        shoppingListDao.deleteItems(items) // Diese Methode muss im DAO existieren
    }

    suspend fun getItemById(id: Int): ShoppingListItem? {
        return shoppingListDao.getItemById(id)
    }
}
