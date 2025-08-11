package com.example.myfood.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
//import androidx.compose.runtime.snapshots.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.myfood.FoodItem
import com.example.myfood.data.model.Ingredient
import kotlin.math.max
import kotlin.math.min

// ---- Vergleichs-Zustand (sichtbare Compose-States) ----
class ComparisonItemState(
    val recipeIngredient: Ingredient,
    suggested: List<FoodItem> = emptyList(),
    selectedPantryItem: FoodItem? = null,
    userConfirmedEnough: Boolean = false,
    needsToBeAdded: Boolean = false
) {
    // mutable Vorschlagsliste, damit wir bei Bedarf nachträglich updaten können
    val suggestedPantryItems: SnapshotStateList<FoodItem> = mutableStateListOf<FoodItem>().apply { addAll(suggested) }

    var selectedPantryItem by mutableStateOf(selectedPantryItem)
    var userConfirmedEnoughInPantry by mutableStateOf(userConfirmedEnough)
    var needsToBeAddedToShoppingList by mutableStateOf(needsToBeAdded)

    // Wenn true, hat der Nutzer die Checkbox bewusst verändert — dann ändern wir die Checkbox nicht mehr automatisch.
    var userOverrodeNeedsToBeAdded by mutableStateOf(false)

    fun updateSuggestions(newSuggestions: List<FoodItem>, autoSelectSingle: Boolean = true) {
        suggestedPantryItems.clear()
        suggestedPantryItems.addAll(newSuggestions)
        // nur automatische Anpassung, wenn der Nutzer die Checkbox NICHT manuell verändert hat
        if (!userOverrodeNeedsToBeAdded) {
            needsToBeAddedToShoppingList = newSuggestions.isEmpty()
        }
        // falls genau 1 match und noch keine Auswahl, optional auto-select
        if (autoSelectSingle && newSuggestions.size == 1 && selectedPantryItem == null) {
            selectedPantryItem = newSuggestions.first()
            // wenn ausgewählt wurde, setzen wir needsToBeAdded false (auch wenn nicht vom Nutzer übersteuert)
            needsToBeAddedToShoppingList = false
        }
        // falls keine Vorschläge mehr, Auswahl löschen
        if (newSuggestions.isEmpty()) {
            selectedPantryItem = null
            userConfirmedEnoughInPantry = false
        }
    }
}

// Extension zur Anzeige der Zutat
fun Ingredient.getDisplayQuantityWithName(): String {
    val quantityText = this.quantity?.takeIf { it.isNotBlank() }
    val unitText = this.unit?.takeIf { it.isNotBlank() }
    val nameText = this.name ?: ""
    return buildString {
        quantityText?.let { append("$it ") }
        unitText?.let { append("$it ") }
        append(nameText)
    }.trim()
}

// ---- Hilfsfunktionen für Matching ----
private fun normalizeName(s: String?): String {
    if (s.isNullOrBlank()) return ""
    var t = s.lowercase()
    // Klammern-Inhalte entfernen (z.B. "Tomate (gehackt)")
    t = t.replace(Regex("\\(.*?\\)"), " ")
    // Zahlen entfernen
    t = t.replace(Regex("\\d+"), " ")
    // Einheiten & häufige Attribute entfernen
    t = t.replace(
        Regex("\\b(g|kg|ml|l|st|stück|cup|cups|tl|tsp|tbsp|esslöffel|teelöffel|el|fein|grob|gehackt|gewürfelt|frisch|getrocknet|in\\spulverform)\\b"),
        " "
    )
    // alles außer Buchstaben & Leerzeichen entfernen
    t = t.replace(Regex("[^\\p{L}\\s]"), " ")
    t = t.replace(Regex("\\s+"), " ").trim()
    // einfache Singularisierung: entferne gängige deutsche Endungen (heuristisch)
    val tokens = t.split(" ").map { token ->
        token
            .removeSuffix("en")
            .removeSuffix("er")
            .removeSuffix("n")
            .removeSuffix("e")
            .removeSuffix("s")
    }.filter { it.isNotBlank() }
    return tokens.joinToString(" ")
}

