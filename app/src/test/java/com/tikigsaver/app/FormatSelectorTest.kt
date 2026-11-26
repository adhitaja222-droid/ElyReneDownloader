package com.tikigsaver.app

import com.tikigsaver.app.engine.FormatSelector
import com.tikigsaver.app.engine.ParsedFormat
import com.tikigsaver.app.engine.UrlUtils
import com.tikigsaver.app.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSelectorTest {

    @Test
    fun `hd selector prefers 1080p 60fps over 1080p 30fps and offers 2k ultra option`() {
        val formats = listOf(
            ParsedFormat(
                formatId = "download_addr-0",
                ext = "mp4",
                width = 1080,
                height = 1920,
                fps = 60.0,
                tbr = 6500.0,
                vcodec = "h264",
                acodec = "aac",
                formatNote = "watermarked"
            ),
            ParsedFormat(
                formatId = "h264_540p_30",
                ext = "mp4",
                width = 540,
                height = 960,
                fps = 30.0,
                tbr = 900.0,
                vcodec = "h264",
                acodec = "aac"
            ),
            ParsedFormat(
                formatId = "h264_1080p_30",
                ext = "mp4",
                width = 1080,
                height = 1920,
                fps = 30.0,
                tbr = 3200.0,
                vcodec = "h264",
                acodec = "aac"
            ),
            ParsedFormat(
                formatId = "bytevc1_1080p_60",
                ext = "mp4",
                width = 1080,
                height = 1920,
                fps = 60.0,
                tbr = 5400.0,
                vcodec = "hevc",
                acodec = "aac"
            )
        )

        val selected = FormatSelector.selectVideoOptions(formats)

        assertEquals(60.0, selected.hd.fps, 0.01)
        assertEquals(1080, selected.hd.width)
        assertEquals(1920, selected.hd.height)
        assertEquals(0, selected.hd.upscaleTargetShort)
        assertTrue(selected.hd.formatSpec.startsWith("bytevc1_1080p_60"))

        assertEquals(540, selected.normal.width)
        assertEquals(30.0, selected.normal.fps, 0.01)
        assertEquals(null, selected.ultra)
    }

    @Test
    fun `when source max is 720p hd upgrades to 1080p preserving fps`() {
        val formats = listOf(
            ParsedFormat(
                formatId = "h264_720p_60",
                ext = "mp4",
                width = 720,
                height = 1280,
                fps = 60.0,
                tbr = 2200.0,
                vcodec = "h264",
                acodec = "aac"
            )
        )

        val selected = FormatSelector.selectVideoOptions(formats)
        assertEquals(720, selected.normal.width)
        assertEquals(1080, selected.hd.width)
        assertEquals(1920, selected.hd.height)
        assertEquals(60.0, selected.hd.fps, 0.01)
        assertEquals(null, selected.ultra)
    }

    @Test
    fun `url utils extracts tiktok and instagram links from share text`() {
        val shareText = "Cek video keren ini! https://vt.tiktok.com/ZS12345ab/ #fyp"
        val extracted = UrlUtils.extractSupportedUrl(shareText)
        assertEquals("https://vt.tiktok.com/ZS12345ab/", extracted)
        assertEquals(Platform.TIKTOK, UrlUtils.detectPlatform(extracted!!))

        val igUrl = "https://www.instagram.com/p/C9xAbCdEfGh/?igsh=123"
        assertEquals(Platform.INSTAGRAM, UrlUtils.detectPlatform(igUrl))
        assertEquals("C9xAbCdEfGh", UrlUtils.extractInstagramShortcode(igUrl))
    }
}
