package com.example.myfood.data.recipe

// Importiere ALLES Notwendige aus RecipeData.kt
import com.example.myfood.data.recipe.MealDBRecipe // WICHTIG
import com.example.myfood.data.recipe.MealDBResponse // WICHTIG (die Version aus RecipeData.kt)
import com.example.myfood.data.recipe.RecipeDetail // WICHTIG
import com.example.myfood.data.recipe.RecipeSummary // WICHTIG
// Ingredient und IngredientPresentation werden indirekt über die oberen Klassen verwendet

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable // Bleibt für lokale Datenklassen wie LibreTranslateResponse
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

// Basis-URL für TheMealDB API
private const val BASE_URL_THEMEALDB = "https://www.themealdb.com/api/json/v1/1/"

// --- Statische lokale Übersetzungsmap ---
val GERMAN_TO_ENGLISH_INGREDIENT_MAP: Map<String, String> = mapOf(
    "mehl" to "flour",
    "weizenmehl" to "wheat flour",
    "roggenmehl" to "rye flour",
    "dinkelmehl" to "spelt flour",
    "vollkornmehl" to "whole wheat flour",
    "speisestärke" to "cornstarch", // oder "potato starch" je nach Kontext
    "stärke" to "starch",
    "zucker" to "sugar",
    "puderzucker" to "powdered sugar", // oder "icing sugar"
    "brauner zucker" to "brown sugar",
    "vanillezucker" to "vanilla sugar",
    "hagelzucker" to "pearl sugar",
    "eier" to "eggs",
    "ei" to "egg",
    "eigelb" to "egg yolk",
    "eiweiß" to "egg white",
    "milch" to "milk",
    "vollmilch" to "whole milk",
    "fettarme milch" to "low-fat milk",
    "sahne" to "cream",
    "schlagsahne" to "whipping cream",
    "saure sahne" to "sour cream",
    "schmand" to "sour cream", // oft synonym verwendet
    "creme fraiche" to "creme fraiche",
    "joghurt" to "yogurt",
    "naturjoghurt" to "plain yogurt",
    "quark" to "quark", // Manchmal auch "curd cheese" oder "farmer's cheese"
    "butter" to "butter",
    "margarine" to "margarine",
    "pflanzenöl" to "vegetable oil",
    "öl" to "oil",
    "olivenöl" to "olive oil",
    "sonnenblumenöl" to "sunflower oil",
    "rapsöl" to "rapeseed oil", // oder "canola oil"
    "kokosöl" to "coconut oil",
    "paniermehl" to "breadcrumbs",
    "semmelbrösel" to "breadcrumbs",
    "panko" to "panko breadcrumbs",

    // Gemüse
    "kartoffel" to "potato",
    "kartoffeln" to "potatoes",
    "süßkartoffel" to "sweet potato",
    "süßkartoffeln" to "sweet potatoes",
    "zwiebel" to "onion",
    "zwiebeln" to "onions",
    "rote zwiebel" to "red onion",
    "schalotte" to "shallot",
    "schalotten" to "shallots",
    "frühlingszwiebel" to "spring onion", // oder "scallion", "green onion"
    "lauchzwiebel" to "spring onion",
    "knoblauch" to "garlic",
    "knoblauchzehe" to "garlic clove",
    "karotte" to "carrot",
    "möhre" to "carrot",
    "karotten" to "carrots",
    "möhren" to "carrots",
    "sellerie" to "celery",
    " Knollensellerie" to "celeriac", // oder "celery root"
    "stangensellerie" to "celery sticks",
    "lauch" to "leek",
    "porree" to "leek",
    "tomate" to "tomato",
    "tomaten" to "tomatoes",
    "kirschtomate" to "cherry tomato",
    "kirschtomaten" to "cherry tomatoes",
    "passierte tomaten" to "strained tomatoes", // oder "tomato passata"
    "gehackte tomaten" to "chopped tomatoes",
    "tomatendose" to "canned tomatoes",
    "tomatenmark" to "tomato paste",
    "gurke" to "cucumber",
    "salatgurke" to "cucumber",
    "paprika" to "bell pepper", // generisch
    "rote paprika" to "red bell pepper",
    "gelbe paprika" to "yellow bell pepper",
    "grüne paprika" to "green bell pepper",
    "spitzpaprika" to "pointed pepper",
    "chili" to "chili", // oder "chilli pepper"
    "peperoni" to "pepperoni", // Achtung: im Englischen oft eine Wurstsorte, für Schoten eher "chili peppers"
    "jalapeño" to "jalapeno",
    "brokkoli" to "broccoli",
    "blumenkohl" to "cauliflower",
    "spinat" to "spinach",
    "mangold" to "swiss chard", // oder "chard"
    "grünkohl" to "kale",
    "weißkohl" to "white cabbage",
    "rotkohl" to "red cabbage",
    "blaukraut" to "red cabbage",
    "spitzkohl" to "pointed cabbage",
    "chinakohl" to "napa cabbage", // oder "chinese cabbage"
    "wirsing" to "savoy cabbage",
    "kohlrabi" to "kohlrabi", // oder "german turnip"
    "erbse" to "pea",
    "erbsen" to "peas",
    "zuckererbse" to "sugar snap pea", // oder "snow pea" je nach Art
    "zuckerschote" to "snow pea",
    "bohne" to "bean",
    "bohnen" to "beans",
    "grüne bohnen" to "green beans", // oder "string beans"
    "kidneybohnen" to "kidney beans",
    "weiße bohnen" to "white beans", // oder "cannellini beans"
    "kichererbsen" to "chickpeas", // oder "garbanzo beans"
    "linsen" to "lentils",
    "rote linsen" to "red lentils",
    "tellerlinsen" to "brown lentils", // oder "green lentils"
    "mais" to "corn",
    "zucchini" to "zucchini", // oder "courgette"
    "aubergine" to "eggplant", // oder "aubergine"
    "kürbis" to "pumpkin", // generisch, oft Butternut oder Hokkaido
    "hokkaidokürbis" to "hokkaido pumpkin", // oder "red kuri squash"
    "butternutkürbis" to "butternut squash",
    "radieschen" to "radish",
    "rettich" to "radish", // kann auch "daikon radish" sein
    "spargel" to "asparagus",
    "grüner spargel" to "green asparagus",
    "weißer spargel" to "white asparagus",
    "pilze" to "mushrooms",
    "champignons" to "mushrooms", // oder "button mushrooms"
    "pfifferlinge" to "chanterelle mushrooms",
    "steinpilze" to "porcini mushrooms",
    "austernpilze" to "oyster mushrooms",
    "feldsalat" to "lamb's lettuce", // oder "corn salad"
    "kopfsalat" to "lettuce", // oder "butterhead lettuce"
    "eisbergsalat" to "iceberg lettuce",
    "rucola" to "arugula", // oder "rocket"
    "fenchel" to "fennel",
    "rote bete" to "beetroot", // oder "beet"
    "pastinake" to "parsnip",
    "topinambur" to "jerusalem artichoke", // oder "sunchoke"
    "artischocke" to "artichoke",
    "ingwer" to "ginger",
    "meerrettich" to "horseradish",

    // Obst
    "apfel" to "apple",
    "äpfel" to "apples",
    "birne" to "pear",
    "birnen" to "pears",
    "banane" to "banana",
    "bananen" to "bananas",
    "orange" to "orange",
    "orangen" to "oranges",
    "zitrone" to "lemon",
    "zitronen" to "lemons",
    "limette" to "lime",
    "limetten" to "limes",
    "mandarine" to "mandarin", // oder "tangerine"
    "clementine" to "clementine",
    "grapefruit" to "grapefruit",
    "pampelmuse" to "pomelo", // eng verwandt mit Grapefruit
    "kiwi" to "kiwi",
    "mango" to "mango",
    "ananas" to "pineapple",
    "traube" to "grape",
    "trauben" to "grapes",
    "weintraube" to "grape",
    "weintrauben" to "grapes",
    "erdbeere" to "strawberry",
    "erdbeeren" to "strawberries",
    "himbeere" to "raspberry",
    "himbeeren" to "raspberries",
    "blaubeere" to "blueberry",
    "heidelbeere" to "blueberry",
    "blaubeeren" to "blueberries",
    "heidelbeeren" to "blueberries",
    "brombeere" to "blackberry",
    "brombeeren" to "blackberries",
    "johannisbeere" to "currant",
    "rote johannisbeeren" to "red currants",
    "schwarze johannisbeeren" to "black currants",
    "stachelbeere" to "gooseberry",
    "kirsche" to "cherry",
    "kirschen" to "cherries",
    "sauerkirsche" to "sour cherry",
    "pflaume" to "plum",
    "zwetschge" to "plum", // oft als "damson plum" oder einfach "plum"
    "mirabelle" to "mirabelle plum",
    "aprikose" to "apricot",
    "marille" to "apricot",
    "pfirsich" to "peach",
    "nektarine" to "nectarine",
    "melone" to "melon",
    "wassermelone" to "watermelon",
    "honigmelone" to "honeydew melon",
    "galiamelone" to "galia melon",
    "dattel" to "date",
    "datteln" to "dates",
    "feige" to "fig",
    "feigen" to "figs",
    "granatapfel" to "pomegranate",
    "avocado" to "avocado",
    "rhabarber" to "rhubarb",
    "physalis" to "physalis", // oder "cape gooseberry"

    // Fleisch & Wurst
    "hähnchenbrust" to "chicken breast",
    "hähnchen" to "chicken",
    "hähnchenkeule" to "chicken leg", // oder "chicken thigh" / "drumstick"
    "putenfleisch" to "turkey meat",
    "pute" to "turkey",
    "putenbrust" to "turkey breast",
    "schweinefleisch" to "pork",
    "schweinefilet" to "pork tenderloin", // oder "pork fillet"
    "schweinebraten" to "roast pork",
    "schweinekotelett" to "pork chop",
    "schweineschnitzel" to "pork cutlet", // oder "pork schnitzel"
    "schnitzel" to "schnitzel", // TheMealDB kennt "Schnitzel" oft, kann aber auch "cutlet" sein
    "wiener schnitzel" to "wiener schnitzel", // spezifisch Kalb
    "hackfleisch" to "ground meat", // oder "minced meat"
    "gemischtes hackfleisch" to "mixed ground meat",
    "rinderhack" to "ground beef",
    "schweinehack" to "ground pork",
    "lammhack" to "ground lamb",
    "rindfleisch" to "beef",
    "rinderbraten" to "roast beef",
    "rinderfilet" to "beef tenderloin", // oder "beef fillet"
    "steak" to "steak",
    "hüftsteak" to "sirloin steak", // oder "rump steak"
    "entrecote" to "ribeye steak", // oder "entrecote"
    "gulasch" to "goulash",
    "lammfleisch" to "lamb",
    "lammkeule" to "leg of lamb",
    "lammkotelett" to "lamb chop",
    "wildschwein" to "wild boar",
    "hirsch" to "venison", // oder "deer meat"
    "reh" to "roe deer meat", // oder auch "venison"
    "ente" to "duck",
    "gans" to "goose",
    "speck" to "bacon",
    "schinken" to "ham",
    "roher schinken" to "prosciutto", // oder "cured ham"
    "gekochter schinken" to "cooked ham",
    "salami" to "salami",
    "leberkäse" to "liver cheese", // schwer zu übersetzen, "bavarian meatloaf"
    "bratwurst" to "bratwurst", // oft als deutsches Lehnwort
    "wiener würstchen" to "vienna sausage", // oder "frankfurter"
    "frankfurter" to "frankfurter",

    // Fisch & Meeresfrüchte
    "fisch" to "fish",
    "lachs" to "salmon",
    "forelle" to "trout",
    "kabeljau" to "cod", // oder "codfish"
    "dorsch" to "cod",
    "seelachs" to "pollock", // oder "coley"
    "hering" to "herring",
    "matjes" to "soused herring", // oder "matjes herring"
    "makrele" to "mackerel",
    "thunfisch" to "tuna",
    "garnelen" to "shrimp", // oder "prawns" (größer)
    "krabben" to "shrimp", // Nordseekrabben sind kleiner
    "scampi" to "scampi", // oder "langoustines"
    "muscheln" to "mussels",
    "miesmuscheln" to "blue mussels",
    "jakobsmuscheln" to "scallops",
    "austern" to "oysters",
    "tintenfisch" to "squid", // oder "calamari"
    "oktopus" to "octopus",
    "kaviar" to "caviar",

    // Getreide, Nudeln, Reis
    "reis" to "rice",
    "basmatireis" to "basmati rice",
    "jasminreis" to "jasmine rice",
    "risottoreis" to "risotto rice", // z.B. Arborio, Carnaroli
    "milchreis" to "rice pudding rice", // oder "short-grain rice"
    "vollkornreis" to "brown rice",
    "nudeln" to "pasta", // generisch, auch "noodles"
    "spaghetti" to "spaghetti",
    "penne" to "penne",
    "fusilli" to "fusilli",
    "farfalle" to "farfalle",
    "lasagneplatten" to "lasagna sheets",
    "tagliatelle" to "tagliatelle",
    "bandnudeln" to "tagliatelle", // oder "egg noodles"
    "spätzle" to "spätzle", // deutsches Lehnwort
    "gnocchi" to "gnocchi",
    "couscous" to "couscous",
    "bulgur" to "bulgur",
    "quinoa" to "quinoa",
    "polenta" to "polenta",
    "haferflocken" to "oats", // oder "rolled oats"
    "cornflakes" to "cornflakes",
    "müsli" to "muesli",
    "brot" to "bread",
    "weißbrot" to "white bread",
    "graubrot" to "rye bread", // oder "grey bread"
    "vollkornbrot" to "whole wheat bread",
    "roggenbrot" to "rye bread",
    "toastbrot" to "toast bread",
    "brötchen" to "bread roll", // oder "bun"
    "semmel" to "bread roll",
    "baguette" to "baguette",
    "ciabatta" to "ciabatta",
    "knäckebrot" to "crispbread",
    "brezel" to "pretzel",

    // Käse
    "käse" to "cheese",
    "schnittkäse" to "semi-hard cheese", // generisch
    "hartkäse" to "hard cheese",
    "weichkäse" to "soft cheese",
    "frischkäse" to "cream cheese",
    "gauda" to "gouda cheese",
    "edamer" to "edam cheese",
    "emmentaler" to "emmental cheese", // oder "swiss cheese"
    "cheddar" to "cheddar cheese",
    "parmesan" to "parmesan cheese",
    "pecorino" to "pecorino cheese",
    "mozzarella" to "mozzarella",
    "feta" to "feta cheese",
    "schafskäse" to "sheep cheese", // Feta ist eine Art davon
    "ziegenkäse" to "goat cheese",
    "camembert" to "camembert cheese",
    "brie" to "brie cheese",
    "blaunschimmelkäse" to "blue cheese",
    "gorgonzola" to "gorgonzola cheese",
    "roquefort" to "roquefort cheese",
    "bergkäse" to "mountain cheese", // generisch
    "raclettekäse" to "raclette cheese",
    "frischkäse" to "cream cheese",
    "ricotta" to "ricotta cheese",
    "mascarpone" to "mascarpone cheese",

    // Backzutaten & Süßes
    "backpulver" to "baking powder",
    "natron" to "baking soda", // oder "sodium bicarbonate"
    "hefe" to "yeast",
    "trockenhefe" to "dry yeast",
    "frische hefe" to "fresh yeast",
    "gelatine" to "gelatin",
    "agar-agar" to "agar-agar",
    "vanilleextrakt" to "vanilla extract",
    "vanilleschote" to "vanilla bean", // oder "vanilla pod"
    "zimt" to "cinnamon",
    "nelken" to "cloves",
    "muskatnuss" to "nutmeg",
    "kardamom" to "cardamom",
    "anis" to "anise",
    "sternanis" to "star anise",
    "honig" to "honey",
    "ahornsirup" to "maple syrup",
    "marmelade" to "jam", // oder "marmalade" für Zitrusfrüchte
    "konfitüre" to "jam",
    "schokolade" to "chocolate",
    "zartbitterschokolade" to "dark chocolate",
    "vollmilchschokolade" to "milk chocolate",
    "weiße schokolade" to "white chocolate",
    "kuvertüre" to "couverture chocolate", // oder "coating chocolate"
    "kakaopulver" to "cocoa powder",
    "nüsse" to "nuts",
    "mandeln" to "almonds",
    "haselnüsse" to "hazelnuts",
    "walnüsse" to "walnuts",
    "paranüsse" to "brazil nuts",
    "cashewkerne" to "cashew nuts", // oder "cashews"
    "pistazien" to "pistachios",
    "macadamianüsse" to "macadamia nuts",
    "erdnüsse" to "peanuts",
    "kokosnuss" to "coconut",
    "kokosraspeln" to "desiccated coconut", // oder "shredded coconut"
    "kokosmilch" to "coconut milk",
    "rosinen" to "raisins",
    "sultaninen" to "sultanas", // oft synonym mit Rosinen verwendet
    "korinthen" to "currants", // getrocknete, kleine Trauben
    "marzipan" to "marzipan",
    "nougat" to "nougat",
    "kekse" to "cookies", // US, oder "biscuits" (UK)
    "plätzchen" to "cookies",

    // Kräuter & Gewürze (einige doppelt, aber mit Fokus hier)
    "salz" to "salt",
    "meersalz" to "sea salt",
    "jodsalz" to "iodized salt",
    "pfeffer" to "pepper",
    "schwarzer pfeffer" to "black pepper",
    "weißer pfeffer" to "white pepper",
    "cayennepfeffer" to "cayenne pepper",
    "paprikapulver" to "paprika powder", // kann süß (edelsüß) oder scharf sein
    "paprika edelsüß" to "sweet paprika",
    "paprika rosenscharf" to "hot paprika",
    "currypulver" to "curry powder",
    "kurkuma" to "turmeric",
    "kreuzkümmel" to "cumin",
    "kümmel" to "caraway", // nicht dasselbe wie Kreuzkümmel!
    "koriander" to "coriander", // Samen und Blätter (cilantro)
    "korianderblätter" to "cilantro", // oder "coriander leaves"
    "petersilie" to "parsley",
    "glatte petersilie" to "flat-leaf parsley",
    "krause petersilie" to "curly parsley",
    "schnittlauch" to "chives",
    "dill" to "dill",
    "basilikum" to "basil",
    "oregano" to "oregano",
    "thymian" to "thyme",
    "rosmarin" to "rosemary",
    "majoran" to "marjoram",
    "salbei" to "sage",
    "estragon" to "tarragon",
    "lorbeerblatt" to "bay leaf",
    "lorbeerblätter" to "bay leaves",
    "minze" to "mint",
    "pfefferminze" to "peppermint",
    "lavendel" to "lavender",
    "wacholderbeeren" to "juniper berries",
    "senf" to "mustard",
    "scharfer senf" to "hot mustard", // oder "spicy mustard"
    "mittelscharfer senf" to "medium hot mustard",
    "süßer senf" to "sweet mustard", // bayerischer Art
    "dijonsenf" to "dijon mustard",
    "ketchup" to "ketchup",
    "mayonnaise" to "mayonnaise",
    "essig" to "vinegar",
    "apfelessig" to "apple cider vinegar",
    "weißweinessig" to "white wine vinegar",
    "rotweinessig" to "red wine vinegar",
    "balsamicoessig" to "balsamic vinegar", // oder nur "balsamic"
    "sojasauce" to "soy sauce",
    "worcestersauce" to "worcestershire sauce",
    "tabasco" to "tabasco sauce",
    "sambal oelek" to "sambal oelek",
    "fischsauce" to "fish sauce",
    "brühe" to "broth", // oder "stock"
    "gemüsebrühe" to "vegetable broth",
    "hühnerbrühe" to "chicken broth",
    "rinderbrühe" to "beef broth",
    "brühwürfel" to "stock cube", // oder "bouillon cube"

    // Sonstiges
    "wasser" to "water",
    "eiswürfel" to "ice cubes",
    "wein" to "wine",
    "rotwein" to "red wine",
    "weißwein" to "white wine",
    "bier" to "beer",
    "kaffee" to "coffee",
    "tee" to "tea",
    "schwarztee" to "black tea",
    "grüntee" to "green tea",
    "kräutertee" to "herbal tea",
    "semmelknödel" to "bread dumpling",
    "kartoffelknödel" to "potato dumpling",
    "rotkohl mit apfelstücken" to "red cabbage with apple pieces", // Beispiel für komplexere
    "volvic juicy orange mango, 1 l" to "volvic juicy orange mango, 1 l", // Markennamen bleiben oft
    "naturalis classic" to "naturalis classic", // Markennamen bleiben oft
    "schnitzel" to "cutlet" // nochmal explizit, kann spezifischer als "schnitzel" sein
    // ... Erweitere diese Liste nach Bedarf
)

