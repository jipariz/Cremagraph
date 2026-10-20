package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val OLD_ABSENT = setOf("reframe", "recordedAt", "title", "recipe", "phases", "targetWidth", "format")

class SidecarTest {
    private val item =
        VideoItem(
            id = "v1",
            file = "v1.mp4",
            createdAt = Instant.parse("2026-10-11T08:14:00Z"),
            durationUs = 41_000_000,
            origin = Origin.RECORDED,
            hasAudio = false,
            shot = shotOf(s(0.0, pressure = 0f), s(1.0, pressure = 2.5f, weight = 0.4f)),
            shotStartInVideoUs = 3_000_000,
            syncLocked = true,
            trimInUs = 0,
            trimOutUs = 41_000_000,
            overlay = OverlaySettings(styleId = "gweb", opacity = 0.85f),
        )

    @Test
    fun roundTrips() {
        assertEquals(item, SidecarCodec.decode(SidecarCodec.encode(item)))
    }

    @Test
    fun writesSchemaVersion() {
        assertTrue(SidecarCodec.encode(item).contains("\"schema\": 1"))
    }

    @Test
    fun rejectsNewerSchema() {
        val newer = SidecarCodec.encode(item).replace("\"schema\": 1", "\"schema\": 2")
        assertFailsWith<UnsupportedSidecarSchema> { SidecarCodec.decode(newer) }
    }

    @Test
    fun theLegendSettingRoundTripsAndOlderSidecarsShowIt() {
        val hidden = item.copy(overlay = item.overlay.copy(legend = false))
        assertEquals(hidden, SidecarCodec.decode(SidecarCodec.encode(hidden)))
        // Written before the setting existed: no legend field at all.
        val older = SidecarCodec.encode(item).replace(Regex(",\\s*\"legend\":\\s*true"), "")
        assertTrue("legend" !in older)
        assertTrue(SidecarCodec.decode(older).overlay.legend)
    }

    @Test
    fun theReframeTitleRecipeAndClockRoundTrip() {
        val edited =
            item.copy(
                reframe = Reframe(zoom = 1.6f, x = 0.4f, y = 0.55f),
                recordedAt = Instant.parse("2026-10-11T08:13:57Z"),
                overlay = item.overlay.copy(title = "Sunday shot", recipe = "18 g · 36 g · 28 s"),
            )
        assertEquals(edited, SidecarCodec.decode(SidecarCodec.encode(edited)))
    }

    @Test
    fun olderSidecarsWithoutReframeTitleOrClockStillRead() {
        // Written before these fields existed: none of their keys anywhere.
        val older = without(SidecarCodec.json.parseToJsonElement(SidecarCodec.encode(item)), OLD_ABSENT).toString()
        OLD_ABSENT.forEach { assertTrue("\"$it\"" !in older) }
        val read = SidecarCodec.decode(older)
        assertEquals(Reframe(), read.reframe)
        assertEquals(null, read.recordedAt)
        assertEquals(null, read.overlay.title)
        assertTrue(read.overlay.phases)
        assertEquals(VideoFormat.PORTRAIT_9_16, read.format)
        assertEquals(item, read)
    }

    private fun without(e: JsonElement, keys: Set<String>): JsonElement =
        when (e) {
            is JsonObject -> JsonObject(e.filterKeys { it !in keys }.mapValues { without(it.value, keys) })
            is JsonArray -> JsonArray(e.map { without(it, keys) })
            else -> e
        }

    @Test
    fun ignoresUnknownFields() {
        val extra = SidecarCodec.encode(item).replaceFirst("{", "{\"futureField\": true,")
        assertEquals(item, SidecarCodec.decode(extra))
    }

    @Test
    fun stopRuleSerializesWithStableNames() {
        val text =
            SidecarCodec.json.encodeToString(
                RecordingSettings.serializer(),
                RecordingSettings(stopRule = StopRule.WeightSettles(4)),
            )
        assertTrue(text.contains("\"weight_settles\""), text)
        assertEquals(
            StopRule.WeightSettles(4),
            SidecarCodec.json.decodeFromString(RecordingSettings.serializer(), text).stopRule,
        )
    }

    @Test
    fun fileNameFollowsConvention() {
        assertEquals("abc.onlyshots.json", Sidecar.fileName("abc"))
    }
}
