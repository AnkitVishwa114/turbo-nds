package com.appkraft.turbonds

import com.appkraft.turbonds.library.NdsHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NdsHeaderTest {
    /** A minimal valid header: game code, logo start bytes, banner offset and a correct CRC. */
    private fun header(code: String = "ABCE", banner: Int = 0x1000): ByteArray {
        val bytes = ByteArray(NdsHeader.HEADER_SIZE)
        code.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x0C)
        bytes[0xC0] = 0x24; bytes[0xC1] = 0xFF.toByte()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0x68, banner)
        val crc = NdsHeader.crc16(bytes, 0, 0x15E)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(0x15E, crc.toShort())
        return bytes
    }

    private fun banner(title: String): ByteArray {
        val bytes = ByteArray(NdsHeader.BANNER_SIZE)
        bytes[0] = 1 // version 1
        // Palette colour 1 = pure red (BGR555 0x001F); every pixel uses colour 1.
        bytes[0x222] = 0x1F; bytes[0x223] = 0
        for (i in 0x20 until 0x220) bytes[i] = 0x11
        title.toByteArray(Charsets.UTF_16LE).copyInto(bytes, 0x340)
        return bytes
    }

    @Test fun readsUsedRomSize() {
        val bytes = header()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0x80, 0x0400_0000)
        assertEquals(64L * 1024 * 1024, NdsHeader.usedSize(bytes))
        assertEquals(0L, NdsHeader.usedSize(ByteArray(10)))
    }

    @Test fun readsGameCodeAndStableId() {
        val info = NdsHeader.parseHeader(header())
        assertNotNull(info)
        assertEquals("ABCE", info!!.gameCode)
        assertEquals(0x1000L, info.bannerOffset)
        assertEquals("ABCE-%04X".format(info.headerCrc), info.stableId)
    }

    @Test fun rejectsFilesThatAreNotDsGames() {
        assertNull(NdsHeader.parseHeader(ByteArray(NdsHeader.HEADER_SIZE)))
        assertNull(NdsHeader.parseHeader(ByteArray(10)))
    }

    @Test fun readsEnglishTitleSubtitleAndIcon() {
        val info = NdsHeader.withBanner(NdsHeader.parseHeader(header())!!, banner("Puzzle Quest\nSubtitle\nMaker"))
        assertEquals("Puzzle Quest", info.title)
        assertEquals("Subtitle · Maker", info.subtitle)
        val pixels = info.iconPixels!!
        assertEquals(32 * 32, pixels.size)
        assertEquals(0xFFFF0000.toInt(), pixels[0])
    }

    @Test fun crcMatchesKnownValue() {
        // CRC-16/MODBUS of "123456789" is 0x4B37.
        assertEquals(0x4B37, NdsHeader.crc16("123456789".toByteArray(), 0, 9))
    }

    @Test fun cleansFileNamesIntoTitles() {
        assertEquals("Super Game", NdsHeader.titleFromFileName("1234 - Super Game (Europe) [b].nds"))
        assertEquals("My Game", NdsHeader.titleFromFileName("My_Game.zip"))
    }
}
