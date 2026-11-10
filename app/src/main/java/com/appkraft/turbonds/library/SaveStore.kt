package com.appkraft.turbonds.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Per-game saves in private storage, keyed by the game's stable id (game code + header CRC), so
 * an exported save matches the same game on another phone. Layout of `saves/<id>/`:
 * - `battery.sav` — the in-game save, with `battery.sav.bak1..3` kept from earlier sessions
 * - `autosave.state` — written when leaving or backgrounding a game; "Continue" resumes it
 * - `slot1.state` … `slot6.state` — manual save states
 */
class SaveStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "saves").apply { mkdirs() }

    fun folder(id: String): File = File(root, safe(id)).apply { mkdirs() }

    fun hasContinue(id: String): Boolean = File(File(root, safe(id)), AUTOSAVE).isFile

    fun hasAnySave(id: String): Boolean = File(root, safe(id)).listFiles().orEmpty().isNotEmpty()

    /** Drops the resume point so the game boots fresh (the in-game save is kept). */
    fun clearContinue(id: String) {
        File(File(root, safe(id)), AUTOSAVE).delete()
    }

    /** Deletes every save of this game, including in-game progress. */
    fun deleteAll(id: String) {
        File(root, safe(id)).deleteRecursively()
    }

    /** Keeps the last three in-game saves: called once each time a game starts. */
    fun rotateBackups(id: String) {
        val folder = folder(id)
        val current = File(folder, BATTERY)
        if (!current.isFile || current.length() == 0L) return
        val newest = File(folder, "$BATTERY.bak1")
        if (newest.isFile && newest.readBytes().contentEquals(current.readBytes())) return
        File(folder, "$BATTERY.bak3").delete()
        File(folder, "$BATTERY.bak2").takeIf { it.isFile }?.renameTo(File(folder, "$BATTERY.bak3"))
        newest.takeIf { it.isFile }?.renameTo(File(folder, "$BATTERY.bak2"))
        current.copyTo(newest, overwrite = true)
    }

    /**
     * Earlier copies of the in-game save, newest first: the last three sessions, the one kept
     * before a state was loaded and the one kept before a backup import.
     */
    fun backups(id: String): List<File> {
        val folder = File(root, safe(id))
        val current = File(folder, BATTERY).takeIf { it.isFile }?.readBytes()
        return BACKUP_NAMES.map { File(folder, it) }
            .filter { it.isFile && it.length() > 0 }
            .distinctBy { it.readBytes().contentHashCode() }
            .filter { current == null || !it.readBytes().contentEquals(current) }
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Makes [backup] the in-game save again. The current save is kept as `battery.sav.before-restore`
     * and the resume point is dropped, so the next start really uses the restored save.
     */
    fun restore(id: String, backup: File) {
        val folder = folder(id)
        require(backup.parentFile == folder && backup.isFile) { "That save is no longer there." }
        val current = File(folder, BATTERY)
        if (current.isFile) current.copyTo(File(folder, "$BATTERY.before-restore"), overwrite = true)
        val pending = File(folder, "$BATTERY.tmp")
        backup.copyTo(pending, overwrite = true)
        check(pending.renameTo(current)) { "The save could not be restored." }
        File(folder, AUTOSAVE).delete()
    }

    /** Copies everything into one .zip at [destination] (a document the user picked). */
    suspend fun exportAll(context: Context, destination: Uri): Int = withContext(Dispatchers.IO) {
        var files = 0
        context.contentResolver.openOutputStream(destination, "w")?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                    zip.putNextEntry(ZipEntry("TurboNDS-saves/" + file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
        } ?: error("The backup file could not be created.")
        files
    }

    /**
     * Restores a backup made by [exportAll]. Existing saves of the same game are kept as
     * `*.before-import` so an import can never silently destroy progress.
     */
    suspend fun importAll(context: Context, source: Uri): Int = withContext(Dispatchers.IO) {
        var files = 0
        context.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name.removePrefix("TurboNDS-saves/")
                    if (entry.isDirectory || path.contains("..") || path.count { it == '/' } != 1) continue
                    val (game, name) = path.split('/')
                    if (name.isBlank() || name.endsWith(".tmp")) continue
                    val target = File(folder(game), name)
                    if (target.isFile) target.copyTo(File(target.parentFile, "$name.before-import"), overwrite = true)
                    target.outputStream().use { zip.copyTo(it) }
                    files++
                }
            }
        } ?: error("The backup file could not be opened.")
        files
    }

    companion object {
        const val BATTERY = "battery.sav"
        const val AUTOSAVE = "autosave.state"
        const val SLOTS = 6
        private val BACKUP_NAMES = listOf("bak1", "bak2", "bak3", "before-load", "before-import", "before-restore").map { "$BATTERY.$it" }

        /** Folder-safe id (ids are letters, digits and dashes already; this is a guard). */
        fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
