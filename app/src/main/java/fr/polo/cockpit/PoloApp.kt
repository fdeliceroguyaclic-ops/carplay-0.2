package fr.polo.cockpit

import android.app.Application
import fr.polo.cockpit.apps.AppLauncher
import fr.polo.cockpit.data.SettingsStore
import fr.polo.cockpit.vehicle.VehicleRepository
import fr.polo.cockpit.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Conteneur de dépendances simple (pas de framework d'injection : APK léger). */
class PoloApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsStore(this) }
    val vehicle by lazy { VehicleRepository(this, appScope) }
    val launcher by lazy { AppLauncher(this) }
    val weather by lazy { WeatherRepository() }

    override fun onCreate() {
        super.onCreate()
        // Enregistre tout plantage (affiché dans Réglages › Système et joint au rapport)
        fr.polo.cockpit.vehicle.CrashLog.install(this)
    }
}
