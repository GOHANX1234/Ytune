# YouTube Music Streaming API Reference

Production base URL: `http://103.30.211.180:8000`  
Local development base URL: `http://localhost:8000`  
API version prefix: `/api/v1`  
Content type: `application/json`

Every response includes an `X-Request-ID` header. Clients may supply their own `X-Request-ID`; otherwise the server generates one. FastAPI publishes an OpenAPI schema at `/openapi.json`, Swagger UI at `/docs`, and ReDoc at `/redoc`.

## Error shape

All application errors, including request-validation errors, use this shape:

```json
{
  "error": {
    "code": "not_found",
    "message": "YouTube track is unavailable or was not found",
    "details": null
  }
}
```

Common statuses are `404` for unavailable tracks, `422` for invalid parameters, `502` for YouTube/LRCLIB failures, `503` for MongoDB unavailability, and `500` for unexpected failures. Validation errors place FastAPI's validation entries in `error.details.errors`.

YouTube failures use more specific stable codes when possible: `region_restricted`, `age_restricted`, `rate_limited` (`429`), `bot_challenge`, and `extractor_outdated`. Other upstream failures use `upstream_error`.

## GET /health

Reports application and MongoDB health.

Request:

```bash
curl http://localhost:8000/health
```

Response `200` (or the same body with `503` when degraded):

```json
{
  "status": "healthy",
  "database": "connected",
  "version": "1.1.0",
  "timestamp": "2026-08-15T05:30:00Z"
}
```

## GET /api/v1/search

Searches YouTube Music/YouTube through `yt-dlp`. It returns lightweight metadata without resolving stream URLs.

Query parameters:

| Name | Type | Required | Rules | Description |
|---|---|---:|---|---|
| `q` | string | yes | 1–200 characters, nonblank | Song, artist, or album query |
| `limit` | integer | no | 1–50; default 10 | Maximum results |
| `type` | string | no | `song`, `video`, or `any`; default `song` | Result category |
| `min_duration` | integer | no | 0–86400 seconds | Minimum duration |
| `max_duration` | integer | no | 1–86400 seconds | Maximum duration |
| `official` | boolean | no | `true` or `false` | Require or exclude likely official uploads |

Request:

```bash
curl --get http://localhost:8000/api/v1/search \
  --data-urlencode "q=Daft Punk Get Lucky" \
  --data "limit=2"
```

Response `200`:

```json
{
  "results": [
    {
      "video_id": "5NV6Rdv1a3I",
      "title": "Daft Punk - Get Lucky (Official Audio) ft. Pharrell Williams, Nile Rodgers",
      "artists": ["Daft Punk"],
      "artist": null,
      "uploader": "Daft Punk",
      "duration_seconds": 369.0,
      "webpage_url": "https://www.youtube.com/watch?v=5NV6Rdv1a3I",
      "highest_resolution_thumbnail": "https://i.ytimg.com/vi/5NV6Rdv1a3I/maxresdefault.jpg",
      "thumbnails": [
        {
          "url": "https://i.ytimg.com/vi/5NV6Rdv1a3I/hqdefault.jpg",
          "width": 480,
          "height": 360,
          "preference": null,
          "id": "2"
        }
      ],
      "view_count": 1000000000,
      "upload_date": "20130419",
      "official": true,
      "result_type": "song",
      "relevance_score": 100.0
    }
  ],
  "meta": {"query": "Daft Punk Get Lucky", "count": 1, "limit": 2}
}
```

The API fetches extra candidates, ranks title/artist token overlap, boosts likely official/verified uploads and typical song durations, and penalizes covers, reactions, remixes, slowed/reverb versions, and karaoke. `relevance_score` is relative and is not guaranteed to remain numerically stable between versions.

Errors: `404` no upstream search result; `422` invalid filters/query/limit; `429` YouTube rate limit; `502` extraction failure; `503` database unavailable.

## GET /api/v1/tracks/{video_id}

Returns full stable music metadata. A fresh extraction occurs when the cache is absent, incomplete, or older than `TRACK_CACHE_TTL_SECONDS` (default 24 hours). The `cached` field describes this request.

Path parameters:

| Name | Type | Rules |
|---|---|---|
| `video_id` | string | Exactly 11 URL-safe YouTube ID characters |

Request:

```bash
curl http://localhost:8000/api/v1/tracks/5NV6Rdv1a3I
```

Response `200` (nullable fields remain present):

