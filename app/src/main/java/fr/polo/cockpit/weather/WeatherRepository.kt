package fr.polo.cockpit.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Weather(val tempC: Int, val code: Int, val city: String) {
    val description: String get() = when (code) {
        0 -> "Ciel dégagé"
        1, 2 -> "Partiellement nuageux"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        in 51..57 -> "Bruine"
        in 61..67, in 80..82 -> "Pluie"
        in 71..77, 85, 86 -> "Neige"
        in 95..99 -> "Orage"
        else -> "—"
    }
}

/** Météo via Open-Meteo : gratuit, sans clé, sans compte. Optionnelle (hors ligne = masquée). */
class WeatherRepository {
    suspend fun fetch(lat: Float, lon: Float, city: String): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code",
                lat, lon))
            val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
            c.inputStream.bufferedReader().use { r ->
                val cur = JSONObject(r.readText()).getJSONObject("current")
                Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), city)
            }.also { c.disconnect() }
        }.getOrNull()
    }
}
