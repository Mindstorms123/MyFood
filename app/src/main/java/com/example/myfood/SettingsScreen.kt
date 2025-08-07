package com.example.myfood.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// Import für LocalContext, um auf Ressourcen zuzugreifen
import androidx.compose.ui.platform.LocalContext // <<< HINZUGEFÜGT
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// Unnötige explizite Imports für getValue/setValue entfernt, wenn sie nicht direkt genutzt werden
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myfood.R // <<< HINZUGEFÜGT (für Zugriff auf R.plurals)
import com.example.myfood.datastore.NotificationSettings
import com.example.myfood.ui.theme.MyFoodTheme
// Locale wird nicht mehr direkt für die Formatierung dieses Texts benötigt,
// da getQuantityString das handhabt. Kann aber für andere Formatierungen bleiben.
// import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberTimePickerState(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean = true
): TimePickerState {
    return remember(initialHour, initialMinute, is24Hour) {
        TimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = is24Hour
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentSettings by viewModel.currentSettings.collectAsState()
    var showTimePickerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current // <<< HINZUGEFÜGT

    val timePickerState = rememberTimePickerState(
        initialHour = currentSettings.notificationHour,
        initialMinute = currentSettings.notificationMinute
    )

    // Dieser State hält den Wert, mit dem der Slider gerade interagiert.
    // Er wird initial mit dem Wert aus `currentSettings` gesetzt.
    var sliderInteractionValue by remember(currentSettings.leadTimeDays) {
        mutableFloatStateOf(currentSettings.leadTimeDays.toFloat())
    }

    // --- TimePickerDialog bleibt unverändert ---
    if (showTimePickerDialog) {
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false }
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Benachrichtigungszeit wählen", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePickerDialog = false }) {
                            Text("Abbrechen")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            viewModel.updateNotificationTime(timePickerState.hour, timePickerState.minute)
                            showTimePickerDialog = false
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp) // Sorgt für gleichmäßigen Abstand zwischen den Elementen
    ) {
        Text("Benachrichtigungseinstellungen", style = MaterialTheme.typography.headlineSmall)

        // Vorlaufzeit in Tagen mit korrekter Pluralisierung
        val leadTimeDaysInt = sliderInteractionValue.toInt() // Konvertiere zu Int für getQuantityString
        Text(
            context.resources.getQuantityString(
                R.plurals.notification_lead_time_days, // Deine Plural-Ressource
                leadTimeDaysInt,                       // Die Anzahl, die die Auswahl (one/other) bestimmt
                leadTimeDaysInt                        // Der Wert, der in %d eingesetzt wird
            )
        )
        Slider(
            value = sliderInteractionValue,
            onValueChange = { newValue ->
                sliderInteractionValue = newValue // UI sofort aktualisieren
            },
            valueRange = 1f..14f,
            steps = 12, // (14-1) = 13 Schritte, also 12 step-Punkte
            onValueChangeFinished = {
                // Wenn der Nutzer die Interaktion beendet, den Wert im ViewModel speichern.
                viewModel.updateLeadTime(sliderInteractionValue.toInt())
            }
        )
        // Die separate Textanzeige unter dem Slider ist jetzt nicht mehr nötig.

        Spacer(modifier = Modifier.height(16.dp)) // Abstandshalter

        // Uhrzeit der Benachrichtigung
        Text("Tägliche Prüfung um:")
        Button(onClick = { showTimePickerDialog = true }) {
            Text(
                String.format(
                    java.util.Locale.getDefault(), // Locale hier explizit für die Zeitformatierung
                    "%02d:%02d Uhr",
                    currentSettings.notificationHour,
                    currentSettings.notificationMinute
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.Edit, contentDescription = "Uhrzeit ändern")
        }
    }
}

// Preview kann angepasst werden, um mit Dummy-Daten zu arbeiten oder ein Mock-ViewModel zu verwenden.
@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MyFoodTheme {
        val dummySettings = remember { NotificationSettings(leadTimeDays = 3, notificationHour = 10, notificationMinute = 30) }
        var sliderPos by remember(dummySettings.leadTimeDays) { mutableFloatStateOf(dummySettings.leadTimeDays.toFloat()) }
        val context = LocalContext.current // Für die Preview ebenfalls notwendig

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Benachrichtigungseinstellungen", style = MaterialTheme.typography.headlineSmall)

            val leadTimeDaysIntPreview = sliderPos.toInt()
            Text(
                // In der Preview kann es sein, dass R.plurals nicht direkt aufgelöst wird,
                // oder du eine Dummy-Implementierung für die Preview benötigst,
                // falls getQuantityString in der Preview-Umgebung Probleme macht.
                // Für eine einfache Preview könntest du den String manuell zusammenbauen:
                if (leadTimeDaysIntPreview == 1) {
                    "Benachrichtigung $leadTimeDaysIntPreview Tag vorher senden"
                } else {
                    "Benachrichtigung $leadTimeDaysIntPreview Tage vorher senden"
                }
                // ODER wenn die Preview `getQuantityString` unterstützt:
                // context.resources.getQuantityString(
                //     R.plurals.notification_lead_time_days,
                //     leadTimeDaysIntPreview,
                //     leadTimeDaysIntPreview
                // )
            )
            Slider(
                value = sliderPos,
                onValueChange = { sliderPos = it },
                valueRange = 1f..14f,
                steps = 12
            )
            // Der doppelte Text ist auch hier entfernt
            Spacer(modifier = Modifier.height(16.dp))
            Text("Tägliche Prüfung um:")
            Button(onClick = { /* In Preview nicht interaktiv für Dialog */ }) {
                Text(
                    String.format(
                        java.util.Locale.getDefault(),
                        "%02d:%02d Uhr",
                        dummySettings.notificationHour,
                        dummySettings.notificationMinute
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.Edit, contentDescription = "Uhrzeit ändern")
            }
        }
    }
}