```json
{
  "track": {
    "video_id": "5NV6Rdv1a3I",
    "title": "Get Lucky",
    "artists": ["Daft Punk", "Pharrell Williams", "Nile Rodgers"],
    "artist": "Daft Punk",
    "uploader": "Daft Punk",
    "uploader_id": "daftpunk",
    "channel": "Daft Punk",
    "channel_id": "UC_kRDKYrUlrbtrSiyu5Tflg",
    "album": "Random Access Memories",
    "album_artist": "Daft Punk",
    "track": "Get Lucky",
    "track_number": 8,
    "disc_number": 1,
    "duration_seconds": 369.0,
    "release_year": 2013,
    "release_date": "20130419",
    "upload_date": "20130419",
    "genres": ["Electronic"],
    "description": "Official audio.",
    "webpage_url": "https://www.youtube.com/watch?v=5NV6Rdv1a3I",
    "original_url": "https://www.youtube.com/watch?v=5NV6Rdv1a3I",
    "highest_resolution_thumbnail": "https://i.ytimg.com/vi/5NV6Rdv1a3I/maxresdefault.jpg",
    "album_art_url": "https://i.ytimg.com/vi/5NV6Rdv1a3I/maxresdefault.jpg",
    "thumbnails": [{"url": "https://i.ytimg.com/vi/5NV6Rdv1a3I/hqdefault.jpg", "width": 480, "height": 360, "preference": null, "id": "2"}],
    "view_count": 1000000000,
    "like_count": 8000000,
    "comment_count": 250000,
    "availability": "public",
    "age_limit": 0,
    "live_status": "not_live",
    "extractor": "youtube",
    "extractor_key": "Youtube",
    "tags": ["Daft Punk", "Get Lucky"],
    "categories": ["Music"],
    "audio_formats": [
      {
        "format_id": "251",
        "url": null,
        "ext": "webm",
        "protocol": "https",
        "audio_codec": "opus",
        "audio_bitrate_kbps": 130.0,
        "sample_rate_hz": 48000,
        "file_size": 6000000,
        "quality": 0.0,
        "format_note": "medium"
      }
    ],
    "created_at": "2026-08-15T05:30:00Z",
    "updated_at": "2026-08-15T05:30:00Z",
    "last_accessed_at": "2026-08-15T05:30:00Z"
  },
  "cached": false
}
```

Errors: `404` invalid/unavailable/private/removed video; `422` malformed path; `502` age, region, or other upstream extraction failure; `503` database unavailable.

## GET /api/v1/tracks/{video_id}/stream

Resolves fresh metadata for every currently available audio-only format, ordered from highest to lowest bitrate. It also selects one `recommended_format` according to the request filters.

This is a resolution endpoint, not the recommended media-delivery endpoint. Use the returned `playback_url` to play audio through this API. Do not use the signed Google `url` in browser or mobile players.

The endpoint always contacts YouTube. Signed URLs are never read from or written to MongoDB.

Request:

```bash
curl http://localhost:8000/api/v1/tracks/5NV6Rdv1a3I/stream
```

Optional query parameters:

| Name | Values | Default | Description |
|---|---|---|---|
| `container` | `m4a`, `webm` | all | Restrict formats by container |
| `codec` | string | all | Case-insensitive codec substring, such as `opus` or `mp4a` |
| `quality` | `best`, `medium`, `low` | `best` | Chooses the highest, middle, or lowest bitrate matching format as `recommended_format` |

Filters affect both `formats` and `recommended_format`. When no format matches, `formats` is empty and `recommended_format` is `null`; the request itself still returns `200`.

Response `200`:

```json
{
  "video_id": "5NV6Rdv1a3I",
  "title": "Get Lucky",
  "resolved_at": "2026-08-15T05:30:00Z",
  "expires_at": "2026-08-15T11:30:00Z",
  "ephemeral": true,
  "warning": "Signed YouTube URLs are ephemeral and may expire sooner than indicated. Resolve again before playback if a URL is rejected.",
  "formats": [
    {
      "format_id": "251",
      "url": "https://rr.example.googlevideo.com/videoplayback?expire=1786774200&sig=...",
      "ext": "webm",
      "protocol": "https",
      "audio_codec": "opus",
      "audio_bitrate_kbps": 130.0,
      "sample_rate_hz": 48000,
      "file_size": 6000000,
      "quality": 0.0,
      "format_note": "medium",
      "playback_url": "/api/v1/tracks/5NV6Rdv1a3I/play?format_id=251"
    }
  ],
  "recommended_format": {
    "format_id": "251",
    "url": "https://rr.example.googlevideo.com/videoplayback?expire=1786774200&sig=...",
    "ext": "webm",
    "protocol": "https",
    "audio_codec": "opus",
    "audio_bitrate_kbps": 130.0,
    "sample_rate_hz": 48000,
    "file_size": 6000000,
    "quality": 0.0,
    "format_note": "medium",
    "playback_url": "/api/v1/tracks/5NV6Rdv1a3I/play?format_id=251"
  },
  "selection": {"container": null, "codec": null, "quality": "best"}
}
```

Important response fields:

