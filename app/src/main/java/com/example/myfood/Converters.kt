package com.example.myfood.data.local // Passe den Paketnamen an

import androidx.compose.ui.input.key.type
import androidx.room.TypeConverter
import com.example.myfood.data.model.Ingredient // Passe den Import an
import com.google.gson.Gson // Einfach für Serialisierung/Deserialisierung von Listen
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // Für List<Ingredient>
    @TypeConverter
    fun fromIngredientList(ingredients: List<Ingredient>?): String? {
        return ingredients?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toIngredientList(ingredientsString: String?): List<Ingredient>? {
        return ingredientsString?.let {
            val type = object : TypeToken<List<Ingredient>>() {}.type
            gson.fromJson(it, type)
        }
    }

    // Für List<String> (z.B. für Zubereitungsschritte)
    @TypeConverter
    fun fromStringList(strings: List<String>?): String? {
        return strings?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(stringsString: String?): List<String>? {
        return stringsString?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
    }
}
