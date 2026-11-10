package com.appkraft.turbonds.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** One game the user owns, found in a folder they chose. */
data class GameEntry(
    val id: String,
    val uri: String,
    val fileName: String,
    val size: Long,
    val title: String,
    val subtitle: String = "",
    val gameCode: String = "",
    val hasIcon: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayed: Long = 0,
    val playTimeMs: Long = 0,
    val favorite: Boolean = false
) {
    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()
    val isArchive: Boolean get() = extension in NdsHeader.archiveExtensions

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("uri", uri).put("fileName", fileName).put("size", size)
        .put("title", title).put("subtitle", subtitle).put("gameCode", gameCode).put("hasIcon", hasIcon)
        .put("addedAt", addedAt).put("lastPlayed", lastPlayed).put("playTimeMs", playTimeMs).put("favorite", favorite)

    companion object {
        fun fromJson(json: JSONObject) = GameEntry(
            id = json.getString("id"), uri = json.getString("uri"), fileName = json.getString("fileName"),
            size = json.optLong("size"), title = json.optString("title"), subtitle = json.optString("subtitle"),
            gameCode = json.optString("gameCode"), hasIcon = json.optBoolean("hasIcon"),
            addedAt = json.optLong("addedAt"), lastPlayed = json.optLong("lastPlayed"),
            playTimeMs = json.optLong("playTimeMs"), favorite = json.optBoolean("favorite")
        )
    }
}

/** A game file that could not be opened, with a reason the user can act on. */
class GameFileProblem(message: String) : Exception(message)

/**
 * The library: folders the user granted (Storage Access Framework, no storage permission),
 * the games found in them, their icons, and the private copy of a game the player loads.
 * Saves live apart from the library and are never touched by a rescan or removal.
 */
class GameStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val indexFile = File(app.filesDir, "library.json")
    private val iconDir = File(app.filesDir, "icons").apply { mkdirs() }
    private val romDir = File(app.filesDir, "roms").apply { mkdirs() }

    fun folders(): List<Uri> = prefs.getStringSet("folders", emptySet()).orEmpty().sorted().map(Uri::parse)

    /** Single game files the user picked one by one (works anywhere, also in Download). */
    fun files(): List<Uri> = prefs.getStringSet("files", emptySet()).orEmpty().sorted().map(Uri::parse)

    /** Keeps read access to picked files across restarts. Returns how many were added. */
    fun addFiles(uris: List<Uri>): Int {
        val kept = uris.filter { uri ->
            runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
        }
        prefs.edit().putStringSet("files", prefs.getStringSet("files", emptySet()).orEmpty() + kept.map { it.toString() }).apply()
        return kept.size
    }

    private fun forgetFile(uri: String) {
        if (uri !in prefs.getStringSet("files", emptySet()).orEmpty()) return
        runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putStringSet("files", prefs.getStringSet("files", emptySet()).orEmpty() - uri).apply()
    }

    fun addFolder(tree: Uri) {
        app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putStringSet("folders", prefs.getStringSet("folders", emptySet()).orEmpty() + tree.toString()).apply()
    }

    /** Stops watching a folder; its games leave the library on the next scan. Saves are kept. */
    fun removeFolder(tree: Uri) {
        runCatching { app.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putStringSet("folders", prefs.getStringSet("folders", emptySet()).orEmpty() - tree.toString()).apply()
    }

    fun folderLabel(tree: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "Internal storage" } }.getOrDefault(tree.toString())

    // ---- Index -----------------------------------------------------------------------------

    @Synchronized
    fun load(): List<GameEntry> = runCatching {
        if (!indexFile.isFile) return emptyList()
        val array = JSONArray(indexFile.readText())
        List(array.length()) { GameEntry.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(entries: List<GameEntry>) {
        val array = JSONArray().apply { entries.forEach { put(it.toJson()) } }
        val pending = File(indexFile.parentFile, indexFile.name + ".tmp")
        pending.writeText(array.toString())
        pending.renameTo(indexFile)
    }

    fun update(id: String, change: (GameEntry) -> GameEntry): List<GameEntry> {
        val updated = load().map { if (it.id == id) change(it) else it }
        save(updated)
        return updated
    }

    fun remove(id: String): List<GameEntry> {
        load().firstOrNull { it.id == id }?.let { forgetFile(it.uri) }
        val updated = load().filterNot { it.id == id }
        save(updated)
        File(romDir, "$id.nds").delete()
        File(iconDir, "$id.png").delete()
        return updated
    }

    fun iconFile(id: String): File = File(iconDir, "$id.png")

    // ---- Scanning --------------------------------------------------------------------------

    /**
     * Walks every granted folder (a few levels deep) and refreshes the library. Known files are
     * kept as they are (with play time and favourites); new ones are read once for their icon.
     */
    suspend fun scan(onFound: (Int) -> Unit = {}): List<GameEntry> = withContext(Dispatchers.IO) {
        val previous = load()
        val byUri = previous.associateBy { it.uri }
        val found = linkedMapOf<String, GameEntry>()
        var count = 0
        for (tree in folders()) {
            val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            walk(tree, root, depth = 0) { uri, name, size ->
                coroutineContext.ensureActive()
                val known = byUri[uri.toString()]
                val entry = if (known != null && known.size == size) known else describe(uri, name, size)?.let { fresh ->
                    // Keep play history if the same game moved or was renamed.
                    previous.firstOrNull { it.id == fresh.id }?.let { old ->
                        fresh.copy(lastPlayed = old.lastPlayed, playTimeMs = old.playTimeMs, favorite = old.favorite, addedAt = old.addedAt)
                    } ?: fresh
                }
                if (entry != null && entry.id !in found) {
                    found[entry.id] = entry
                    onFound(++count)
                }
            }
        }
        for (file in files()) {
            coroutineContext.ensureActive()
            val meta = runCatching {
                app.contentResolver.query(file, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) (cursor.getString(0) ?: "") to cursor.getLong(1) else null
                }
            }.getOrNull() ?: continue // permission lost or file deleted
            val (name, size) = meta
            if (name.substringAfterLast('.', "").lowercase() !in NdsHeader.allExtensions) continue
            val known = byUri[file.toString()]
            val entry = if (known != null && known.size == size) known else describe(file, name, size)?.let { fresh ->
                previous.firstOrNull { it.id == fresh.id }?.let { old ->
                    fresh.copy(lastPlayed = old.lastPlayed, playTimeMs = old.playTimeMs, favorite = old.favorite, addedAt = old.addedAt)
                } ?: fresh
            }
            if (entry != null && entry.id !in found) {
                found[entry.id] = entry
                onFound(++count)
            }
        }
        val result = found.values.toList()
        save(result)
        result
    }

    private suspend fun walk(tree: Uri, documentId: String, depth: Int, visit: suspend (Uri, String, Long) -> Unit) {
        if (depth > 5) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val rows = mutableListOf<Triple<String, String, Pair<String, Long>>>()
        runCatching {
            app.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    rows += Triple(cursor.getString(0), cursor.getString(1) ?: "", (cursor.getString(2) ?: "") to cursor.getLong(3))
                }
            }
        }
        for ((id, name, meta) in rows) {
            val (mime, size) = meta
            when {
                mime == DocumentsContract.Document.MIME_TYPE_DIR -> if (!name.startsWith('.')) walk(tree, id, depth + 1, visit)
                name.startsWith('.') -> Unit
                name.substringAfterLast('.', "").lowercase() in NdsHeader.allExtensions ->
                    visit(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, size)
            }
        }
    }

    /** Reads the header and banner of a game file (also inside a .zip) to name it and draw its icon. */
    private fun describe(uri: Uri, name: String, size: Long): GameEntry? {
        val extension = name.substringAfterLast('.', "").lowercase()
        val info: NdsInfo? = runCatching {
            when (extension) {
                in NdsHeader.gameExtensions -> readFromFile(uri)
                "zip" -> readFromZip(uri)
                else -> null // .7z headers need the whole archive decoded; the file name is used instead.
            }
        }.getOrNull()
        if (info == null && extension in NdsHeader.gameExtensions) return null // not a DS game after all
        val id = info?.stableId ?: "F-" + sha1("$name:$size").take(12)
        val icon = info?.iconPixels?.let { pixels ->
            runCatching {
                val bitmap = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
                iconFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
        } ?: false
        return GameEntry(
            id = id, uri = uri.toString(), fileName = name, size = size,
            title = info?.title?.takeIf { it.isNotBlank() } ?: NdsHeader.titleFromFileName(name),
            subtitle = info?.subtitle.orEmpty(), gameCode = info?.gameCode.orEmpty(), hasIcon = icon
        )
    }

    private fun readFromFile(uri: Uri): NdsInfo? =
        app.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                val header = ByteBuffer.allocate(NdsHeader.HEADER_SIZE)
                channel.read(header, 0)
                val info = NdsHeader.parseHeader(header.array()) ?: return null
                if (info.bannerOffset <= 0 || info.bannerOffset + NdsHeader.BANNER_SIZE > channel.size()) return info
                val banner = ByteBuffer.allocate(NdsHeader.BANNER_SIZE)
                channel.read(banner, info.bannerOffset)
                NdsHeader.withBanner(info, banner.array())
            }
        }

    private fun readFromZip(uri: Uri): NdsInfo? =
        app.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    val inner = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || inner.startsWith('.') || inner.substringAfterLast('.', "").lowercase() !in NdsHeader.gameExtensions) continue
                    val header = zip.readExactly(NdsHeader.HEADER_SIZE) ?: return null
                    val info = NdsHeader.parseHeader(header) ?: return null
                    // Only decompress up to the banner when it sits early in the file.
                    val skip = info.bannerOffset - NdsHeader.HEADER_SIZE
                    if (info.bannerOffset <= 0 || skip > 48L * 1024 * 1024) return info
                    if (!zip.skipFully(skip)) return info
                    val banner = zip.readExactly(NdsHeader.BANNER_SIZE) ?: return info
                    return NdsHeader.withBanner(info, banner)
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        }

    // ---- Preparing a game for the player ----------------------------------------------------

    /** The private copy the core loads (cores need a real file path, not a content URI). */
    fun preparedFile(id: String): File = File(romDir, "$id.nds")

    /**
     * Copies (or extracts) the game into private storage once; later plays start instantly.
     * Older copies are trimmed so the cache stays within [CACHE_LIMIT] bytes.
     */
    suspend fun prepare(entry: GameEntry, progress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val target = preparedFile(entry.id)
        if (target.isFile && target.length() > NdsHeader.HEADER_SIZE) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        val uri = Uri.parse(entry.uri)
        val pending = File(romDir, "${entry.id}.part")
        pending.delete()
        try {
            when (entry.extension) {
                "zip" -> extractZip(uri, entry.size, pending, progress)
                "7z" -> extract7z(uri, pending, progress)
                else -> {
                    requireSpace(entry.size)
                    copyStream(app.contentResolver.openInputStream(uri) ?: throw GameFileProblem("The game file could not be opened. Check that it is still in its folder."),
                        pending, entry.size, progress)
                }
            }
            val header = pending.inputStream().use { it.readExactly(NdsHeader.HEADER_SIZE) }
            if (header == null || NdsHeader.parseHeader(header) == null) throw GameFileProblem("This file is not a Nintendo DS game, or it is damaged.")
            if (NdsHeader.usedSize(header) > pending.length()) throw GameFileProblem("This game file is incomplete. Download it again, then rescan.")
            check(pending.renameTo(target)) { "The game could not be saved on this phone." }
            trimCache(keep = entry.id)
            target
        } catch (problem: Exception) {
            pending.delete()
            throw when (problem) {
                is GameFileProblem -> problem
                is SecurityException -> GameFileProblem("Turbo NDS can no longer read this folder. Add the folder again in Settings › Game folders.")
                is java.io.FileNotFoundException -> GameFileProblem("The game file was moved or deleted. Rescan your folders.")
                else -> GameFileProblem(problem.message ?: "The game could not be prepared. Please try again.")
            }
        }
    }

    private suspend fun extractZip(uri: Uri, archiveSize: Long, output: File, progress: (Float?) -> Unit) {
        app.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: throw GameFileProblem("This .zip has no .nds game inside.")
                    val inner = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || inner.startsWith('.') || inner.substringAfterLast('.', "").lowercase() !in NdsHeader.gameExtensions) continue
                    val expected = entry.size.takeIf { it > 0 } ?: (archiveSize * 2)
                    requireSpace(expected)
                    copyStream(zip, output, expected, progress, closeInput = false)
                    return
                }
            }
        } ?: throw GameFileProblem("The game file could not be opened.")
    }

    private suspend fun extract7z(uri: Uri, output: File, progress: (Float?) -> Unit) {
        val descriptor = app.contentResolver.openFileDescriptor(uri, "r") ?: throw GameFileProblem("The game file could not be opened.")
        descriptor.use {
            val channel = FileInputStream(it.fileDescriptor).channel
            try {
                SevenZFile.builder().setSeekableByteChannel(channel).setMaxMemoryLimitKb(SEVEN_Z_MEMORY_KB).get().use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: throw GameFileProblem("This .7z has no .nds game inside.")
                        val inner = entry.name.substringAfterLast('/')
                        if (entry.isDirectory || inner.startsWith('.') || inner.substringAfterLast('.', "").lowercase() !in NdsHeader.gameExtensions) continue
                        requireSpace(entry.size)
                        copyStream(archive.getInputStream(entry), output, entry.size, progress, closeInput = false)
                        return
                    }
                }
            } catch (_: org.apache.commons.compress.MemoryLimitException) {
                throw GameFileProblem("This .7z needs more memory than this phone can safely use. Extract it on a computer and add the .nds file.")
            }
        }
    }

    private suspend fun copyStream(input: InputStream, output: File, expected: Long, progress: (Float?) -> Unit, closeInput: Boolean = true) {
        val buffer = ByteArray(1 shl 16)
        var copied = 0L
        var lastReport = 0L
        try {
            output.outputStream().use { out ->
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    if (copied - lastReport > 1 shl 20) {
                        lastReport = copied
                        progress(if (expected > 0) (copied.toFloat() / expected).coerceIn(0f, 1f) else null)
                    }
                }
                out.fd.sync()
            }
        } finally {
            if (closeInput) input.close()
        }
        progress(1f)
    }

    private fun requireSpace(bytes: Long) {
        if (app.filesDir.usableSpace < bytes + 150L * 1024 * 1024) {
            trimCache(keep = null)
            if (app.filesDir.usableSpace < bytes + 150L * 1024 * 1024) {
                throw GameFileProblem("Not enough free space on this phone. Free up ${(bytes / (1024 * 1024)) + 150} MB and try again.")
            }
        }
    }

    /** Keeps the most recently played copies within the limit; saves are never touched. */
    private fun trimCache(keep: String?) {
        val copies = romDir.listFiles { file -> file.extension == "nds" }.orEmpty().sortedByDescending { it.lastModified() }
        var total = 0L
        copies.forEach { file ->
            total += file.length()
            if (total > CACHE_LIMIT && file.nameWithoutExtension != keep) file.delete()
        }
    }

    fun cacheBytes(): Long = romDir.listFiles().orEmpty().sumOf { it.length() }

    fun clearCache() {
        romDir.listFiles().orEmpty().forEach { it.delete() }
    }

    companion object {
        private const val CACHE_LIMIT = 3L * 1024 * 1024 * 1024
        private const val SEVEN_Z_MEMORY_KB = 256 * 1024

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Reads exactly [count] bytes, or null if the stream ends first. */
internal fun InputStream.readExactly(count: Int): ByteArray? {
    val bytes = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(bytes, offset, count - offset)
        if (read < 0) return null
        offset += read
    }
    return bytes
}

/** Skips exactly [count] bytes (decompressing streams may skip less per call). */
internal fun InputStream.skipFully(count: Long): Boolean {
    var left = count
    val scratch = ByteArray(1 shl 16)
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) { left -= skipped; continue }
        val read = read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
        if (read < 0) return false
        left -= read
    }
    return true
}