| Field | Meaning |
|---|---|
| `formats` | Matching audio-only formats in descending bitrate order |
| `recommended_format` | Format selected by `quality`, or `null` when no format matches |
| `playback_url` | Supported API URL for playing that format; relative to the same API origin |
| `url` | Ephemeral signed Google media URL; exposed for server-side diagnostics and compatibility only |
| `resolved_at` | Time this format list was extracted |
| `expires_at` | Expiry parsed from a signed URL when its `expire` parameter exists; otherwise `null` |
| `ephemeral` | Always `true`; availability and signed media data can change at any time |
| `selection` | Filters used to produce this result |

`expires_at` is informational and is not a guaranteed lifetime. A URL may be rejected before that time. Never persist, cache, log, or share the signed `url` values.

### Supported playback flow

1. Request `/stream` when the client needs a format list or wants to choose a container, codec, or quality.
2. Read `recommended_format.playback_url`, or choose a format from `formats` and read its `playback_url`.
3. Send the media player to that API URL. No YouTube-specific request headers are required from the client.
4. Request `/stream` again if a selected `format_id` later returns `404`, because YouTube's available formats may have changed.

Example using the recommended format:

```bash
playback_url="$(
  curl --silent 'http://localhost:8000/api/v1/tracks/5NV6Rdv1a3I/stream?quality=best' |
    jq --raw-output '.recommended_format.playback_url'
)"

curl --location --range 0-65535 \
  --output stream-sample.bin \
  "http://localhost:8000${playback_url}"
```

Browser example:

```html
<audio
  controls
  preload="metadata"
  src="http://103.30.211.180:8000/api/v1/tracks/5NV6Rdv1a3I/play?format_id=251">
</audio>
```

For a frontend served from the same API origin, the relative `playback_url` can be assigned directly. For a different origin, prefix it with the API base URL. Production applications should expose this API over HTTPS; an HTTPS page will normally block audio loaded over plain HTTP as mixed content.

Errors: `404` unavailable track; `422` malformed ID or query parameters; `429` YouTube rate limit; `502` extraction, region, age, bot-challenge, or other upstream failure; `503` database unavailable. This endpoint can be slower than cached metadata endpoints.

### Direct signed URL compatibility

The `url` field is not the supported browser playback path. Direct requests can fail with `403` because URLs are short-lived, tied to YouTube client parameters, and may require the exact per-format headers used by yt-dlp. Fixed `User-Agent`, `Origin`, and `Referer` values are not reliable across all videos or formats.

Browsers cannot reliably set headers such as `User-Agent`, `Origin`, or `Referer`, and Google media responses may also be blocked by CORS. Use `playback_url` instead. Server-side diagnostic clients that deliberately use `url` must obtain and apply yt-dlp's exact current format headers; the API does not expose those headers as a public contract.

## GET /api/v1/tracks/{video_id}/play

Streams a freshly resolved audio format through this API. This is the supported endpoint for HTML audio elements, mobile media players, backend consumers, seeking, and byte-range downloads.

Request using the best currently available audio-only format:

```bash
curl --location --range 0-65535 \
  --output stream-sample.bin \
  http://localhost:8000/api/v1/tracks/5NV6Rdv1a3I/play
```

Request a specific format returned by `/stream`:

```bash
curl --location --range 0-65535 \
  --output stream-sample.bin \
  'http://localhost:8000/api/v1/tracks/5NV6Rdv1a3I/play?format_id=251'
```

Query parameters:

| Name | Required | Description |
|---|---|---|
| `format_id` | No | Exact yt-dlp audio format ID from `/stream`; when omitted, the highest-bitrate audio-only format is selected |

Playback behavior:

- It contacts YouTube and resolves a fresh signed URL for every playback attempt. No stream URL is read from or written to MongoDB.
- It applies yt-dlp's exact headers for the selected format and follows upstream redirects.
- It forwards the client's `Range` header. If the client sends no range, it requests `bytes=0-` because Google media servers can reject unbounded media requests.
- It re-resolves and retries transient upstream `403` and `410` responses up to six times.
- It streams the upstream response without loading the complete audio file into application memory.
- It returns `Cache-Control: no-store` so signed media responses are not retained by shared caches.
- No YouTube-specific `User-Agent`, `Origin`, or `Referer` header is required from the caller.

Response:

- Normally `206 Partial Content`, with `Content-Type` such as `audio/mp4` or `audio/webm`.
- `Accept-Ranges`, `Content-Length`, `Content-Range`, `Content-Type`, `ETag`, and `Last-Modified` are forwarded when supplied by the media server.
- The response body is raw audio bytes, not JSON.

Errors: `404` unavailable track or requested `format_id` no longer available; `422` malformed ID or `format_id`; `429` YouTube rate limit; `502` extraction failure or media server rejection after all retries; `503` database unavailable. Error responses use the standard JSON error shape. Request `/stream` again after a `404` for a specific format.

