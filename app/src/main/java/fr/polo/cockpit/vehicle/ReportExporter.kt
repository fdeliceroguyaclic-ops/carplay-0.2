package fr.polo.cockpit.vehicle

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enregistrement du rapport compatible Android 8 à 14, sans MediaStore.Downloads (API 29+)
 * ni autorisation de stockage :
 *  1. voie principale : l'utilisateur choisit l'emplacement (ACTION_CREATE_DOCUMENT, API 19+) -> [writeToUri] ;
 *  2. secours si l'autoradio n'a pas de sélecteur de fichiers : dossier propre à l'appli
 *     Android/data/fr.polo.cockpit/files/rapports (API 8+) -> [writeFallback].
 * Chaque écriture est relue pour vérifier que le fichier existe et contient tout le texte.
 */
object ReportExporter {
    fun suggestedName(prefix: String = "polo_cockpit_rapport", ext: String = "txt"): String =
        "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())}.$ext"

    fun writeToUri(ctx: Context, uri: Uri, text: String): String {
        val data = text.toByteArray(Charsets.UTF_8)
        val os = ctx.contentResolver.openOutputStream(uri, "w") ?: error("le système n'a pas ouvert le fichier choisi")
        os.use { it.write(data); it.flush() }
        val back = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        return verify(data, back, uri.lastPathSegment ?: uri.toString())
    }

    fun writeFallback(ctx: Context, text: String): String {
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val dir = File(base, "rapports").apply { mkdirs() }
        val f = File(dir, suggestedName())
        val data = text.toByteArray(Charsets.UTF_8)
        f.writeBytes(data)
        return verify(data, f.readBytes(), f.absolutePath)
    }

    private fun verify(written: ByteArray, readBack: ByteArray?, where: String): String {
        if (readBack == null) error("fichier écrit mais impossible à relire : $where")
        if (!readBack.contentEquals(written)) error("fichier incomplet (${readBack.size}/${written.size} octets) : $where")
        val lines = String(readBack, Charsets.UTF_8).lines().size
        return "Rapport vérifié : ${readBack.size / 1024} Ko, $lines lignes — $where"
    }
}

/** Mémorise le dernier plantage pour l'afficher et l'inclure dans le rapport. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "dernier_plantage.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                file(app).writeText("${Date()} — fil ${t.name}\nAndroid API ${android.os.Build.VERSION.SDK_INT}\n$sw")
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText()?.take(6000) }.getOrNull()
    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
}
