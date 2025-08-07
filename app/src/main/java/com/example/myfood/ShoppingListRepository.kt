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

    suspend fun updateItem(item: ShoppingListItem) {
        shoppingListDao.updateItem(item)
    }

    // Diese Methode kann bleiben, falls du sie an anderer Stelle noch verwendest.
    // Für den aktuellen ViewModel-Bedarf (Löschen via ID) wird sie nicht direkt genutzt.
    suspend fun deleteItem(item: ShoppingListItem) {
        shoppingListDao.deleteItem(item)
    }

    // NEU: Methode zum Löschen eines Items anhand seiner ID
    suspend fun deleteItemById(itemId: Int) {
        shoppingListDao.deleteItemById(itemId) // Diese Methode muss im DAO existieren
    }

    suspend fun deleteCheckedItems() {
        shoppingListDao.deleteCheckedItems()
    }

    // NEU: Methode zum Löschen einer Liste von Items
    suspend fun deleteItems(items: List<ShoppingListItem>) {
        shoppingListDao.deleteItems(items) // Diese Methode muss im DAO existieren
    }

    suspend fun getItemById(id: Int): ShoppingListItem? {
        return shoppingListDao.getItemById(id)
    }
}