## GET /api/v1/tracks/{video_id}/lyrics

Gets cached lyrics or uses the full track’s name, artist, album, and rounded duration with LRCLIB’s `/api/get` exact-match endpoint. Synced LRC lyrics are preferred; plain lyrics are retained as fallback. A no-match response is also cached with `found: false`.

Request:

```bash
curl http://localhost:8000/api/v1/tracks/5NV6Rdv1a3I/lyrics
```

Response `200` with lyrics:

```json
{
  "lyrics": {
    "video_id": "5NV6Rdv1a3I",
    "found": true,
    "instrumental": false,
    "synced_lyrics": "[00:12.40]Like the legend of the phoenix\n[00:16.20]All ends with beginnings",
    "plain_lyrics": "Like the legend of the phoenix\nAll ends with beginnings",
    "preferred_type": "synced",
    "lrclib_id": 12345,
    "match_confidence": "exact",
    "track_name": "Get Lucky",
    "artist_name": "Daft Punk",
    "album_name": "Random Access Memories",
    "duration_seconds": 369.0,
    "fetched_at": "2026-08-15T05:30:00Z",
    "updated_at": "2026-08-15T05:30:00Z"
  },
  "cached": false
}
```

Response `200` when no LRCLIB match exists:

```json
{
  "lyrics": {
    "video_id": "5NV6Rdv1a3I",
    "found": false,
    "instrumental": false,
    "synced_lyrics": null,
    "plain_lyrics": null,
    "preferred_type": null,
    "lrclib_id": null,
    "match_confidence": null,
    "track_name": "Get Lucky",
    "artist_name": "Daft Punk",
    "album_name": "Random Access Memories",
    "duration_seconds": 369.0,
    "fetched_at": "2026-08-15T05:30:00Z",
    "updated_at": "2026-08-15T05:30:00Z"
  },
  "cached": false
}
```

For an instrumental match, `found` and `instrumental` are `true`, lyric strings may be `null`, and `preferred_type` is `instrumental`.

The service first calls LRCLIB's exact-match endpoint. If no exact record exists, it searches LRCLIB and scores candidates using normalized title, artist, and duration. Fallback responses use `lookup_method: "search_fallback"`, `match_confidence: "fallback"`, and a numeric `match_score`. Exact responses use `lookup_method: "exact"` and `match_score: 1.0`.

Errors: `404` source track unavailable; `422` malformed ID; `502` YouTube or LRCLIB failure; `503` database unavailable.

## POST /api/v1/tracks/{video_id}/lyrics/refresh

Ignores the cached lyrics record, repeats track/LRCLIB lookup, and replaces the stored result. Use this when lyrics are missing, stale, or incorrect.

```bash
curl -X POST http://localhost:8000/api/v1/tracks/5NV6Rdv1a3I/lyrics/refresh
```

The response has the normal lyrics shape with `cached: false` and `refreshed: true`.

## GET /api/v1/playlists/{playlist_id}

Returns YouTube playlist metadata and an ordered, paginated list of lightweight tracks. It does not resolve stream URLs.

Query parameters: `offset` is zero-based and defaults to `0`; `limit` accepts `1–100` and defaults to `50`.

```bash
curl "http://localhost:8000/api/v1/playlists/PL_PLAYLIST_ID?offset=0&limit=25"
```

Response `200`:

```json
{
  "playlist": {
    "playlist_id": "PL_PLAYLIST_ID",
    "title": "My playlist",
    "uploader": "Channel",
    "description": null,
    "webpage_url": "https://www.youtube.com/playlist?list=PL_PLAYLIST_ID",
    "thumbnail": "https://i.ytimg.com/...",
    "track_count": 100
  },
  "tracks": [
    {
      "position": 1,
      "video_id": "5NV6Rdv1a3I",
      "title": "Get Lucky",
      "uploader": "Daft Punk",
      "duration_seconds": 369.0,
      "webpage_url": "https://www.youtube.com/watch?v=5NV6Rdv1a3I",
      "thumbnail": "https://i.ytimg.com/..."
    }
  ],
  "offset": 0,
  "limit": 25,
  "count": 1,
  "total": 100
}
```

## GET /api/v1/system/ytdlp

Compares the installed yt-dlp version with the latest PyPI release. The server also performs this check at startup and every `YTDLP_CHECK_INTERVAL_SECONDS` (default six hours), logging a warning when an update is available. Monitoring never upgrades production automatically.

```bash
curl http://localhost:8000/api/v1/system/ytdlp
```

```json
{
  "installed_version": "2026.7.4",
  "latest_version": "2026.7.4",
  "update_available": false,
  "checked_at": "2026-08-15T06:30:00Z",
  "source": "PyPI"
}
```
