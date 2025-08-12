package com.example.myfood.data.local // Passe den Paketnamen an

import androidx.room.*
import com.example.myfood.data.model.Recipe // Passe den Import an
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long // Gibt die ID des eingefügten Rezepts zurück

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRecipes(recipes: List<Recipe>)

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    @Query("SELECT * FROM recipes ORDER BY title ASC")
    fun getAllRecipes(): Flow<List<Recipe>> // Flow für reaktive Updates

    // Behalte diese Version für Beobachtungen, falls benötigt
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    fun getRecipeByIdAsFlow(recipeId: Long): Flow<Recipe?>

    // --- KORREKTUR/ERGÄNZUNG: suspend-Funktion für einmaligen Abruf ---
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipeById(recipeId: Long): Recipe? // Gibt direkt Recipe? zurück

    @Query("SELECT * FROM recipes WHERE source = :source ORDER BY title ASC")
    fun getRecipesBySource(source: String): Flow<List<Recipe>>

    @Query("SELECT COUNT(*) FROM recipes WHERE source = 'github'")
    suspend fun countGithubRecipes(): Int // Um zu prüfen, ob der Import schon lief

    @Query("SELECT COUNT(*) FROM recipes WHERE source = :sourceName")
    fun countRecipesBySource(sourceName: String): Int // Muss Int zurückgeben, nicht Flow<Int>
    // da refreshRecipes... suspend ist und direkt den Wert braucht.
    // Wenn du es als Flow brauchst, dann Flow<Int> und im Repo .first()

    // Die auskommentierte Version ist redundant, wenn countRecipesBySource existiert.
    // @Query("SELECT COUNT(*) FROM recipes WHERE source = 'github'")
    // suspend fun countGithubRecipesAlso(): Int // Beispiel für eine spezifischere Zählung, falls die obere anders genutzt wird
}