/** Levenshtein (iterative, O(n*m)), returns distance >= 0 */
private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val costs = IntArray(b.length + 1) { it }
    var i = 1
    while (i <= a.length) {
        var prevCost = i - 1
        costs[0] = i
        var j = 1
        while (j <= b.length) {
            val curCost = costs[j]
            val insertCost = costs[j - 1] + 1
            val deleteCost = costs[j] + 1
            val replaceCost = prevCost + if (a[i - 1] == b[j - 1]) 0 else 1
            val newCost = min(min(insertCost, deleteCost), replaceCost)
            prevCost = costs[j]
            costs[j] = newCost
            j++
        }
        i++
    }
    return costs[b.length]
}

/** Berechnet einen "score" (je kleiner desto besser) zwischen ingredient und pantryItem */
private fun matchScore(ingredient: String?, pantry: String?): Int {
    val i = normalizeName(ingredient)
    val p = normalizeName(pantry)
    if (i.isBlank() || p.isBlank()) return Int.MAX_VALUE / 4

    // wenn eine Seite komplett enthalten ist -> sehr guter Score
    if (p.contains(i) || i.contains(p)) return 0

    // token matching
    val itoks = i.split(" ").filter { it.length >= 2 }
    val ptoks = p.split(" ").filter { it.length >= 2 }

    var bestTokenDistance = Int.MAX_VALUE
    for (it in itoks) {
        for (pt in ptoks) {
            if (pt.contains(it) || it.contains(pt)) return 1
            val d = levenshtein(it, pt)
            if (d < bestTokenDistance) bestTokenDistance = d
        }
    }

    // distance on whole normalized strings (penalize longer distances)
    val wholeDist = levenshtein(i, p)
    // combine: prefer token match distance, otherwise wholeDist
    return min(bestTokenDistance + 2, wholeDist + 4)
}

