package fr.polo.cockpit.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cockpit_settings")

enum class ClockStyle(val label: String) { DIGITAL("Numérique"), LARGE("Grand format"), MINIMAL("Minimal") }

/** Couleurs proposées pour l'interface. */
val ACCENT_PRESETS = listOf(
    "Bleu Volkswagen" to 0xFF0A7CFFL,
    "Rouge sportif" to 0xFFE5262DL,
    "Blanc" to 0xFFF2F5F8L,
    "Violet" to 0xFF8E5CFFL,
    "Cyan" to 0xFF22D3EEL,
    "Ambre" to 0xFFFFB020L,
)

/** Teintes de carrosserie (approximations de teintes Polo 6R, à ajuster). */
val BODY_PRESETS = listOf(
    "Blanc Candy" to 0xFFF4F4F2L,
    "Noir Intense" to 0xFF0E0F12L,
    "Gris Urano" to 0xFF5D6166L,
    "Argent Reflex" to 0xFFB7BBBFL,
    "Rouge Flash" to 0xFFB3121BL,
    "Bleu Shadow" to 0xFF1D2D4FL,
    "Bleu Pacifique" to 0xFF1E5AA8L,
    "Vert Viper" to 0xFF5E9E2AL,
)

data class CockpitSettings(
    val accent: Long = 0xFF0A7CFFL,
    val bodyColor: Long = 0xFFF4F4F2L,
    val vehicleName: String = "POLO V",
    val plate: String = "DF-618-XL",
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val showSeconds: Boolean = false,
    val animations: Boolean = true,
    val splash: Boolean = true,
    val dim: Float = 0f,
    val wallpaperUri: String? = null,
    val widgetWeather: Boolean = true,
    val widgetVehicle: Boolean = true,
    val widgetConsumption: Boolean = true,
    val demoMode: Boolean = true,
    val obdEnabled: Boolean = false,
    val obdAddress: String? = null,
    /** "bt" (Bluetooth) ou "wifi" */
    val obdTransport: String = "bt",
    val obdWifiHost: String = "192.168.0.10",
    val obdWifiPort: Int = 35000,
    /** Clé de la configuration dont la connexion a été testée avec succès (voir ObdConfig.key). */
    val obdVerified: String? = null,
    /** Associations CanBox, une par ligne : ACTION;EXTRA;CHAMP[;FACTEUR|bit:N] */
    val canMappings: String = "",
    val weatherCity: String = "Cayenne",
    val weatherLat: Float = 4.93f,
    val weatherLon: Float = -52.33f,
    val listenActions: String = "",
    /** Raccourcis choisis par l'utilisateur : "clé=paquet;clé=paquet" */
    val shortcutOverrides: String = "",
) {
    val overrides: Map<String, String>
        get() = shortcutOverrides.split(';').mapNotNull {
            val p = it.split('='); if (p.size == 2 && p[1].isNotBlank()) p[0] to p[1] else null
        }.toMap()

    val obdConfig: fr.polo.cockpit.vehicle.ObdConfig
        get() = fr.polo.cockpit.vehicle.ObdConfig(obdTransport, obdAddress, obdWifiHost, obdWifiPort)

    /** La lecture OBD n'est autorisée que pour une configuration testée avec succès. */
    val obdConfigVerified: Boolean get() = obdVerified != null && obdVerified == obdConfig.key
}

class SettingsStore(private val context: Context) {
    private object K {
        val accent = longPreferencesKey("accent")
        val body = longPreferencesKey("body")
        val name = stringPreferencesKey("name")
        val plate = stringPreferencesKey("plate")
        val clock = stringPreferencesKey("clock")
        val seconds = booleanPreferencesKey("seconds")
        val anim = booleanPreferencesKey("anim")
        val splash = booleanPreferencesKey("splash")
        val dim = floatPreferencesKey("dim")
        val wallpaper = stringPreferencesKey("wallpaper")
        val wWeather = booleanPreferencesKey("w_weather")
        val wVehicle = booleanPreferencesKey("w_vehicle")
        val wConso = booleanPreferencesKey("w_conso")
        val demo = booleanPreferencesKey("demo")
        val obd = booleanPreferencesKey("obd")
        val obdAddr = stringPreferencesKey("obd_addr")
        val city = stringPreferencesKey("city")
        val lat = floatPreferencesKey("lat")
        val lon = floatPreferencesKey("lon")
        val listen = stringPreferencesKey("listen")
        val shortcuts = stringPreferencesKey("shortcuts")
        val obdTransport = stringPreferencesKey("obd_transport")
        val obdWifiHost = stringPreferencesKey("obd_wifi_host")
        val obdWifiPort = intPreferencesKey("obd_wifi_port")
        val obdVerified = stringPreferencesKey("obd_verified")
        val canMappings = stringPreferencesKey("can_mappings")
    }