// --- Datenklassen für Übersetzungs-APIs (bleiben lokal in dieser Datei) ---
@Serializable
data class LibreTranslateResponse(
    val translatedText: String? = null,
    val error: String? = null
)

@Serializable
data class MyMemoryResponseData(
    val translatedText: String,
    val match: Float
)

@Serializable
data class MyMemoryResponse(
    val responseData: MyMemoryResponseData?,
    val responseStatus: Int,
    val responseDetails: String? = null
)

// ----- GELÖSCHT: Die folgenden Datenklassen sind jetzt in RecipeData.kt definiert und werden von dort importiert -----
// @Serializable
// data class Meal(...) { ... } // Alte Meal-Klasse komplett entfernt
//
// @Serializable
// data class MealDBResponse(val meals: List<Meal>?) // Alte MealDBResponse-Klasse entfernt
//
// data class RecipeSummary(...) // Alte RecipeSummary-Klasse entfernt
//
// data class RecipeDetail(...) { // Alte RecipeDetail-Klasse (und ihre innere Ingredient-Klasse) entfernt
//    data class Ingredient(...)
// }
// -------------------------------------------------------------------------------------------------------------


object RecipeApiService {

    private val client: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 10000
            }
        }
    }

    private val translationCache = ConcurrentHashMap<String, String>()
    private const val MYMEMORY_API_URL = "https://api.mymemory.translated.net/get"

    suspend fun translateIngredient(
        ingredientName: String,
        sourceLang: String = "de",
        targetLang: String = "en",
        useLibreTranslateAsFallback: Boolean = false,
        myMemoryEmail: String? = null
    ): String {
        val normalizedOriginal = ingredientName.lowercase().trim()
        if (normalizedOriginal.isEmpty()) return ""

        translationCache[normalizedOriginal]?.let {
            println("DEBUG_API_TRANSLATE_MAIN: Cache HIT for '$normalizedOriginal' -> '$it'")
            return it
        }
        println("DEBUG_API_TRANSLATE_MAIN: Cache MISS for '$normalizedOriginal'.")

        GERMAN_TO_ENGLISH_INGREDIENT_MAP[normalizedOriginal]?.let { localTranslation ->
            if (localTranslation != normalizedOriginal) {
                println("DEBUG_API_TRANSLATE_MAIN: Local Map HIT for '$normalizedOriginal' -> '$localTranslation'")
                translationCache[normalizedOriginal] = localTranslation
                return localTranslation
            }
        }
        println("DEBUG_API_TRANSLATE_MAIN: Local Map MISS for '$normalizedOriginal'.")

        var translatedName = translateIngredientViaMyMemoryInternal(normalizedOriginal, sourceLang, targetLang, myMemoryEmail)

        if (translatedName == normalizedOriginal && useLibreTranslateAsFallback) {
            println("DEBUG_API_TRANSLATE_MAIN: MyMemory returned original. Attempting LibreTranslate...")
            translatedName = translateIngredientViaLibreTranslateInternal(normalizedOriginal, sourceLang, targetLang)
        }

        if (translatedName != normalizedOriginal && translatedName.isNotBlank()) {
            println("DEBUG_API_TRANSLATE_MAIN: Final translation for '$normalizedOriginal' -> '$translatedName'. Caching.")
            translationCache[normalizedOriginal] = translatedName
        } else if (translatedName == normalizedOriginal) {
            println("DEBUG_API_TRANSLATE_MAIN: All translation attempts failed or returned original for '$normalizedOriginal'. Using original.")
        }
        return translatedName
    }

    private suspend fun translateIngredientViaMyMemoryInternal(
        normalizedOriginal: String, sourceLang: String, targetLang: String, email: String?
    ): String {
        println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL: Calling MyMemory for '$normalizedOriginal'...")
        return try {
            val response: MyMemoryResponse = client.get(MYMEMORY_API_URL) {
                parameter("q", normalizedOriginal)
                parameter("langpair", "$sourceLang|$targetLang")
                email?.let { parameter("de", it) }
            }.body()
            if (response.responseStatus == 200 && response.responseData != null && response.responseData.translatedText.isNotBlank()) {
                val translated = response.responseData.translatedText.lowercase().trim()
                if (response.responseData.match < 0.5 && translated == normalizedOriginal) {
                    normalizedOriginal
                } else {
                    println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL: Success: '$normalizedOriginal' -> '$translated'")
                    translated
                }
            } else {
                println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL_ERROR: Failed for '$normalizedOriginal'. Status: ${response.responseStatus}")
                normalizedOriginal
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_MYMEMORY_INTERNAL_EXCEPTION: for '$normalizedOriginal': ${e.message}")
            normalizedOriginal
        }
    }

    private suspend fun translateIngredientViaLibreTranslateInternal(
        normalizedOriginal: String, sourceLang: String, targetLang: String
    ): String {
        val libreTranslateApiUrl = "https://translate.argosopentech.com/translate"
        println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL: Calling LibreTranslate for '$normalizedOriginal'...")
        return try {
            val response: LibreTranslateResponse = client.post(libreTranslateApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("q" to normalizedOriginal, "source" to sourceLang, "target" to targetLang, "format" to "text"))
            }.body()
            if (response.translatedText != null && response.translatedText.isNotBlank()) {
                val translated = response.translatedText.lowercase().trim()
                println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL: Success: '$normalizedOriginal' -> '$translated'")
                translated
            } else {
                println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL_ERROR: Failed for '$normalizedOriginal'. API Error: ${response.error}")
                normalizedOriginal
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL_EXCEPTION: for '$normalizedOriginal': ${e.message}")
            normalizedOriginal
        }
    }

    suspend fun getRandomRecipes(count: Int = 10): Result<List<RecipeSummary>> {
        println("DEBUG_API_THEMEALDB: getRandomRecipes called")
        return try {
            // Verwende MealDBResponse und MealDBRecipe aus RecipeData.kt
            val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}filter.php") {
                parameter("c", "Seafood")
            }.body()
            println("DEBUG_API_THEMEALDB: getRandomRecipes response meals count: ${response.meals?.size ?: 0}")

            // .toRecipeSummary() ist jetzt eine Erweiterungsfunktion auf MealDBRecipe aus RecipeData.kt
            val recipes = response.meals?.take(count)?.mapNotNull { mealDBRecipe ->
                mealDBRecipe.toRecipeSummary()
            } ?: emptyList()
            Result.success(recipes)
        } catch (e: Exception) {
            println("DEBUG_API_THEMEALDB_ERROR: getRandomRecipes failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getRecipeDetails(recipeId: String): Result<RecipeDetail> {
        println("DEBUG_API_THEMEALDB: getRecipeDetails called for ID: $recipeId")
        return try {
            // Verwende MealDBResponse und MealDBRecipe aus RecipeData.kt
            val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}lookup.php") {
                parameter("i", recipeId)
            }.body()
            val mealDBRecipe = response.meals?.firstOrNull() // Ist jetzt vom Typ MealDBRecipe
            if (mealDBRecipe != null) {
                println("DEBUG_API_THEMEALDB: getRecipeDetails success for ID: $recipeId, Title: ${mealDBRecipe.strMeal}")
                // .toRecipeDetail() ist jetzt eine Erweiterungsfunktion auf MealDBRecipe aus RecipeData.kt
                Result.success(mealDBRecipe.toRecipeDetail())
            } else {
                println("DEBUG_API_THEMEALDB_WARN: getRecipeDetails recipe not found for ID: $recipeId")
                Result.failure(Exception("Rezept nicht gefunden (ID: $recipeId) oder API-Antwort fehlerhaft."))
            }
        } catch (e: Exception) {
            println("DEBUG_API_THEMEALDB_ERROR: getRecipeDetails failed for ID $recipeId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun findRecipesContainingAnyOfIngredients(
        ingredientNames: List<String>,
        maxIngredientsToQuery: Int = 3,
        maxResultsPerIngredient: Int = 5
    ): Result<List<RecipeSummary>> {
        println("DEBUG_API_THEMEALDB: findRecipesContainingAnyOfIngredients called with EN ingredients: $ingredientNames")
        if (ingredientNames.isEmpty()) {
            return Result.success(emptyList())
        }

        val allFoundRecipes = mutableSetOf<RecipeSummary>()

        ingredientNames.take(maxIngredientsToQuery).forEach { ingredientNameEN ->
            val formattedIngredientName = ingredientNameEN.replace(" ", "_").trim()
            if (formattedIngredientName.isEmpty()) return@forEach

            try {
                println("DEBUG_API_THEMEALDB: Querying filter.php for EN ingredient: '$formattedIngredientName'")
                // Verwende MealDBResponse und MealDBRecipe aus RecipeData.kt
                val response: MealDBResponse = client.get("${BASE_URL_THEMEALDB}filter.php") {
                    parameter("i", formattedIngredientName)
                }.body()
                println("DEBUG_API_THEMEALDB: Response for EN '$formattedIngredientName' meals count: ${response.meals?.size ?: 0}")

                response.meals?.take(maxResultsPerIngredient)?.forEach { mealDBRecipe ->
                    // .toRecipeSummary() ist jetzt eine Erweiterungsfunktion auf MealDBRecipe aus RecipeData.kt
                    allFoundRecipes.add(mealDBRecipe.toRecipeSummary())
                }
            } catch (e: Exception) {
                println("DEBUG_API_THEMEALDB_ERROR: findRecipes... - Error for EN ingredient '$formattedIngredientName': ${e.message}")
            }
        }
        println("DEBUG_API_THEMEALDB: findRecipes... - Total unique recipes found: ${allFoundRecipes.size}")
        return Result.success(allFoundRecipes.toList())
    }

    suspend fun translateText(
        textToTranslate: String,
        sourceLang: String = "en", // Meistens Englisch von der API
        targetLang: String = "de",
        myMemoryEmail: String? = null // Für MyMemory, falls du es weiterhin nutzen willst
    ): String {
        if (textToTranslate.isBlank() || sourceLang == targetLang) {
            return textToTranslate
        }

        val normalizedText = textToTranslate.trim() // Für längere Texte ist lowercase() nicht immer ideal

        // 1. Prüfe den Cache (erwäge einen separaten Cache für längere Texte oder einen mit Größenlimit)
        translationCache[normalizedText]?.let { // Du könntest den Cache-Key spezifischer machen, z.B. "$sourceLang-$targetLang:$normalizedText"
            println("DEBUG_API_TRANSLATE_TEXT: Cache HIT for text starting with '${normalizedText.take(30)}...' -> '$it'")
            return it
        }
        println("DEBUG_API_TRANSLATE_TEXT: Cache MISS for text starting with '${normalizedText.take(30)}...'.")

        // 2. MyMemory (kann auch längere Texte, aber beachte API-Limits)
        //    Es ist fraglich, ob MyMemory für ganze Anleitungen die beste Wahl ist, LibreTranslate könnte besser sein.
        //    Du könntest hier auch direkt zu LibreTranslate gehen oder die Logik anpassen.
        var translatedText = try {
            val response: MyMemoryResponse = client.get(MYMEMORY_API_URL) {
                parameter("q", normalizedText)
                parameter("langpair", "$sourceLang|$targetLang")
                myMemoryEmail?.let { parameter("de", it) }
                // Beachte, dass MyMemory manchmal HTML-Entitäten zurückgibt, die du dekodieren müsstest.
            }.body()
            if (response.responseStatus == 200 && response.responseData != null && response.responseData.translatedText.isNotBlank()) {
                // Hier könntest du noch prüfen, ob die Übersetzung plausibel ist (nicht das Original, etc.)
                println("DEBUG_API_TRANSLATE_TEXT_MYMEMORY: Success for text starting with '${normalizedText.take(30)}...'")
                response.responseData.translatedText // Ggf. .trim()
            } else {
                println("DEBUG_API_TRANSLATE_TEXT_MYMEMORY_ERROR: Failed for text. Status: ${response.responseStatus}")
                normalizedText // Fallback zum Original
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_TEXT_MYMEMORY_EXCEPTION: ${e.message}")
            normalizedText // Fallback zum Original
        }

        // 3. LibreTranslate als Fallback oder primäre Wahl für längere Texte
        if (translatedText == normalizedText) { // Wenn MyMemory fehlschlug oder das Original lieferte
            println("DEBUG_API_TRANSLATE_TEXT: MyMemory returned original or failed. Attempting LibreTranslate...")
            translatedText = translateTextViaLibreTranslateInternal(normalizedText, sourceLang, targetLang)
        }

        // 4. In den Cache legen
        if (translatedText != normalizedText && translatedText.isNotBlank()) {
            println("DEBUG_API_TRANSLATE_TEXT: Final translation for text starting with '${normalizedText.take(30)}...' -> '${translatedText.take(30)}...'. Caching.")
            translationCache[normalizedText] = translatedText // Wieder: Cache-Key überdenken
        } else if (translatedText == normalizedText) {
            println("DEBUG_API_TRANSLATE_TEXT: All translation attempts failed or returned original. Using original.")
        }
        return translatedText
    }

    // Die separate LibreTranslate-Funktion für Texte (fast identisch zu der für Zutaten)
    private suspend fun translateTextViaLibreTranslateInternal(
        normalizedText: String, sourceLang: String, targetLang: String
    ): String {
        val libreTranslateApiUrl = "https://translate.argosopentech.com/translate" // oder eine andere Instanz
        println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL (TEXT): Calling LibreTranslate for text starting with '${normalizedText.take(30)}...'")
        return try {
            val requestBody = mapOf("q" to normalizedText, "source" to sourceLang, "target" to targetLang, "format" to "text")
            val response: LibreTranslateResponse = client.post(libreTranslateApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()
            if (response.translatedText != null && response.translatedText.isNotBlank()) {
                val translated = response.translatedText //.trim() ist bei längeren Texten vllt. nicht immer gewollt
                println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL (TEXT): Success for text starting with '${normalizedText.take(30)}...'")
                translated
            } else {
                println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL_ERROR (TEXT): Failed. API Error: ${response.error}")
                normalizedText
            }
        } catch (e: Exception) {
            println("DEBUG_API_TRANSLATE_LIBRE_INTERNAL_EXCEPTION (TEXT): ${e.message}")
            normalizedText
        }
    }
}