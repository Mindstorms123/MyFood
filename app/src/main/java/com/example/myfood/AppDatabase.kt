package com.example.myfood.data // Or your appropriate package

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myfood.data.shopping.ShoppingListDao
import com.example.myfood.data.shopping.ShoppingListItem

// --- KORREKTUR: Version erhöht ---
@Database(entities = [ShoppingListItem::class], version = 2, exportSchema = false) // Add other entities if you have them
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    // Add other DAOs here if you have them

    // --- OPTIONAL aber EMPFOHLEN für die Entwicklung, wenn du noch keine Migrationsstrategie hast ---
    // Wenn du deine Datenbankinstanz erstellst, füge .fallbackToDestructiveMigration() hinzu,
    // damit die App nicht abstürzt, wenn du das Schema änderst und nur die Version erhöhst.
    // Beispiel (an der Stelle, wo du die Datenbank baust, z.B. in einem Hilt Modul):
    //
    // @Provides
    // @Singleton
    // fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
    //     return Room.databaseBuilder(
    //         appContext,
    //         AppDatabase::class.java,
    //         "my_food_database" // Dein Datenbankname
    //     )
    //     .fallbackToDestructiveMigration() // WICHTIG: Löscht alte Daten bei Versionserhöhung ohne Migration
    //     .build()
    // }
    //
    // Wenn du exportSchema = true verwenden möchtest (empfohlen für später), stelle sicher, dass du
    // auch die Konfiguration in deinem build.gradle für room.schemaLocation setzt.
}
