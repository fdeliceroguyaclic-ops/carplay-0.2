package fr.polo.cockpit.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.Toast

data class InstalledApp(val label: String, val packageName: String, val icon: Drawable?)

/** Un raccourci de l'interface et les paquets candidats, testés dans l'ordre. */
data class Shortcut(
    val key: String,
    val label: String,
    val candidates: List<String>,
    val keywords: List<String> = emptyList(),
    val fallback: (() -> Intent)? = null,
)

/**
 * Lancement des applications existantes de l'autoradio.
 * On ne remplace ni ne modifie CarPlay/Android Auto : on ouvre simplement l'appli de projection
 * déjà installée (ZLink, TLink, AutoKit…), qui gère elle-même le protocole.
 */
class AppLauncher(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    val shortcuts = listOf(
        // L'appli de projection gère généralement CarPlay ET Android Auto sur ces autoradios.
        Shortcut("carplay", "CarPlay",
            listOf("com.zjinnova.zlink", "com.zjinnova.zlinka", "com.autokit.autokit", "com.ludashi.autokit"),
            keywords = listOf("zlink", "carplay", "autokit", "tlink", "carlink", "easyconn", "phonelink", "cplay")),
        Shortcut("androidauto", "Android Auto",
            listOf("com.zjinnova.zlink", "com.google.android.projection.gearhead"),
            keywords = listOf("zlink", "androidauto", "android auto", "carlink", "autokit", "tlink")),
        Shortcut("maps", "Google Maps", listOf("com.google.android.apps.maps")),
        Shortcut("waze", "Waze", listOf("com.waze")),
        Shortcut("spotify", "Spotify", listOf("com.spotify.music")),
        Shortcut("ytmusic", "YouTube Music", listOf("com.google.android.apps.youtube.music")),
        Shortcut("music", "Musique", listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
            keywords = listOf("music", "musique", "media", "player"),
            fallback = { Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC) }),
        Shortcut("phone", "Téléphone", emptyList(),
            keywords = listOf("bt", "bluetooth", "phone", "dial", "téléphone"),
            fallback = { Intent(Intent.ACTION_DIAL) }),
        Shortcut("bluetooth", "Bluetooth", emptyList(),
            keywords = listOf("bluetooth", "bt"),
            fallback = { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) }),
        Shortcut("gps", "Navigation", listOf("com.waze", "com.google.android.apps.maps"),
            keywords = listOf("navi", "gps", "map")),
        Shortcut("settings", "Paramètres système", emptyList(),
            fallback = { Intent(Settings.ACTION_SETTINGS) }),
    )

    fun installedApps(): List<InstalledApp> {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(main, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, runCatching { it.loadIcon(pm) }.getOrNull()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Détecte l'appli CarPlay installée (ZLink, TLink, AutoKit...). */
    fun detectProjectionApps(): List<InstalledApp> {
        val kw = shortcuts.first { it.key == "carplay" }.keywords
        return installedApps().filter { a -> kw.any { a.packageName.lowercase().contains(it) || a.label.lowercase().contains(it) } }
    }

    fun resolve(s: Shortcut, overrides: Map<String, String>): String? {
        overrides[s.key]?.let { if (isLaunchable(it)) return it }
        s.candidates.firstOrNull { isLaunchable(it) }?.let { return it }
        if (s.keywords.isNotEmpty()) {
            installedApps().firstOrNull { a ->
                s.keywords.any { a.packageName.lowercase().contains(it) || a.label.lowercase().contains(it) }
            }?.let { return it.packageName }
        }
        return null
    }

    private fun isLaunchable(pkg: String) = pm.getLaunchIntentForPackage(pkg) != null

    fun launch(key: String, overrides: Map<String, String>) {
        val s = shortcuts.firstOrNull { it.key == key } ?: return
        val pkg = resolve(s, overrides)
        val intent = pkg?.let { pm.getLaunchIntentForPackage(it) } ?: s.fallback?.invoke()
        if (intent == null) {
            toast("${s.label} : aucune application trouvée. Choisissez-la dans Réglages > Raccourcis.")
            return
        }
        start(intent)
    }

    fun launchPackage(pkg: String) {
        pm.getLaunchIntentForPackage(pkg)?.let { start(it) } ?: toast("Application introuvable")
    }

    /** Ouvre l'interface d'origine de l'autoradio (un autre écran d'accueil que le nôtre). */
    fun openOriginalLauncher() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val other = pm.queryIntentActivities(home, 0).firstOrNull { it.activityInfo.packageName != context.packageName }
        if (other == null) { toast("Interface d'origine introuvable"); return }
        start(Intent(home).setComponent(ComponentName(other.activityInfo.packageName, other.activityInfo.name)))
    }

    /** Active/désactive le mode « écran d'accueil » (aucun root nécessaire). */
    fun setHomeMode(enabled: Boolean) {
        val cn = ComponentName(context, "fr.polo.cockpit.HomeAlias")
        pm.setComponentEnabledSetting(cn,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
    }

    fun isHomeMode(): Boolean =
        pm.getComponentEnabledSetting(ComponentName(context, "fr.polo.cockpit.HomeAlias")) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun openHomeChooser() = start(Intent(Settings.ACTION_HOME_SETTINGS))

    fun uninstallSelf() = start(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${context.packageName}")))

    private fun start(i: Intent) {
        try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: ActivityNotFoundException) { toast("Impossible d'ouvrir cette application") }
        catch (e: SecurityException) { toast("Ouverture refusée par le système") }
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
