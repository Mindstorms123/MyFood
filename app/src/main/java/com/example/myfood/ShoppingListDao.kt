package com.example.myfood.data.shopping

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface ShoppingListDao {

    // Gibt alle Einkaufslisten-Elemente zurück, sortiert nach Zeitstempel (Neueste zuerst)
    @androidx.room.Query("SELECT * FROM shopping_list_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<ShoppingListItem>>

    // Fügt ein einzelnes Element ein oder ersetzt es, falls die ID bereits existiert.
    // Nützlich, da ShoppingListItem jetzt 'brand' enthält.
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingListItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE) // NEU HINZUGEFÜGT
    suspend fun insertItems(items: List<ShoppingListItem>) // NEU HINZUGEFÜGT

    // Aktualisiert ein bestehendes Element.
    @androidx.room.Update
    suspend fun updateItem(item: ShoppingListItem)

    // Löscht ein einzelnes Element anhand des Objekts.
    @androidx.room.Delete
    suspend fun deleteItem(item: ShoppingListItem)

    // NEU: Löscht ein einzelnes Element anhand seiner ID.
    // Diese Funktion wird im ViewModel für deleteItem(itemId: Int) benötigt.
    @androidx.room.Query("DELETE FROM shopping_list_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Int)

    // Löscht alle Elemente, die als "isChecked = true" markiert sind.
    // Wird für die separate "deleteCheckedItems" Funktion im ViewModel verwendet.
    @androidx.room.Query("DELETE FROM shopping_list_items WHERE isChecked = 1")
    suspend fun deleteCheckedItems()

    // Holt ein einzelnes Element anhand seiner ID.
    // Wird im ViewModel benötigt, z.B. um ein Item vor dem Aktualisieren oder togglen zu laden.
    @androidx.room.Query("SELECT * FROM shopping_list_items WHERE id = :id")
    suspend fun getItemById(id: Int): ShoppingListItem?

    // NEU: Löscht mehrere Elemente auf einmal.
    // Dies ist nützlich für die `transferCheckedItemsToPantryAndClear`-Funktion im ViewModel,
    // um die übertragenen Items effizient von der Einkaufsliste zu entfernen.
    // Room kann eine Liste von Objekten für @Delete verarbeiten.
    @androidx.room.Delete
    suspend fun deleteItems(items: List<ShoppingListItem>)

    // Optional: Falls du das Einfügen mehrerer Items auf einmal benötigst (nicht direkt
    // aus dem aktuellen ViewModel-Code ersichtlich, aber oft nützlich):
    // @androidx.room.Insert(onConflict = OnConflictStrategy.REPLACE)
    // suspend fun insertItems(items: List<ShoppingListItem>)
}

