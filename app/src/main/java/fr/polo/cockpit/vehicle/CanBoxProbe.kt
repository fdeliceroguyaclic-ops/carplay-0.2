package fr.polo.cockpit.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Sonde de compatibilité CanBox / MCU — 100 % passive, sans root.
 *
 * La CanBox Raise parle à la MCU en série ; la MCU relaie les infos à Android via un service
 * propriétaire. La sonde observe uniquement ce qu'Android expose à une appli ordinaire :
 * paquets et composants déclarés, services système listables, fournisseurs de contenu lisibles,
 * chaînes présentes dans le code du fabricant, diffusions effectivement reçues, journaux système
 * (si l'autorisation a été accordée par ADB). Elle n'envoie rien à la MCU ni au bus CAN et
 * ne présente comme « observé » que ce qui l'a réellement été.
 */
class CanBoxProbe(private val context: Context) {

    private val _journal = MutableStateFlow<List<String>>(emptyList())
    val journal: StateFlow<List<String>> = _journal
    private val _captureStatus = MutableStateFlow<String?>(null)
    val captureStatus: StateFlow<String?> = _captureStatus

    private var receiver: BroadcastReceiver? = null
    /** Diffusions réellement reçues : action -> (nombre, dernier contenu). */
    private val observed = LinkedHashMap<String, Pair<Int, String>>()
    @Volatile var candidateActions: List<String> = emptyList(); private set

    private val vendorHints = listOf("can", "mcu", "car", "vehicle", "syu", "fyt", "jcst", "ujc", "raise", "rzc", "hzc",
        "autochips", "atc", "microntek", "mtc", "ts10", "ts18", "radio", "settings", "launcher", "bt", "bluetooth", "service")
    private val canWords = listOf("canbus", "canbox", "can_", "door", "handbrake", "radar", "fuel", "coolant", "carinfo",
        "car_info", "mcu", "speed", "rpm", "raise", "rzc")

    // ------------------------------------------------------------------ écoute des diffusions
    fun startListening(actions: List<String>, tag: String = "écoute") {
        stopListening()
        val clean = actions.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(400)
        if (clean.isEmpty()) return
        val filter = IntentFilter().apply { clean.forEach { addAction(it) } }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = record(intent)
        }
        // Une diffusion « collante » éventuelle est renvoyée immédiatement : on l'enregistre aussi.
        val sticky = runCatching { ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED) }
            .onFailure { log("Écoute impossible : ${it.message}") }.getOrNull()
        receiver = r
        sticky?.let { record(it) }
        log("Début $tag : ${clean.size} action(s) écoutée(s)")
    }

    fun stopListening() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun record(intent: Intent) {
        val action = intent.action ?: "(sans action)"
        val content = describeExtras(intent.extras)
        synchronized(observed) {
            val prev = observed[action]
            observed[action] = ((prev?.first ?: 0) + 1) to content
        }
        log("${intent.action} → $content")
    }

    private fun log(line: String) {
        _journal.value = (listOf("${time()} $line") + _journal.value).take(300)
    }

    /**
     * Capture guidée : écoute toutes les actions candidates pendant [seconds] secondes pendant que
     * l'utilisateur ouvre une portière, allume les feux, serre le frein à main…
     */
    suspend fun guidedCapture(extra: List<String>, seconds: Int = 60) {
        if (candidateActions.isEmpty()) {
            _captureStatus.value = "Recherche des actions candidates dans le code du fabricant…"
            withContext(Dispatchers.IO) { candidateActions = collectCandidates() }
        }
        val actions = (candidateActions + extra).distinct()
        if (actions.isEmpty()) {
            _captureStatus.value = "Aucune action candidate trouvée : la capture ne peut rien écouter. Générez le rapport pour voir pourquoi."
            return
        }
        synchronized(observed) { observed.clear() }
        startListening(actions, "capture guidée")
        for (s in seconds downTo 1) {
            _captureStatus.value = "Capture en cours ($s s) — ouvrez/fermez une portière, le coffre, allumez/éteignez les feux, serrez/desserrez le frein à main."
            delay(1000)
        }
        stopListening()
        val n = synchronized(observed) { observed.size }
        _captureStatus.value = if (n == 0)
            "Capture terminée : aucune diffusion reçue sur ${actions.size} actions écoutées. Le fabricant n'utilise probablement pas de diffusions publiques (voir le rapport)."
        else "Capture terminée : $n action(s) ont réellement émis des données. Elles figurent dans le rapport."
        log("Fin de la capture : $n action(s) observée(s)")
    }

    // ------------------------------------------------------------------ rapport
    suspend fun buildReport(onProgress: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        fun section(t: String) { sb.appendLine(); sb.appendLine("=== $t ===") }
        sb.appendLine("POLO DIGITAL COCKPIT — RAPPORT DE COMPATIBILITÉ CANBOX / MCU")
        sb.appendLine("Généré le ${Date()} — sonde passive, aucune commande envoyée au véhicule.")

        section("1. Système")
        sb.appendLine("Android réel : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        if (Build.VERSION.SDK_INT < 31) sb.appendLine("NOTE : l'autoradio annonce « Android 12 », mais le système exécute en réalité l'API ${Build.VERSION.SDK_INT}.")
        sb.appendLine("Fabricant=${Build.MANUFACTURER} Marque=${Build.BRAND} Modèle=${Build.MODEL} Produit=${Build.PRODUCT}")
        sb.appendLine("Board=${Build.BOARD} Hardware=${Build.HARDWARE} ABI=${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("Empreinte=${Build.FINGERPRINT}")
        sb.appendLine("Affichage=${Build.DISPLAY}")

        onProgress("Propriétés système…")
        section("2. Propriétés système utiles (getprop, filtrées)")
        val props = exec(listOf("getprop"), 3000)
        if (props.first == null) sb.appendLine("getprop indisponible : ${props.second}")
        else props.first!!.lines().filter { l ->
            listOf("mcu", "can", "car", "platform", "version", "product", "sys.fyt", "persist.sys", "ro.build", "vendor", "raise", "bt")
                .any { l.contains(it, ignoreCase = true) }
        }.take(500).forEach { sb.appendLine(it) }

        onProgress("Services système…")
        section("3. Services système (service list)")
        val svc = exec(listOf("service", "list"), 4000)
        if (svc.first == null || svc.first!!.isBlank()) sb.appendLine("Non accessible à une appli (${svc.second ?: "sortie vide"}).")
        else {
            val lines = svc.first!!.lines()
            sb.appendLine("${lines.size} services listés. Services évoquant le véhicule :")
            lines.filter { l -> canWords.any { l.contains(it, true) } || listOf("car", "vehicle", "auto").any { l.contains(it, true) } }
                .forEach { sb.appendLine("  $it") }
            sb.appendLine("Liste complète :")
            lines.forEach { sb.appendLine("  $it") }
        }

        onProgress("Applications du fabricant…")
        val pm = context.packageManager
        val all = installedPackages(pm)
        val vendor = vendorPackages(all)
        section("4. Applications système du fabricant (${vendor.size})")
        for (pi in vendor) {
            val ai = pi.applicationInfo
            sb.appendLine("* ${pi.packageName} [${ai?.loadLabel(pm)}] v${pi.versionName} uid=${ai?.uid} sharedUser=${pi.sharedUserId ?: "-"}")
            sb.appendLine("    fichier=${ai?.sourceDir} lisible=${ai?.sourceDir?.let { File(it).canRead() }}")
            pi.permissions?.forEach { sb.appendLine("    permission déclarée ${it.name} niveau=${it.protectionLevel}") }
            pi.services?.forEach { sb.appendLine("    service  ${it.name} exporté=${it.exported} permission=${it.permission ?: "aucune"}") }
            pi.providers?.forEach { sb.appendLine("    provider ${it.name} authority=${it.authority} exporté=${it.exported} lecture=${it.readPermission ?: "libre"}") }
            pi.receivers?.forEach { sb.appendLine("    receiver ${it.name} exporté=${it.exported} permission=${it.permission ?: "aucune"}") }
        }

        onProgress("Fournisseurs de contenu…")
        section("5. Fournisseurs de contenu du fabricant : test de lecture (requête simple, lecture seule)")
        var readable = 0
        for (pi in vendor) for (p in pi.providers.orEmpty()) {
            if (!p.exported || p.authority == null) continue
            for (auth in p.authority.split(';')) {
                val res = queryProvider(auth)
                if (res.startsWith("LISIBLE")) readable++
                sb.appendLine("content://$auth → $res")
            }
        }
        if (readable == 0) sb.appendLine("Aucun fournisseur exporté n'a renvoyé de données.")

        onProgress("Analyse du code du fabricant…")
        section("6. Chaînes liées au CAN dans le code du fabricant (APK + fichiers précompilés .vdex/.odex)")
        val cands = LinkedHashSet<String>()
        for (pi in vendor.take(40)) {
            val ai = pi.applicationInfo ?: continue
            onProgress("Analyse ${pi.packageName}…")
            val files = codeFiles(ai)
            if (files.isEmpty()) { sb.appendLine("# ${pi.packageName} : aucun fichier de code lisible"); continue }
            val strings = LinkedHashSet<String>()
            for (f in files) strings += scanStrings(f)
            val canRelated = strings.filter { s -> canWords.any { s.contains(it, true) } }
            val actions = strings.filter { ActionCandidates.isCandidate(it) }
            cands += actions
            if (canRelated.isNotEmpty() || actions.isNotEmpty()) {
                sb.appendLine("# ${pi.packageName} — fichiers : ${files.joinToString { it.name }}")
                if (actions.isNotEmpty()) { sb.appendLine("  Actions candidates (${actions.size}) :"); actions.take(200).forEach { sb.appendLine("    $it") } }
                sb.appendLine("  Autres chaînes liées au CAN (${canRelated.size}) :")
                canRelated.take(250).forEach { sb.appendLine("    $it") }
            }
        }
        candidateActions = cands.toList()

        onProgress("Journaux système…")
        section("7. Journaux système (logcat)")
        val readLogs = context.checkSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED
        sb.appendLine("Autorisation READ_LOGS : ${if (readLogs) "accordée" else "non accordée (seuls les journaux de l'appli sont visibles)"}")
        val lc = exec(listOf("logcat", "-d", "-v", "time", "-t", "4000"), 6000)
        if (lc.first == null) sb.appendLine("logcat indisponible : ${lc.second}")
        else {
            val hits = lc.first!!.lines().filter { l -> canWords.any { l.contains(it, true) } && !l.contains("fr.polo.cockpit") }
            sb.appendLine("${hits.size} ligne(s) liées au CAN trouvées :")
            hits.takeLast(300).forEach { sb.appendLine("  $it") }
        }

        section("8. Diffusions réellement reçues (capture guidée / écoute)")
        val obs = synchronized(observed) { observed.toMap() }
        if (obs.isEmpty()) sb.appendLine("Aucune diffusion reçue pour l'instant. Lancez la « capture guidée » puis régénérez ce rapport.")
        obs.forEach { (a, v) -> sb.appendLine("$a — reçue ${v.first} fois — dernier contenu : ${v.second}") }
        sb.appendLine("Journal :"); _journal.value.take(150).forEach { sb.appendLine("  $it") }

        section("9. Bluetooth (pour l'OBD2)")
        val bt = ObdLinks.diagnose(context)
        sb.appendLine("Adaptateur présent=${bt.adapterPresent} activé=${bt.enabled} autorisation=${bt.permissionOk}")
        sb.appendLine(bt.message)
        bt.devices.forEach { sb.appendLine("  ${it.name} ${it.address} (${it.kind})") }

        section("10. Dernier plantage de l'appli")
        sb.appendLine(CrashLog.read(context) ?: "Aucun.")

        section("11. Conclusion automatique (faits observés uniquement)")
        sb.appendLine("- Applis du fabricant candidates : ${vendor.size}")
        sb.appendLine("- Services système listables : ${if (svc.first.isNullOrBlank()) "non" else "oui"}")
        sb.appendLine("- Fournisseurs de contenu lisibles : $readable")
        sb.appendLine("- Actions de diffusion candidates trouvées dans le code : ${cands.size}")
        sb.appendLine("- Actions ayant réellement émis pendant la capture : ${obs.size}")
        sb.appendLine("- Journaux système des autres applis lisibles : ${if (readLogs) "oui" else "non"}")
        sb.appendLine()
        sb.appendLine("Si aucune donnée n'a été observée, les éléments suivants permettront d'aller plus loin :")
        sb.appendLine("  a) le fichier ZIP « applis du fabricant » (bouton Exporter les applis CAN) pour analyse du code ;")
        sb.appendLine("  b) l'autorisation des journaux, accordée une fois depuis un PC : adb shell pm grant fr.polo.cockpit android.permission.READ_LOGS");
        sb.appendLine("     puis une nouvelle capture guidée et un nouveau rapport ;")
        sb.appendLine("  c) des photos des écrans d'origine qui affichent des infos véhicule (popup portières, page CanBus).")
        sb.toString()
    }

    /** Liste des actions candidates (sans produire de rapport). */
    private fun collectCandidates(): List<String> {
        val pm = context.packageManager
        val out = LinkedHashSet<String>()
        for (pi in vendorPackages(installedPackages(pm)).take(40)) {
            val ai = pi.applicationInfo ?: continue
            for (f in codeFiles(ai)) out += scanStrings(f).filter { ActionCandidates.isCandidate(it) }
        }
        return out.toList()
    }

    /** Copie les APK et fichiers précompilés des applis du fabricant liées au CAN dans un ZIP (pour analyse). */
    fun exportVendorCode(target: Uri): String {
        val pm = context.packageManager
        val vendor = vendorPackages(installedPackages(pm)).filter { pi ->
            val n = pi.packageName.lowercase()
            listOf("can", "mcu", "car", "vehicle", "syu", "fyt", "jcst", "ujc", "raise", "settings", "service").any { it in n }
        }
        var count = 0; var bytes = 0L
        val os = context.contentResolver.openOutputStream(target) ?: error("impossible d'ouvrir le fichier de destination")
        ZipOutputStream(os).use { zip ->
            for (pi in vendor) {
                val ai = pi.applicationInfo ?: continue
                for (f in codeFiles(ai)) {
                    if (f.length() > 80L * 1024 * 1024) continue
                    runCatching {
                        zip.putNextEntry(ZipEntry("${pi.packageName}/${f.name}"))
                        f.inputStream().use { bytes += it.copyTo(zip) }
                        zip.closeEntry(); count++
                    }
                }
            }
        }
        return "$count fichier(s) exporté(s) depuis ${vendor.size} appli(s), ${bytes / 1024} Ko"
    }

    // ------------------------------------------------------------------ outils
    private fun installedPackages(pm: PackageManager): List<PackageInfo> {
        val flags = PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS
        // Avec beaucoup de composants, l'appel groupé peut dépasser la taille du binder : repli paquet par paquet.
        return runCatching { pm.getInstalledPackages(flags) }.getOrElse {
            pm.getInstalledPackages(0).mapNotNull { p -> runCatching { pm.getPackageInfo(p.packageName, flags) }.getOrNull() }
        }
    }

    private fun vendorPackages(all: List<PackageInfo>): List<PackageInfo> = all.filter { pi ->
        val ai = pi.applicationInfo ?: return@filter false
        val sys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val n = pi.packageName.lowercase()
        sys && !n.startsWith("com.google.") && !n.startsWith("com.android.providers") && n != context.packageName &&
            vendorHints.any { it in n }
    }

    /** APK et fichiers .vdex/.odex associés (le code des applis système est souvent précompilé). */
    private fun codeFiles(ai: ApplicationInfo): List<File> {
        val out = mutableListOf<File>()
        val apk = ai.sourceDir?.let { File(it) } ?: return out
        if (apk.canRead()) out += apk
        val oat = File(apk.parentFile, "oat")
        oat.listFiles()?.forEach { arch -> arch.listFiles()?.filter { (it.name.endsWith(".vdex") || it.name.endsWith(".odex")) && it.canRead() }?.let { out += it } }
        return out
    }

    /** Extrait les chaînes ASCII imprimables d'un fichier (dex dans un APK, ou vdex/odex brut). */
    private fun scanStrings(f: File): Set<String> {
        val out = LinkedHashSet<String>()
        fun scan(ins: InputStream) {
            val buf = ByteArray(64 * 1024)
            val cur = StringBuilder()
            var total = 0L
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                total += n
                for (i in 0 until n) {
                    val c = buf[i].toInt() and 0xFF
                    if (c in 0x20..0x7E) { if (cur.length < 200) cur.append(c.toChar()) }
                    else {
                        if (cur.length in 6..160 && !cur.contains(' ')) out += cur.toString()
                        cur.setLength(0)
                    }
                }
                if (total > 60L * 1024 * 1024 || out.size > 200_000) break
            }
        }
        runCatching {
            if (f.name.endsWith(".apk")) ZipFile(f).use { z ->
                z.entries().asSequence().filter { it.name.endsWith(".dex") }.take(8).forEach { e -> z.getInputStream(e).use { scan(it) } }
            } else f.inputStream().use { scan(it) }
        }
        return out
    }

    /** Requête de lecture simple, limitée à 3 s. */
    private fun queryProvider(authority: String): String {
        var result = "pas de réponse en 3 s"
        val t = Thread {
            result = try {
                context.contentResolver.query(Uri.parse("content://$authority"), null, null, null, null).use { c ->
                    if (c == null) "aucun curseur (refus ou URI racine non gérée)"
                    else {
                        val cols = c.columnNames.joinToString()
                        val rows = StringBuilder()
                        var i = 0
                        while (c.moveToNext() && i < 3) {
                            rows.append(" | ").append((0 until c.columnCount).joinToString(",") { k -> runCatching { c.getString(k) }.getOrNull()?.take(40) ?: "?" })
                            i++
                        }
                        "LISIBLE : ${c.count} ligne(s), colonnes [$cols]$rows"
                    }
                }
            } catch (e: Throwable) { "refusé : ${e.javaClass.simpleName} ${e.message?.take(120) ?: ""}" }
        }
        t.isDaemon = true; t.start(); t.join(3000)
        return result
    }

    private fun exec(cmd: List<String>, timeoutMs: Long): Pair<String?, String?> = try {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        var text = ""
        val reader = Thread { text = runCatching { p.inputStream.bufferedReader().readText() }.getOrDefault("") }
        reader.isDaemon = true; reader.start(); reader.join(timeoutMs)
        runCatching { p.destroy() }
        if (text.contains("Permission denied", true) && text.lines().size < 5) null to text.trim() else text to null
    } catch (e: Throwable) { null to (e.message ?: e.javaClass.simpleName) }

    private fun describeExtras(b: Bundle?): String {
        if (b == null) return "(aucun extra)"
        return runCatching { b.keySet().joinToString { k -> "$k=${extraValue(b, k)}" } }.getOrElse { "(extras illisibles : ${it.message})" }
    }

    @Suppress("DEPRECATION")
    private fun extraValue(b: Bundle, k: String): String = when (val v = b.get(k)) {
        is ByteArray -> v.joinToString(" ") { "%02X".format(it) }
        is IntArray -> v.joinToString(",")
        is Array<*> -> v.joinToString(",")
        else -> v?.toString() ?: "null"
    }

    private fun time() = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
}
