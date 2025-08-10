package com.example.myfood.di // Or your chosen package for DI modules

import android.content.Context
import androidx.room.Room
import com.example.myfood.data.AppDatabase
import com.example.myfood.data.shopping.ShoppingListDao
import com.example.myfood.data.local.RecipeDao
// Importiere hier andere DAOs, falls deine AppDatabase mehr als nur das ShoppingListDao hat
// import com.example.myfood.data.pantry.PantryDao // Beispiel, falls du es verwendest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "my_food_database" // Name your database file
        )
            .fallbackToDestructiveMigration() // <<< HIER IST DIE KORREKTUR/ERGÄNZUNG
            .build()
    }

    @Provides
    // Die @Singleton Annotation ist hier nicht unbedingt nötig, da AppDatabase bereits ein Singleton ist
    // und Hilt den DAO immer von derselben Datenbankinstanz holt.
    // Aber es schadet auch nicht.
    fun provideShoppingListDao(appDatabase: AppDatabase): ShoppingListDao {
        return appDatabase.shoppingListDao()
    }

    // Wenn du andere DAOs in deiner AppDatabase definiert hast, füge hier entsprechende @Provides Methoden hinzu:
    /*
    @Provides
    fun provideMyOtherDao(appDatabase: AppDatabase): MyOtherDao {
        return appDatabase.myOtherDao()
    }
    */

    @Provides
    fun provideRecipeDao(appDatabase: AppDatabase): RecipeDao {
        return appDatabase.recipeDao()
    }
}
