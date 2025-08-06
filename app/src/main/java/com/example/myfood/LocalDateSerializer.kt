package com.example.myfood // Oder dein passendes package

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException // Für bessere Fehlerbehandlung beim Parsen

object LocalDateSerializer : KSerializer<LocalDate?> { // KSerializer für LocalDate? (optionales Datum)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE // Standard ISO Format (YYYY-MM-DD)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate?) {
        if (value == null) {
            // Explizit null serialisieren, wenn der Wert null ist.
            // Alternativ könnte encoder.encodeNotNullMark() und dann encoder.encodeString() verwendet werden,
            // aber das macht das Deserialisieren manchmal komplexer, wenn man leere Strings abfangen muss.
            // encoder.encodeNull() ist oft klarer für optionale Typen.
            encoder.encodeNull()
        } else {
            encoder.encodeString(value.format(formatter))
        }
    }

    override fun deserialize(decoder: Decoder): LocalDate? {
        // Zuerst prüfen, ob der Wert explizit als null dekodiert wird.
        if (decoder.decodeNotNullMark()) { // Gibt true zurück, wenn der Wert nicht null ist
            return try {
                LocalDate.parse(decoder.decodeString(), formatter)
            } catch (e: DateTimeParseException) {
                // Handle den Fall, dass der String kein valides Datum im erwarteten Format ist.
                // Hier könntest du einen Fehler loggen oder null zurückgeben,
                // je nachdem, wie strikt deine Anwendung sein soll.
                println("Error deserializing LocalDate: Invalid date format. ${e.message}")
                null // Fallback auf null, wenn das Parsen fehlschlägt
            }
        } else {
            // Der Wert wurde als null markiert, also null zurückgeben.
            return null
        }
    }
}
