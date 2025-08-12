package com.example.myfood.di

import com.example.myfood.data.pantry.DataStorePantryRepository
import com.example.myfood.data.pantry.PantryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Da DataStorePantryRepository @Singleton ist
abstract class RepositoryModule {

    @Binds
    @Singleton // Muss mit dem Scope von DataStorePantryRepository übereinstimmen
    abstract fun bindPantryRepository(
        dataStorePantryRepository: DataStorePantryRepository // Parameter ist die konkrete Implementierung
    ): PantryRepository // Rückgabetyp ist das Interface
}