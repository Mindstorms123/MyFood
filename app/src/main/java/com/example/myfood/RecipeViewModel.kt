package com.example.myfood.ui.recipe // Stelle sicher, dass der Paketname korrekt ist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfood.data.model.Recipe
import com.example.myfood.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Definiere den UI-State für den RecipeDetailScreen
sealed interface RecipeDetailUiState {
    object Loading : RecipeDetailUiState
    data class Success(val recipe: Recipe) : RecipeDetailUiState
    data class Error(val message: String) : RecipeDetailUiState
}

@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    // StateFlow für die Liste aller Rezepte
    val recipes: StateFlow<List<Recipe>> = recipeRepository.allRecipes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // UI-State für RecipeDetailScreen (jetzt als StateFlow)
    private val _recipeDetailUiState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.Loading)
    val recipeDetailUiState: StateFlow<RecipeDetailUiState> = _recipeDetailUiState.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    recipeRepository.refreshRecipesFromMarkdownAssetsIfNeeded()
                } catch (e: Exception) {
                    Log.e("RecipeViewModel", "Error refreshing recipes from Markdown", e)
                    // Optional: Einen Fehler-State für die Gesamtliste setzen, falls relevant
                }
            }
        }
    }

    // Funktion zum Laden der Rezeptdetails
    fun loadRecipeDetails(recipeIdString: String, translateToGerman: Boolean = false) {
        _recipeDetailUiState.value = RecipeDetailUiState.Loading
        viewModelScope.launch {
            try {
                val id = recipeIdString.toLongOrNull()
                if (id == null) {
                    _recipeDetailUiState.value = RecipeDetailUiState.Error("Ungültige Rezept-ID: '$recipeIdString'")
                    return@launch
                }

                val recipeFromRepo: Recipe? = withContext(Dispatchers.IO) {
                    recipeRepository.getRecipeDetailsById(id, translateToGerman)
                }

                if (recipeFromRepo != null) {
                    _recipeDetailUiState.value = RecipeDetailUiState.Success(recipeFromRepo)
                } else {
                    _recipeDetailUiState.value = RecipeDetailUiState.Error("Rezept mit ID $id nicht gefunden.")
                }
            } catch (e: Exception) {
                _recipeDetailUiState.value = RecipeDetailUiState.Error("Fehler beim Laden der Rezeptdetails: ${e.localizedMessage}")
                Log.e("RecipeViewModel", "Error loading recipe details for ID $recipeIdString", e)
            }
        }
    }

    // Funktion, um ein einzelnes Rezept per ID zu holen (als StateFlow)
    // Nützlich, wenn du reaktiv auf Änderungen eines einzelnen Rezepts hören möchtest.
    fun getRecipeByIdFlow(id: Long): StateFlow<Recipe?> {
        return recipeRepository.getRecipeByIdAsFlow(id)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = null
            )
    }

    // Suspend-Funktion für einmaligen Abruf eines Rezepts
    // Gut für einmalige Operationen, wie das Laden vor dem Bearbeiten oder Löschen.
    suspend fun getSingleRecipeById(id: Long): Recipe? {
        return withContext(Dispatchers.IO) {
            recipeRepository.getSingleRecipeById(id)
            // Alternativ, falls getSingleRecipeById nicht existiert:
            // recipeRepository.getRecipeByIdAsFlow(id).firstOrNull()
        }
    }


    // --- CRUD-Operationen (Create, Read, Update, Delete) ---

    fun addRecipe(recipe: Recipe, onComplete: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    recipeRepository.insertRecipe(recipe)
                }
                onComplete?.invoke(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Error adding recipe: ${recipe.title}", e)
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun updateRecipe(recipe: Recipe, onComplete: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    recipeRepository.updateRecipe(recipe)
                }
                // Optional: Den recipeDetailUiState aktualisieren, falls das bearbeitete Rezept angezeigt wird
                if (_recipeDetailUiState.value is RecipeDetailUiState.Success &&
                    (_recipeDetailUiState.value as RecipeDetailUiState.Success).recipe.id == recipe.id) {
                    _recipeDetailUiState.value = RecipeDetailUiState.Success(recipe)
                }
                onComplete?.invoke(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Error updating recipe: ${recipe.title}", e)
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    /**
     * Löscht ein Rezept anhand seines Recipe-Objekts.
     * Nützlich, wenn du das Objekt bereits hast.
     */
    fun deleteRecipe(recipe: Recipe, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    recipeRepository.deleteRecipe(recipe)
                }
                onDeleted?.invoke()
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Error deleting recipe: ${recipe.title}", e)
                // Setze einen Fehlerstatus, wenn das Löschen fehlschlägt und es das aktuell angezeigte Rezept ist
                if (_recipeDetailUiState.value is RecipeDetailUiState.Success &&
                    (_recipeDetailUiState.value as RecipeDetailUiState.Success).recipe.id == recipe.id) {
                    _recipeDetailUiState.value = RecipeDetailUiState.Error("Fehler beim Löschen des Rezepts: ${e.message}")
                }
            }
        }
    }

    /**
     * NEU: Löscht ein Rezept anhand seiner ID (als String, wie von der Navigation übergeben).
     * Diese Funktion ist für den Aufruf vom RecipeDetailScreen gedacht.
     */
    fun deleteRecipeById(recipeIdString: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val id = recipeIdString.toLongOrNull()
            if (id == null) {
                _recipeDetailUiState.value = RecipeDetailUiState.Error("Ungültige Rezept-ID zum Löschen: '$recipeIdString'")
                return@launch
            }

            try {
                // Hole zuerst das vollständige Recipe-Objekt, da deine `deleteRecipe` Funktion im Repository
                // wahrscheinlich das Objekt erwartet und nicht nur die ID.
                // Wenn dein Repository eine `deleteRecipeById(id: Long)` hat, kannst du das direkt verwenden.
                val recipeToDelete = withContext(Dispatchers.IO) {
                    recipeRepository.getSingleRecipeById(id) // Oder getRecipeByIdAsFlow(id).firstOrNull()
                }

                if (recipeToDelete != null) {
                    withContext(Dispatchers.IO) {
                        recipeRepository.deleteRecipe(recipeToDelete) // Rufe die bestehende deleteRecipe auf
                    }
                    Log.i("RecipeViewModel", "Recipe with ID $id deleted.")
                    onDeleted() // Callback für die Navigation
                } else {
                    Log.w("RecipeViewModel", "Recipe with ID $id not found for deletion.")
                    _recipeDetailUiState.value = RecipeDetailUiState.Error("Rezept zum Löschen nicht gefunden.")
                }
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Error deleting recipe with ID $id", e)
                _recipeDetailUiState.value = RecipeDetailUiState.Error("Fehler beim Löschen: ${e.message}")
            }
        }
    }


    /**
     * Speichert ein Rezept (fügt hinzu oder aktualisiert).
     * Der onComplete Callback kann für Navigation oder UI-Feedback genutzt werden.
     */
    fun saveRecipe(recipe: Recipe, onComplete: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (recipe.id == 0L) { // Annahme: 0L bedeutet neues Rezept
                        recipeRepository.insertRecipe(recipe)
                    } else {
                        recipeRepository.updateRecipe(recipe)
                    }
                }
                // Optional: Den recipeDetailUiState aktualisieren, falls das gespeicherte Rezept angezeigt wird
                if (recipe.id != 0L && _recipeDetailUiState.value is RecipeDetailUiState.Success &&
                    (_recipeDetailUiState.value as RecipeDetailUiState.Success).recipe.id == recipe.id) {
                    _recipeDetailUiState.value = RecipeDetailUiState.Success(recipe) // Zeige die aktualisierte Version
                }
                onComplete?.invoke(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Error saving recipe: ${recipe.title}", e)
                onComplete?.invoke(Result.failure(e))
                // Optional: Fehler im UI State setzen
                // _recipeDetailUiState.value = RecipeDetailUiState.Error("Fehler beim Speichern: ${e.message}")
            }
        }
    }

    // State für das Rezept, das im AddEditRecipeScreen bearbeitet wird
    // (Beachte: _recipeToEdit ist mutableStateOf, was für diesen Anwendungsfall oft okay ist,
    // da es typischerweise nur von einer Stelle (dem EditScreen) beobachtet und modifiziert wird.
    // Für komplexere Szenarien könnte auch hier ein StateFlow erwogen werden.)
    private val _recipeToEdit = MutableStateFlow<Recipe?>(null)
    val recipeToEdit: StateFlow<Recipe?> = _recipeToEdit.asStateFlow()


    fun loadRecipeForEditing(recipeId: Long?) {
        if (recipeId == null || recipeId == 0L) { // 0L als Indikator für "neues Rezept"
            _recipeToEdit.value = null // oder ein leeres Recipe-Objekt: Recipe(title="", ...)
            return
        }
        viewModelScope.launch {
            _recipeToEdit.value = withContext(Dispatchers.IO) {
                recipeRepository.getSingleRecipeById(recipeId)
                // Oder: recipeRepository.getRecipeByIdAsFlow(recipeId).firstOrNull()
            }
        }
    }

    /**
     * Setzt den Bearbeitungsstatus zurück, z.B. wenn der EditScreen verlassen wird.
     */
    fun clearRecipeForEditing() {
        _recipeToEdit.value = null
    }
}
