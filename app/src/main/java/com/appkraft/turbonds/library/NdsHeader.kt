package com.appkraft.turbonds.library

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What a DS game file says about itself: the cartridge header gives the game code and a header
 * checksum (together a stable id for saves across devices), and the banner carries the game's
 * own 32×32 icon and its title in several languages. Everything is read from the file, offline.
 */
data class NdsInfo(
    val gameCode: String,
    val headerCrc: Int,
    val bannerOffset: Long,
    val title: String? = null,
    val subtitle: String? = null,
    /** 32×32 ARGB pixels, row by row, or null when the banner could not be read. */
    val iconPixels: IntArray? = null
) {
    /** Stable id for this game's saves: same ROM → same id on every phone and file name. */
    val stableId: String get() = "${gameCode.ifBlank { "NONE" }}-%04X".format(headerCrc and 0xFFFF)
}

object NdsHeader {
    const val HEADER_SIZE = 0x200
    const val BANNER_SIZE = 0x840

    /** File extensions the app recognises as DS games or archives holding one. */
    val gameExtensions = setOf("nds", "dsi", "srl", "ids")
    val archiveExtensions = setOf("zip", "7z")
    val allExtensions = gameExtensions + archiveExtensions

    /** Parses the first 0x200 bytes of a DS ROM; null when they do not look like a DS header. */
    fun parseHeader(header: ByteArray): NdsInfo? {
        if (header.size < HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        // The Nintendo logo at 0x0C0 always starts with these bytes on a real DS cartridge image.
        val logoOk = header[0xC0].toInt() and 0xFF == 0x24 && header[0xC1].toInt() and 0xFF == 0xFF
        val storedCrc = buffer.getShort(0x15E).toInt() and 0xFFFF
        val computedCrc = crc16(header, 0, 0x15E)
        if (!logoOk && storedCrc != computedCrc) return null
        val code = String(header, 0x0C, 4, Charsets.US_ASCII).filter { it.isLetterOrDigit() }
        val banner = buffer.getInt(0x68).toLong() and 0xFFFFFFFFL
        return NdsInfo(gameCode = code, headerCrc = storedCrc, bannerOffset = banner)
    }

    /**
     * The size the header says the game needs (0x080, "total used ROM size"). A file shorter
     * than this is a partial download, which crashes the emulator instead of showing an error.
     */
    fun usedSize(header: ByteArray): Long =
        if (header.size < HEADER_SIZE) 0L
        else ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(0x80).toLong() and 0xFFFFFFFFL

    /** Adds title, subtitle and icon from the banner block (at least [BANNER_SIZE] bytes). */
    fun withBanner(info: NdsInfo, banner: ByteArray): NdsInfo {
        if (banner.size < BANNER_SIZE) return info
        val version = (banner[0].toInt() and 0xFF) or ((banner[1].toInt() and 0xFF) shl 8)
        if (version !in 1..0x0103) return info
        val lines = titleLines(banner)
        return info.copy(
            title = lines.firstOrNull(),
            subtitle = lines.drop(1).joinToString(" · ").ifBlank { null },
            iconPixels = icon(banner)
        )
    }

    /** English title first (offset 0x340), then Japanese (0x240) if English is empty. */
    private fun titleLines(banner: ByteArray): List<String> {
        for (offset in intArrayOf(0x340, 0x240, 0x440, 0x540, 0x640, 0x740)) {
            val text = String(banner, offset, 0x100, Charsets.UTF_16LE).substringBefore('\u0000').trim()
            if (text.isNotBlank()) return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
        return emptyList()
    }

    /** 4-bit tiled icon (4×4 tiles of 8×8) with a 16-colour BGR555 palette; colour 0 is transparent. */
    private fun icon(banner: ByteArray): IntArray {
        val palette = IntArray(16) { index ->
            val value = (banner[0x220 + index * 2].toInt() and 0xFF) or ((banner[0x221 + index * 2].toInt() and 0xFF) shl 8)
            if (index == 0) 0 else {
                val r = (value and 0x1F) * 255 / 31
                val g = (value shr 5 and 0x1F) * 255 / 31
                val b = (value shr 10 and 0x1F) * 255 / 31
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val pixels = IntArray(32 * 32)
        for (tile in 0 until 16) {
            val tileX = tile % 4 * 8
            val tileY = tile / 4 * 8
            for (row in 0 until 8) for (pair in 0 until 4) {
                val byte = banner[0x20 + tile * 32 + row * 4 + pair].toInt() and 0xFF
                val x = tileX + pair * 2
                val y = tileY + row
                pixels[y * 32 + x] = palette[byte and 0x0F]
                pixels[y * 32 + x + 1] = palette[byte shr 4]
            }
        }
        return pixels
    }

    /** CRC-16/MODBUS as used by the DS header checksum. */
    fun crc16(data: ByteArray, start: Int, length: Int): Int {
        var crc = 0xFFFF
        for (i in start until start + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    /** A readable title from a file name: drops the extension, region tags and dump markers. */
    fun titleFromFileName(name: String): String {
        val base = name.substringBeforeLast('.')
            .replace(Regex("""^\d{4}\s*-\s*"""), "")
            .replace(Regex("""\s*[\[(][^\])]*[\])]"""), "")
            .replace('_', ' ')
            .trim()
        return base.ifBlank { name }
    }
}
