package com.ytune.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ApiModelTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun parsesSearchResponse() {
        val value = json.decodeFromString<SearchResponse>("""{"results":[{"video_id":"5NV6Rdv1a3I","title":"Get Lucky","artists":["Daft Punk"],"duration_seconds":369.0}],"meta":{"query":"Get Lucky","count":1,"limit":10}}""")
        assertEquals("5NV6Rdv1a3I", value.results.single().video_id)
        assertEquals(369.0, value.results.single().duration_seconds!!, 0.0)
    }

    @Test fun parsesStableErrorEnvelope() {
        val value = json.decodeFromString<ApiErrorEnvelope>("""{"error":{"code":"rate_limited","message":"Try later","details":null}}""")
        assertEquals("rate_limited", value.error.code)
    }

    @Test fun parsesPlaylistLinksAndIds() {
        assertEquals("PL1234567890", PlaylistIdParser.parse("https://youtube.com/playlist?list=PL1234567890"))
        assertEquals("OLAK5uy_123456789", PlaylistIdParser.parse("OLAK5uy_123456789"))
        assertNull(PlaylistIdParser.parse("not a playlist"))
    }
}
