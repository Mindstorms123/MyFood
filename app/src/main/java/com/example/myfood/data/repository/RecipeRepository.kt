package com.example.myfood.data.repository

import android.util.Log
import com.example.myfood.data.local.RecipeDao
import com.example.myfood.data.model.Recipe
import com.example.myfood.data.parser.MarkdownRecipeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
// import kotlinx.coroutines.flow.map // Nicht direkt hier verwendet, aber oft nützlich
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val markdownRecipeParser: MarkdownRecipeParser
) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes() // Annahme: getAllRecipes() gibt Flow zurück

    // --- KORREKTUR/ANPASSUNG: Umbenannt für Klarheit, wenn eine suspend-Version existiert ---
    // Diese Methode gibt einen Flow zurück, für kontinuierliche Beobachtung.
    /*fun getRecipeByIdAsFlow(id: Long): Flow<Recipe?> {
        // Annahme: recipeDao.getRecipeByIdAsFlow(id) ist die DAO-Methode, die Flow<Recipe?> zurückgibt
        return recipeDao.getRecipeByIdAsFlow(id)
    }*/

    // --- KORREKTUR/ANPASSUNG: Suspend-Funktion für einmaligen Abruf eines Rezepts ohne Transformationen ---
    // Diese Methode gibt ein einzelnes Rezept direkt zurück.
    suspend fun getSingleRecipeById(id: Long): Recipe? {
        // Annahme: recipeDao.getRecipeById(id) ist die SUSPEND DAO-Methode, die Recipe? zurückgibt
        // Diese sollte innerhalb eines withContext(Dispatchers.IO) aufgerufen werden,
        // wenn die DAO-Methode selbst nicht schon auf einem IO-Dispatcher läuft,
        // aber Room's suspend-Funktionen tun das normalerweise.
        // Zur Sicherheit und expliziten Kontrolle:
        return withContext(Dispatchers.IO) {
            recipeDao.getRecipeById(id)
        }
    }


    // Variante B: Repository führt ggf. einfache Transformationen/Übersetzungen durch und gibt Recipe? zurück
    suspend fun getRecipeDetailsById(id: Long, translateToGerman: Boolean): Recipe? {
        // --- KORREKTUR: Expliziter Wechsel zum IO-Dispatcher für die blockierende .firstOrNull() Operation ---
        val localRecipe = withContext(Dispatchers.IO) {
            // Ruft die DAO-Methode auf, die einen Flow zurückgibt, und nimmt das erste Element.
            recipeDao.getRecipeByIdAsFlow(id).firstOrNull()
            // Alternativ, wenn du die suspend DAO-Methode hier direkt nutzen willst:
            // recipeDao.getRecipeById(id)
            // Dann wäre der withContext-Block hier optional, wenn die suspend DAO-Methode schon auf IO läuft.
            // Aber für die .firstOrNull() Operation auf einem Flow ist withContext(Dispatchers.IO) wichtig.
        }

        return localRecipe?.let { recipe ->
            if (translateToGerman) {
                // Die Übersetzungslogik selbst ist CPU-gebunden und muss nicht zwingend auf Dispatchers.IO laufen,
                // es sei denn, sie wäre extrem aufwendig. Für einfache String-Operationen ist das okay.
                // Wenn "translateToGerman" einen Netzwerkaufruf beinhalten würde, müsste dieser
                // ebenfalls in withContext(Dispatchers.IO) gekapselt werden.
                recipe.copy(
                    ingredients = recipe.ingredients.map { ingredient ->
                        ingredient.copy(
                            name = when (ingredient.name.lowercase()) {
                                "sugar" -> "Zucker"
                                "flour" -> "Mehl"
                                "egg" -> "Ei"
                                // ... mehr Mappings ...
                                else -> ingredient.name
                            }
                        )
                    }
                )
            } else {
                recipe
            }
        }
    }

    // --- Schreiboperationen sind bereits suspend und rufen suspend DAO-Methoden auf, das ist gut ---
    suspend fun insertRecipe(recipe: Recipe): Long { // Angenommen, DAO gibt die ID zurück
        // Room führt suspend DAO-Funktionen auf einem Hintergrundthread aus.
        return recipeDao.insertRecipe(recipe)
    }

    suspend fun updateRecipe(recipe: Recipe) {
        recipeDao.updateRecipe(recipe)
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        recipeDao.deleteRecipe(recipe)
    }

    suspend fun refreshRecipesFromMarkdownAssetsIfNeeded(assetSubFolder: String = "recipes_md") {
        // DAO-Aufruf `countRecipesBySource` gibt direkt Int zurück und ist nicht suspend,
        // aber die gesamte Funktion ist suspend. Das ist in Ordnung.
        // Falls countRecipesBySource suspend wäre, wäre es auch gut.
        val count = withContext(Dispatchers.IO) { // Sicherstellen, dass auch Zählung auf IO läuft
            recipeDao.countRecipesBySource("github")
        }
        if (count == 0) {
            Log.d("RecipeRepository", "No GitHub recipes in DB, starting import...")
            try {
                // recipesFromMarkdown wird korrekt auf Dispatchers.IO geholt
                val recipesFromMarkdown = withContext(Dispatchers.IO) {
                    markdownRecipeParser.parseRecipesFromAssets(assetSubFolder)
                }
                if (recipesFromMarkdown.isNotEmpty()) {
                    val recipesToInsert = recipesFromMarkdown.map { it.copy(source = "github") }
                    // insertAllRecipes ist eine suspend-Funktion des DAOs
                    recipeDao.insertAllRecipes(recipesToInsert)
                    Log.d("RecipeRepository", "Successfully inserted ${recipesToInsert.size} recipes from Markdown.")
                } else {
                    Log.w("RecipeRepository", "No recipes were parsed from Markdown assets.")
                }
            } catch (e: Exception) {
                Log.e("RecipeRepository", "Error importing recipes from Markdown", e)
            }
        } else {
            Log.d("RecipeRepository", "GitHub recipes already exist in DB. Skipping import.")
        }
    }
}

