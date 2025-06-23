package com.example.myfood // Stelle sicher, dass dies dein korrekter Paketname ist

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyFoodApplication : Application() {
    // Du kannst die onCreate()-Methode hier überschreiben,
    // falls du zusätzliche Initialisierungslogik für deine App benötigst,
    // die beim Start ausgeführt werden soll.
    // override fun onCreate() {
    //     super.onCreate()
    //     // Deine Initialisierungen
    // }
}