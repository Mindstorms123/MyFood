package com.example.myfood.data.parser

import android.content.Context
import android.util.Log // android.text.Html wird nicht mehr direkt hier benötigt für Pfade
import com.example.myfood.data.model.Ingredient
import com.example.myfood.data.model.Recipe
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
import javax.inject.Inject

class MarkdownRecipeParser @Inject constructor(@ApplicationContext private val context: Context) {

    private fun listRecipeMarkdownFiles(assetSubFolder: String): List<String> {
        return try {
            val files = context.assets.list(assetSubFolder)
            if (files == null) {
                Log.w("MarkdownRecipeParser", "Folder assets/$assetSubFolder not found or empty.")
                return emptyList()
            }
            val mdFiles = files.filter { it.endsWith(".md", ignoreCase = true) }
            Log.d("MarkdownRecipeParser", "Found ${mdFiles.size} MD files in assets/$assetSubFolder: ${mdFiles.joinToString()}")
            mdFiles
        } catch (e: Exception) {
            Log.e("MarkdownRecipeParser", "Error listing asset files in $assetSubFolder", e)
            emptyList()
        }
    }

    suspend fun parseRecipesFromAssets(assetSubFolder: String = "recipes_md"): List<Recipe> {
        val recipes = mutableListOf<Recipe>()
        val markdownFileNames = listRecipeMarkdownFiles(assetSubFolder)

        if (markdownFileNames.isEmpty()) {
            Log.w("MarkdownRecipeParser", "No markdown files found in assets/$assetSubFolder to parse.")
            return emptyList()
        }

        for (fileName in markdownFileNames) {
            Log.d("MarkdownRecipeParser", "Attempting to parse MD file: $fileName (from folder $assetSubFolder)")
            try {
                // Die MD-Dateien werden aus dem assetSubFolder gelesen
                context.assets.open("$assetSubFolder/$fileName").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val rawContent = reader.readText()
                        // Wichtig: assetBaseFolder hier ist der Ordner der MD-Dateien,
                        // nicht unbedingt der Ordner der Bilder, wenn diese global liegen.
                        parseStrictMarkdownToRecipe(rawContent, fileName, assetSubFolder)?.let {
                            recipes.add(it)
                            Log.i("MarkdownRecipeParser", "Successfully parsed and added recipe: ${it.title} from $fileName")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MarkdownRecipeParser", "Error reading or parsing file: $assetSubFolder/$fileName", e)
            }
        }
        Log.i("MarkdownRecipeParser", "Finished parsing. Total recipes parsed: ${recipes.size} out of ${markdownFileNames.size} files.")
        return recipes
    }

    private fun parseStrictMarkdownToRecipe(markdownContent: String, originalFileName: String, mdFileAssetFolder: String): Recipe? {
        // mdFileAssetFolder ist der Ordner, in dem die .md Datei lag, z.B. "recipes_md"
        // Die Bilder liegen aber global in "assets/pics/"

        val allLines = markdownContent.lines()
        val frontMatterLines = mutableListOf<String>()
        var contentLinesAfterFrontMatterSource: MutableList<String>

        var inFrontMatter = false
        var frontMatterClosed = false
        var frontMatterEndMarker: String? = null
        var frontMatterEndIndex = -1

        for ((index, line) in allLines.withIndex()) {
            val trimmedLine = line.trim()
            if (index == 0 && (trimmedLine == "---" || trimmedLine == "<!--")) {
                inFrontMatter = true
                frontMatterEndMarker = if (trimmedLine == "---") "---" else "-->"
                continue
            }

            if (inFrontMatter && !frontMatterClosed) {
                if (trimmedLine == frontMatterEndMarker) {
                    frontMatterClosed = true
                    frontMatterEndIndex = index
                } else {
                    frontMatterLines.add(line)
                }
            }
        }

        if (inFrontMatter && !frontMatterClosed) {
            Log.e("StrictParser", "$originalFileName: Front Matter started but not properly closed with '$frontMatterEndMarker'.")
            return null
        }

        val startIndexForContent = if (inFrontMatter && frontMatterClosed) frontMatterEndIndex + 1 else 0
        if (startIndexForContent <= allLines.size) {
            contentLinesAfterFrontMatterSource = allLines.subList(startIndexForContent, allLines.size).toMutableList()
        } else {
            contentLinesAfterFrontMatterSource = mutableListOf()
            Log.w("StrictParser", "$originalFileName: startIndexForContent ($startIndexForContent) for content was beyond allLines size (${allLines.size}). No content lines after FM.")
        }

        val consumableFrontMatterLines = frontMatterLines.toMutableList()
        var owner = "unknown"
        var recipeNameFromFrontMatter: String? = null
        val tagsList = mutableListOf<String>()

        if (inFrontMatter && frontMatterClosed) {
            if (consumableFrontMatterLines.isEmpty() || !consumableFrontMatterLines.first().trim().startsWith("owner:", ignoreCase = true)) {
                Log.e("StrictParser", "$originalFileName: Missing 'owner:' field in Front Matter. First FM line: '${consumableFrontMatterLines.firstOrNull()}'")
                return null
            }
            val ownerLine = consumableFrontMatterLines.removeAt(0).trim()
            owner = ownerLine.substringAfter("owner:").trim()
            if (owner.isEmpty() || owner.contains(Regex("[^A-Za-z0-9._ -]"))) {
                Log.e("StrictParser", "$originalFileName: Invalid characters or empty owner: '$owner'")
                return null
            }
            Log.d("StrictParser", "$originalFileName: Owner: '$owner'")

            if (consumableFrontMatterLines.isNotEmpty() && consumableFrontMatterLines.first().trim().startsWith("name:", ignoreCase = true)) {
                val nameLine = consumableFrontMatterLines.removeAt(0).trim()
                recipeNameFromFrontMatter = nameLine.substringAfter("name:").trim()
                Log.d("StrictParser", "$originalFileName: Name from Front Matter: '$recipeNameFromFrontMatter'")
            }

            if (consumableFrontMatterLines.isEmpty() || !consumableFrontMatterLines.first().trim().lowercase().startsWith("tags:")) {
                Log.e("StrictParser", "$originalFileName: Missing 'tags:' field or malformed in Front Matter. Expected 'tags:'. Got: '${consumableFrontMatterLines.firstOrNull()}'")
                return null
            }
            consumableFrontMatterLines.removeAt(0) // Consume 'tags:'

            while (consumableFrontMatterLines.isNotEmpty()) {
                val currentLineTrimmed = consumableFrontMatterLines.first().trim()
                if (!currentLineTrimmed.startsWith("- ")) {
                    Log.e("StrictParser", "$originalFileName: Invalid tag format. Expected '- Tag'. Got: '$currentLineTrimmed'")
                    return null // Strict: Tags müssen korrekt sein
                }
                val tag = currentLineTrimmed.removePrefix("- ").trim()
                if (tag.isEmpty() || tag.contains(Regex("[^A-Za-z0-9_ \\-äöüÄÖÜß]"))) {
                    Log.e("StrictParser", "$originalFileName: Invalid characters or empty tag: '$tag'")
                    return null // Strict: Tags müssen valide sein
                }
                tagsList.add(tag)
                consumableFrontMatterLines.removeAt(0)
            }
            Log.d("StrictParser", "$originalFileName: Tags from Front Matter: ${tagsList.joinToString()}")
        } else if (!inFrontMatter) {
            Log.d("StrictParser", "$originalFileName: No Front Matter block found. Owner will be default ('$owner'), no tags from FM.")
        }

        var title: String
        var titleLinesConsumed = 0

        if (!recipeNameFromFrontMatter.isNullOrBlank()) {
            title = recipeNameFromFrontMatter
            Log.d("StrictParser", "$originalFileName: Title from Front Matter 'name': '$title'")
        } else if (contentLinesAfterFrontMatterSource.isNotEmpty()) {
            var firstNonBlankIndex = 0
            while (firstNonBlankIndex < contentLinesAfterFrontMatterSource.size && contentLinesAfterFrontMatterSource[firstNonBlankIndex].isBlank()) {
                firstNonBlankIndex++
            }

            if (firstNonBlankIndex < contentLinesAfterFrontMatterSource.size) {
                val firstRealContentLine = contentLinesAfterFrontMatterSource[firstNonBlankIndex].trim()
                if (firstRealContentLine.startsWith("# ")) {
                    title = firstRealContentLine.removePrefix("# ").trim()
                    titleLinesConsumed = firstNonBlankIndex + 1
                } else if (firstNonBlankIndex + 1 < contentLinesAfterFrontMatterSource.size &&
                    contentLinesAfterFrontMatterSource[firstNonBlankIndex + 1].trim().all { it == '=' } &&
                    contentLinesAfterFrontMatterSource[firstNonBlankIndex + 1].trim().isNotEmpty() &&
                    firstRealContentLine.isNotBlank()) {
                    title = firstRealContentLine
                    titleLinesConsumed = firstNonBlankIndex + 2
                } else if (firstRealContentLine.isNotBlank()) {
                    title = firstRealContentLine // Fallback to first non-blank content line
                    titleLinesConsumed = firstNonBlankIndex + 1
                } else { // All content lines were blank
                    title = originalFileName.removeSuffix(".md").replace("_", " ").replaceFirstChar { it.titlecase() }
                    titleLinesConsumed = contentLinesAfterFrontMatterSource.size // All "consumed" as blank
                }
            } else { // No non-blank content lines found
                title = originalFileName.removeSuffix(".md").replace("_", " ").replaceFirstChar { it.titlecase() }
                titleLinesConsumed = contentLinesAfterFrontMatterSource.size // All "consumed" as blank
            }
        } else { // No frontmatter name and no content lines at all
            title = originalFileName.removeSuffix(".md").replace("_", " ").replaceFirstChar { it.titlecase() }
        }
        title = title.trim()

        if (title.isBlank()) {
            Log.e("StrictParser", "$originalFileName: Title could not be determined or is blank. This is a failure.")
            return null
        }
        Log.i("StrictParser", "$originalFileName: Determined Title: '$title'. Raw title lines consumed: $titleLinesConsumed")

        val linesForSectionParsing: MutableList<String>
        if (titleLinesConsumed > 0 && contentLinesAfterFrontMatterSource.size >= titleLinesConsumed) {
            linesForSectionParsing = contentLinesAfterFrontMatterSource.subList(titleLinesConsumed, contentLinesAfterFrontMatterSource.size).toMutableList()
        } else if (titleLinesConsumed == 0 && !recipeNameFromFrontMatter.isNullOrBlank()) { // Title from FM, use all content
            linesForSectionParsing = contentLinesAfterFrontMatterSource.toMutableList()
        } else { // No title lines consumed from content (e.g. title from filename because content was empty/blank) or content smaller
            linesForSectionParsing = if (contentLinesAfterFrontMatterSource.size > titleLinesConsumed) {
                contentLinesAfterFrontMatterSource.subList(titleLinesConsumed, contentLinesAfterFrontMatterSource.size).toMutableList()
            } else {
                mutableListOf()
            }
        }

        while (linesForSectionParsing.isNotEmpty() && linesForSectionParsing.first().isBlank()) {
            linesForSectionParsing.removeAt(0)
        }
        while (linesForSectionParsing.isNotEmpty() && linesForSectionParsing.last().isBlank()) {
            linesForSectionParsing.removeAt(linesForSectionParsing.size - 1)
        }
        Log.d("StrictParser", "$originalFileName: Lines for section parsing after title removal and blank trim: ${linesForSectionParsing.size}")

        // --- 3. Image Path ---
        // Bilder liegen in "assets/pics/"
        // originalFileName ist z.B. "russischer_zupfkuchen.md"
        // mdFileAssetFolder ist der Ordner, wo die .md Datei liegt, z.B. "recipes_md" - wird hier nicht für den Bildpfad benötigt.

        val baseFileNameWithoutExt = originalFileName.removeSuffix(".md")
        var imagePathForRecipeObject: String? = null // Dieser Pfad wird im Recipe Objekt gespeichert
        val commonImageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp")

        Log.i("StrictParser", "$originalFileName: --- Starting Image Search (Convention) ---")
        Log.d("StrictParser", "$originalFileName: (MD file was in '$mdFileAssetFolder') baseFileNameWithoutExt='$baseFileNameWithoutExt'")
        Log.d("StrictParser", "$originalFileName: Image files are expected directly in 'assets/pics/'")

        for (ext in commonImageExtensions) {
            // Der Pfad zum Bild ist immer relativ zum Root von 'assets'
            // und lautet "pics/rezeptname.ext"
            val imagePathRelativeToAssetsRoot = "pics/$baseFileNameWithoutExt.$ext" // z.B. "pics/russischer_zupfkuchen.jpg"

            Log.d("StrictParser", "$originalFileName: Trying convention: '$imagePathRelativeToAssetsRoot' (ext: .$ext)")
            try {
                // context.assets.open() benötigt den Pfad relativ zum Root von 'assets'
                context.assets.open(imagePathRelativeToAssetsRoot).use { /* Auto-closed */ }
                Log.i("StrictParser", "$originalFileName: SUCCESS - Found image by convention: '$imagePathRelativeToAssetsRoot'")
                imagePathForRecipeObject = imagePathRelativeToAssetsRoot // Speichere z.B. "pics/russischer_zupfkuchen.jpg"
                break // Bild gefunden, Schleife verlassen
            } catch (e: java.io.FileNotFoundException) {
                Log.d("StrictParser", "$originalFileName: FAILED - Not found by convention: '$imagePathRelativeToAssetsRoot'")
                // Versuche die nächste Extension
            } catch (e: Exception) {
                Log.w("StrictParser", "$originalFileName: ERROR checking image by convention '$imagePathRelativeToAssetsRoot': ${e.message}")
                // Bei anderem Fehler als FileNotFound, eventuell hier schon abbrechen oder nur loggen
            }
        }

        if (imagePathForRecipeObject == null) {
            Log.w("StrictParser", "$originalFileName: --- Image Search (Convention) FINISHED: NO IMAGE FOUND --- for $baseFileNameWithoutExt using extensions (${commonImageExtensions.joinToString()}). Searched in 'assets/pics/'")
        } else {
            Log.i("StrictParser", "$originalFileName: --- Image Search (Convention) FINISHED: IMAGE FOUND: $imagePathForRecipeObject ---")
        }

        // --- 4. Ingredients and Instructions Parsing ---
        val ingredientsList = mutableListOf<Ingredient>()
        val instructionsList = mutableListOf<String>()
        var currentSection: String? = null
        var isParsingIngredientsSubItems = false

        for (lineIndex in linesForSectionParsing.indices) {
            val lineContent = linesForSectionParsing[lineIndex]
            val line = lineContent.trim()

            if (line.isBlank()) {
                continue
            }

            var isSectionHeaderLine = false

            if (line.startsWith("## ")) {
                val headerText = line.removePrefix("## ").trim().removeSuffix(":").trim()
                isSectionHeaderLine = true
                when {
                    headerText.equals("Zutaten", ignoreCase = true) -> {
                        currentSection = "ZUTATEN"; isParsingIngredientsSubItems = true
                    }
                    headerText.equals("Zubereitung", ignoreCase = true) || headerText.equals("Anleitung", ignoreCase = true) -> {
                        currentSection = "ZUBEREITUNG"; isParsingIngredientsSubItems = false
                    }
                    else -> {
                        isParsingIngredientsSubItems = false; currentSection = null
                    }
                }
            } else if (lineIndex + 1 < linesForSectionParsing.size) {
                val nextLineTrimmed = linesForSectionParsing[lineIndex + 1].trim()
                if (line.isNotBlank() && nextLineTrimmed.isNotEmpty() && (nextLineTrimmed.all { it == '-' } || nextLineTrimmed.all { it == '=' }) && nextLineTrimmed.length >= 3) {
                    val headerText = line.removeSuffix(":").trim()
                    isSectionHeaderLine = true
                    when {
                        headerText.equals("Zutaten", ignoreCase = true) -> {
                            currentSection = "ZUTATEN"; isParsingIngredientsSubItems = true
                        }
                        headerText.equals("Zubereitung", ignoreCase = true) || headerText.equals("Anleitung", ignoreCase = true) -> {
                            currentSection = "ZUBEREITUNG"; isParsingIngredientsSubItems = false
                        }
                        else -> {
                            isParsingIngredientsSubItems = false; currentSection = null
                        }
                    }
                }
            }

            if (isSectionHeaderLine) {
                Log.d("StrictParser", "$originalFileName: Header '$line', section: $currentSection")
                continue
            }

            if (line.isNotEmpty() && (line.all { it == '=' } || line.all { it == '-' }) && line.length >= 3) {
                if (lineIndex > 0) {
                    val prevLineTrimmed = linesForSectionParsing[lineIndex - 1].trim().removeSuffix(":")
                    if (prevLineTrimmed.equals("Zutaten", ignoreCase = true) ||
                        prevLineTrimmed.equals("Zubereitung", ignoreCase = true) ||
                        prevLineTrimmed.equals("Anleitung", ignoreCase = true)) {
                        Log.d("StrictParser", "$originalFileName: Skipping Setext underline: '$line'")
                        continue
                    }
                }
                Log.d("StrictParser", "$originalFileName: Skipping thematic break: '$line'")
                continue
            }

            if (line.startsWith("### ")) {
                Log.d("StrictParser", "$originalFileName: H3 found: '$line'. Skipping.")
                if (currentSection == "ZUTATEN") isParsingIngredientsSubItems = true
                continue
            }

            when (currentSection) {
                "ZUTATEN" -> {
                    if (isParsingIngredientsSubItems && (line.startsWith("• ") || line.startsWith("* ") || line.startsWith("- "))) {
                        val cleanedLine = line.substringAfter(" ").trim()
                        if (cleanedLine.isNotEmpty()) {
                            parseIngredientLine(cleanedLine)?.let { ingredientsList.add(it) }
                                ?: ingredientsList.add(Ingredient(name = cleanedLine)) // Fallback
                        }
                    } else if (isParsingIngredientsSubItems && line.isNotBlank()) {
                        Log.d("StrictParser", "$originalFileName: Non-bullet/non-header line in ZUTATEN ('$line') while isParsingIngredientsSubItems=true. Ignored.")
                    }
                }
                "ZUBEREITUNG" -> {
                    if (line.isNotBlank()) {
                        val instructionTextToAdd = when {
                            line.matches(Regex("^\\d+[.)]\\s+.*")) -> line.replaceFirst(Regex("^\\d+[.)]\\s*"), "").trim()
                            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> line.substringAfter(" ").trim()
                            else -> line
                        }
                        if (instructionTextToAdd.isNotEmpty()) {
                            if (instructionsList.isNotEmpty() &&
                                line.firstOrNull()?.isLowerCase() == true &&
                                instructionsList.last().length < 250 &&
                                !instructionsList.last().endsWith(".")) {
                                instructionsList[instructionsList.size - 1] = instructionsList.last() + "\n" + instructionTextToAdd
                            } else {
                                instructionsList.add(instructionTextToAdd)
                            }
                        }
                    }
                }
                else -> {
                    if (line.isNotBlank()) {
                        Log.d("StrictParser", "$originalFileName: Line outside known section: '$line'. Current section: $currentSection. Ignoring.")
                    }
                }
            }
        }

        Log.i("StrictParser", "$originalFileName: Final Counts - Ingredients: ${ingredientsList.size}, Instructions: ${instructionsList.size}")

        if (title.isNotBlank() && ingredientsList.isEmpty() && instructionsList.isEmpty()) {
            Log.w("StrictParser", "$originalFileName: No ingredients AND no instructions found for recipe '$title'. This recipe will be skipped.")
            return null
        }

        return Recipe(
            title = title,
            ingredients = ingredientsList,
            instructions = instructionsList.map { it.trim() }.filter { it.isNotEmpty() },
            imagePath = imagePathForRecipeObject, // Hier den korrigierten Bildpfad verwenden
            category = tagsList.firstOrNull(),
            source = "github/$owner",
            owner = owner,
            tags = tagsList.ifEmpty { null }
        )
    }

    private fun parseIngredientLine(originalLine: String): Ingredient? {
        var line = originalLine.trim()
        if (line.isEmpty()) return null

        var quantity: String? = null
        var unit: String? = null
        var name: String

        val quantityRegex = Regex(
            """^(?:ca\.|circa|etwa|approx\.)?\s*(\d+(?:[.,]\d+)?(?:\s*-\s*\d+(?:[.,]\d+)?)?|\d+\s*/\s*\d+|\d+)\s*(.*)""",
            RegexOption.IGNORE_CASE
        )
        val quantityMatch = quantityRegex.find(line)

        if (quantityMatch != null) {
            quantity = quantityMatch.groupValues[1]
                .trim()
                .replace(",", ".")
                .replace(Regex("""\s*(?:-|bis)\s*"""), "-")
                .replace(Regex("""\s*/\s*"""), "/")
            line = quantityMatch.groupValues[2].trim()
        }

        val knownUnitsEntries = listOf(
            "Päckchen", "Pkg.", "Pckg.", "Packung", "pkg", "pck",
            "Esslöffel", "EL", "el", "tbsp", "tablespoon",
            "Teelöffel", "TL", "tl", "tsp", "teaspoon",
            "Messerspitze", "Msp.", "msp", "pinch",
            "Stück", "Stk.", "St.", "stk", "st", "piece", "pieces", "pc", "pcs",
            "Knolle", "Knollen", "bulb", "bulbs",
            "Zehe", "Zehen", "clove", "cloves",
            "Scheibe", "Scheiben", "slice", "slices",
            "Dose", "Dosen", "Ds.", "ds", "can", "cans",
            "Flasche", "Flaschen", "Fl.", "fl", "bottle", "bottles",
            "Glas", "Gläser", "glass", "glasses",
            "Becher", "cup", "cups",
            "Tasse", "Tassen",
            "Bund", "Bd.", "bd", "bunch", "bunches",
            "Prise", "Prisen",
            "Kilogramm", "kg", "kilogram", "kilograms",
            "Gramm", "g", "gr", "gram", "grams",
            "Pfund", "lb", "lbs", "pound", "pounds",
            "Unze", "oz", "ounce", "ounces",
            "Liter", "l", "L", "liter", "liters", "litre", "litres",
            "Milliliter", "ml", "mL", "milliliter", "milliliters", "millilitre", "millilitres",
            "Centiliter", "cl", "cL", "centiliter", "centiliters", "centilitre", "centilitres",
            "Deziliter", "dl", "dL", "deciliter", "deciliters", "decilitre", "decilitres",
            "Blatt", "Blätter", "sheet", "sheets",
            "Zweig", "Zweige", "sprig", "sprigs",
            "Handvoll", "handful",
            "Schuss", "dash", "splash"
        )
        val sortedKnownUnits = knownUnitsEntries.sortedByDescending { it.length }
        val unitPattern = sortedKnownUnits.joinToString("|") { Pattern.quote(it) }
        // Regex angepasst: Erlaube optionale Punkte nach der Einheit und stelle sicher, dass danach Leerraum oder Zeilenende kommt.
        val unitRegex = Regex("""^($unitPattern)(?:\.\s*|\s+|$)""", RegexOption.IGNORE_CASE)
        val unitMatch = unitRegex.find(line)

        if (unitMatch != null) {
            val matchedUnitValue = unitMatch.groupValues[1]
            // Finde die "kanonische" Form der Einheit, falls mehrere Varianten existieren (optional)
            unit = sortedKnownUnits.firstOrNull { it.equals(matchedUnitValue, ignoreCase = true) } ?: matchedUnitValue
            line = line.substring(unitMatch.range.last).trimStart('.', ' ').trim() // Entferne Einheit und folgende Leerzeichen/Punkte
        }

        // Entferne optionale Kommentare am Ende wie "(optional)" oder ", fein gehackt"
        name = line.replace(Regex("""\s*\([^)]*\)$"""), "").trim()
        // Entferne abschließende Satzzeichen, die nicht Teil des Namens sind
        name = name.removeSuffix(",").removeSuffix(":").trim()

        // Fallback, wenn Name leer ist, aber Originalzeile nicht (und keine Menge/Einheit gefunden wurde)
        val finalName = if (name.isNotEmpty()) {
            name
        } else if (originalLine.isNotEmpty() && quantity == null && unit == null) {
            originalLine.trim().removeSuffix(",").removeSuffix(":").trim()
        } else {
            name // Bleibt leer, wenn keine anderen Bedingungen zutreffen
        }

        if (finalName.isEmpty() && quantity == null && unit == null) {
            Log.w("IngredientParse", "Completely empty ingredient after parsing. Original: '$originalLine'. Skipping.")
            return null
        }
        // Wichtig: Eine Zutat ohne Namen ist meist ungültig, auch wenn Menge/Einheit da sind.
        if (finalName.isEmpty() && (quantity != null || unit != null)) {
            Log.w("IngredientParse", "Ingredient name is empty, but quantity/unit found. Q:'$quantity', U:'$unit'. Original: '$originalLine'. Skipping as invalid.")
            return null
        }

        return Ingredient(name = finalName, quantity = quantity, unit = unit)
    }
}