    val settings: Flow<CockpitSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): CockpitSettings {
        val d = CockpitSettings()
        return CockpitSettings(
            accent = this[K.accent] ?: d.accent,
            bodyColor = this[K.body] ?: d.bodyColor,
            vehicleName = this[K.name] ?: d.vehicleName,
            plate = this[K.plate] ?: d.plate,
            clockStyle = runCatching { ClockStyle.valueOf(this[K.clock] ?: "") }.getOrDefault(d.clockStyle),
            showSeconds = this[K.seconds] ?: d.showSeconds,
            animations = this[K.anim] ?: d.animations,
            splash = this[K.splash] ?: d.splash,
            dim = this[K.dim] ?: d.dim,
            wallpaperUri = this[K.wallpaper],
            widgetWeather = this[K.wWeather] ?: d.widgetWeather,
            widgetVehicle = this[K.wVehicle] ?: d.widgetVehicle,
            widgetConsumption = this[K.wConso] ?: d.widgetConsumption,
            demoMode = this[K.demo] ?: d.demoMode,
            obdEnabled = this[K.obd] ?: d.obdEnabled,
            obdAddress = this[K.obdAddr],
            weatherCity = this[K.city] ?: d.weatherCity,
            weatherLat = this[K.lat] ?: d.weatherLat,
            weatherLon = this[K.lon] ?: d.weatherLon,
            listenActions = this[K.listen] ?: d.listenActions,
            obdTransport = this[K.obdTransport] ?: d.obdTransport,
            obdWifiHost = this[K.obdWifiHost] ?: d.obdWifiHost,
            obdWifiPort = this[K.obdWifiPort] ?: d.obdWifiPort,
            obdVerified = this[K.obdVerified],
            canMappings = this[K.canMappings] ?: d.canMappings,
            shortcutOverrides = this[K.shortcuts] ?: d.shortcutOverrides,
        )
    }

    suspend fun update(transform: (CockpitSettings) -> CockpitSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.accent] = s.accent
            p[K.body] = s.bodyColor
            p[K.name] = s.vehicleName
            p[K.plate] = s.plate
            p[K.clock] = s.clockStyle.name
            p[K.seconds] = s.showSeconds
            p[K.anim] = s.animations
            p[K.splash] = s.splash
            p[K.dim] = s.dim
            if (s.wallpaperUri != null) p[K.wallpaper] = s.wallpaperUri else p.remove(K.wallpaper)
            p[K.wWeather] = s.widgetWeather
            p[K.wVehicle] = s.widgetVehicle
            p[K.wConso] = s.widgetConsumption
            p[K.demo] = s.demoMode
            p[K.obd] = s.obdEnabled
            if (s.obdAddress != null) p[K.obdAddr] = s.obdAddress else p.remove(K.obdAddr)
            p[K.city] = s.weatherCity
            p[K.lat] = s.weatherLat
            p[K.lon] = s.weatherLon
            p[K.listen] = s.listenActions
            p[K.obdTransport] = s.obdTransport
            p[K.obdWifiHost] = s.obdWifiHost
            p[K.obdWifiPort] = s.obdWifiPort
            if (s.obdVerified != null) p[K.obdVerified] = s.obdVerified else p.remove(K.obdVerified)
            p[K.canMappings] = s.canMappings
            p[K.shortcuts] = s.shortcutOverrides
        }
    }
}
