package com.example.myfood.di

import com.example.myfood.data.pantry.DataStorePantryRepository // For the implementation
import com.example.myfood.data.pantry.PantryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPantryRepository(
        dataStorePantryRepository: DataStorePantryRepository
    ): PantryRepository // Error would be here if PantryRepository is unresolved
}