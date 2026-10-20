package app.onlyshots.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The JSON file stored next to every video: `<videoId>.onlyshots.json`. It holds everything needed to restyle and
 * re-export the overlay and is the source of truth over the library index.
 */
@Serializable
data class Sidecar(val schema: Int = SCHEMA, val video: VideoItem) {
    companion object {
        const val SCHEMA = 1
        const val SUFFIX = ".onlyshots.json"
        /** What sidecars were called before the app was renamed from Cremagraph; [LibraryStore] renames them. */
        const val LEGACY_SUFFIX = ".cremagraph.json"

        fun fileName(videoId: String): String = "$videoId$SUFFIX"
    }
}

class UnsupportedSidecarSchema(val schema: Int) : Exception("sidecar schema $schema is newer than ${Sidecar.SCHEMA}")

object SidecarCodec {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }

    fun encode(item: VideoItem): String = json.encodeToString(Sidecar.serializer(), Sidecar(video = item))

    /** @throws UnsupportedSidecarSchema for a sidecar written by a newer app version. */
    fun decode(text: String): VideoItem {
        val schema =
            try {
                json.parseToJsonElement(text).jsonObject["schema"]?.jsonPrimitive?.int
            } catch (e: IllegalArgumentException) {
                throw SerializationException("not a sidecar: ${e.message}")
            } ?: throw SerializationException("sidecar without schema")
        if (schema > Sidecar.SCHEMA) throw UnsupportedSidecarSchema(schema)
        return json.decodeFromString(Sidecar.serializer(), text).video
    }
}