/** Liefert sortierte Vorschläge (bestes Match zuerst) */
private fun computeSuggestionsSorted(ingredient: Ingredient, pantryItems: List<FoodItem>): List<FoodItem> {
    val scores = pantryItems.map { pantry ->
        val score = matchScore(ingredient.name, pantry.name)
        pantry to score
    }.filter { (_, score) -> score < Int.MAX_VALUE / 4 } // filter out totally unrelated
        .sortedBy { it.second }
        .map { it.first }
    return scores
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePantryComparisonDialog(
    recipeTitle: String,
    recipeIngredients: List<Ingredient>,
    pantryItems: List<FoodItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<Ingredient>) -> Unit
) {
    // Erzeuge observable Zustände für jedes Ingredient (wird neu gebaut, wenn recipeIngredients oder pantryItems wechseln)
    val comparisonStates = remember(recipeIngredients, pantryItems) {
        recipeIngredients.map { ingredient ->
            val suggestions = computeSuggestionsSorted(ingredient, pantryItems)
            val needsAdd = suggestions.isEmpty()
            val autoSelected = if (suggestions.size == 1) suggestions[0] else null
            ComparisonItemState(
                recipeIngredient = ingredient,
                suggested = suggestions,
                selectedPantryItem = autoSelected,
                userConfirmedEnough = false,
                needsToBeAdded = needsAdd
            )
        }.toMutableStateList()
    }

    // Wenn pantryItems/recipeIngredients sich nach Erzeugung ändern, dann die Vorschläge updaten.
    // (häufig nicht nötig, aber sichert Rekonsistenz)
    LaunchedEffect(pantryItems, recipeIngredients) {
        comparisonStates.forEach { state ->
            val newSuggestions = computeSuggestionsSorted(state.recipeIngredient, pantryItems)
            state.updateSuggestions(newSuggestions, autoSelectSingle = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "'$recipeTitle' mit Vorrat abgleichen",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        items = comparisonStates,
                        key = { state -> (state.recipeIngredient.name ?: "") + (state.recipeIngredient.quantity ?: "") }
                    ) { itemState ->
                        // Wichtig: wir zeigen die Vorschlags-/Dropdown-UI, wenn Vorschläge vorhanden sind,
                        // unabhängig davon, ob die Checkbox gesetzt ist — so erreichst du immer das Kontextmenü.
                        ComparisonItemRow(
                            itemState = itemState,
                            onPantryItemSelected = { pantryItem ->
                                // Benutzer hat bewusst eine Auswahl aus dem Vorrat getroffen -> setze entsprechend
                                itemState.selectedPantryItem = pantryItem
                                itemState.userOverrodeNeedsToBeAdded = true
                                itemState.needsToBeAddedToShoppingList = pantryItem == null
                                if (pantryItem == null) itemState.userConfirmedEnoughInPantry = false
                                else itemState.userConfirmedEnoughInPantry = false // bleibt false bis Nutzer bestätigt
                            },
                            onConfirmEnoughInPantryChange = { isEnough ->
                                itemState.userConfirmedEnoughInPantry = isEnough
                                itemState.userOverrodeNeedsToBeAdded = true
                                if (isEnough && itemState.selectedPantryItem != null) {
                                    itemState.needsToBeAddedToShoppingList = false
                                }
                            },
                            onAddToShoppingListChange = { shouldAdd ->
                                // Benutzer hat die Checkbox verändert -> merken und anwenden
                                itemState.userOverrodeNeedsToBeAdded = true
                                itemState.needsToBeAddedToShoppingList = shouldAdd
                                if (shouldAdd) {
                                    itemState.selectedPantryItem = null
                                    itemState.userConfirmedEnoughInPantry = false
                                } else {
                                    // wenn Nutzer die Checkbox entfernt und es genau 1 Vorschlag gibt, auto-select
                                    if (itemState.suggestedPantryItems.size == 1 && itemState.selectedPantryItem == null) {
                                        itemState.selectedPantryItem = itemState.suggestedPantryItems.first()
                                    }
                                }
                            }
                        )

                        val idx = comparisonStates.indexOf(itemState)
                        if (idx < comparisonStates.size - 1) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val itemsToAdd = comparisonStates
                            .filter { it.needsToBeAddedToShoppingList }
                            .map { it.recipeIngredient }
                        onConfirm(itemsToAdd)
                    }) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Hinzufügen")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonItemRow(
    itemState: ComparisonItemState,
    onPantryItemSelected: (FoodItem?) -> Unit,
    onConfirmEnoughInPantryChange: (Boolean) -> Unit,
    onAddToShoppingListChange: (Boolean) -> Unit
) {
    var expandedPantryDropdown by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = itemState.needsToBeAddedToShoppingList,
                onCheckedChange = { checked ->
                    onAddToShoppingListChange(checked)
                }
            )
            Text(
                text = itemState.recipeIngredient.getDisplayQuantityWithName(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }

        // Wir zeigen die Vorrats-UI, sobald Vorschläge vorhanden sind oder bereits eine Auswahl existiert.
        // So kann der Nutzer auch dann auf das Kontextmenü zugreifen, wenn die Checkbox (aus Versehen) gesetzt wurde.
        AnimatedVisibility(
            visible = itemState.suggestedPantryItems.isNotEmpty() || itemState.selectedPantryItem != null
        ) {
            Column(modifier = Modifier.padding(start = 32.dp, top = 8.dp, end = 16.dp)) {
                Text("Aus Vorrat:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))

                if (itemState.suggestedPantryItems.isNotEmpty() || itemState.selectedPantryItem != null) {
                    ExposedDropdownMenuBox(
                        expanded = expandedPantryDropdown,
                        onExpandedChange = { expandedPantryDropdown = !expandedPantryDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = itemState.selectedPantryItem?.let { "${it.name} (Vorrat: ${it.quantity})" }
                                ?: if (itemState.suggestedPantryItems.isNotEmpty()) "Wähle Vorratsitem..." else "Keine Vorschläge",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPantryDropdown) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPantryDropdown,
                            onDismissRequest = { expandedPantryDropdown = false }
                        ) {
                            itemState.suggestedPantryItems.forEach { pantryItem ->
                                DropdownMenuItem(
                                    text = { Text("${pantryItem.name} (Vorrat: ${pantryItem.quantity})") },
                                    onClick = {
                                        onPantryItemSelected(pantryItem)
                                        expandedPantryDropdown = false
                                    }
                                )
                            }
                            // Falls schon eine Auswahl existiert, Option zum Aufheben
                            if (itemState.selectedPantryItem != null) {
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Auswahl aufheben / Benötigt", color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        onPantryItemSelected(null)
                                        expandedPantryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text("Keine passenden Items im Vorrat gefunden.", style = MaterialTheme.typography.bodySmall)
                }

                AnimatedVisibility(visible = itemState.selectedPantryItem != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable(onClick = { onConfirmEnoughInPantryChange(!itemState.userConfirmedEnoughInPantry) })
                            .fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = itemState.userConfirmedEnoughInPantry,
                            onCheckedChange = onConfirmEnoughInPantryChange
                        )
                        Text("Ausreichend von diesem Vorratsitem vorhanden?", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
