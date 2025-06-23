package com.example.myfood.di // Or your chosen package for DI modules

import android.content.Context
import androidx.room.Room
import com.example.myfood.data.AppDatabase
import com.example.myfood.data.shopping.ShoppingListDao
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
        ).build()
    }

    @Provides
    @Singleton
    fun provideShoppingListDao(appDatabase: AppDatabase): ShoppingListDao {
        return appDatabase.shoppingListDao()
    }
}