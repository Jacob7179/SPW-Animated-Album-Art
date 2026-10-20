<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player 1.19.0 Windows mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.4.4.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Windows / Linux compatibility (0.4.4)

Keep `bin/ffmpeg.exe` (Windows) and `bin/ffmpeg` (Linux x86-64) beside the source before building. Packaging includes only these two executables and license notices, never the rest of an FFmpeg distribution. On Linux, the plugin restores the bundled binary's owner execute permission automatically. No Steam launch options are needed. The binary must match the system architecture and be on a filesystem allowing execution.

Host cover entry points are discovered from source traces and JVM signatures; native member names are resolved from unique type/signature features to accommodate different platform mappings. Requires Salt Player 1.19.0 and Java 25. Missing or ambiguous features produce diagnostics.

Online artwork fetching runs in the background, keeps local-video priority, and copies the original video stream into MP4 without libx264. Playback handles scaling/cropping. Downloads fail on FFmpeg errors and the entire cached video is decoded for validation before publication. Short source clips retain their original duration. `animated-cover.log` records settings, song metadata, search/download phases, no-match results, HTTP errors and FFmpeg details. HTTP 504 is a third-party service failure.

For Apple single-file byte-range HLS, the downloader selects a moderate SDR H.264 variant and downloads the complete underlying MP4 when contiguous ranges identify one file. This avoids corrupt/repeated byte-range fragments. New cache entries use a `complete-` prefix so older potentially truncated entries are downloaded again.

## Third-party attribution and licensing

The online artwork feature uses the public API of [Apple Music Animated Artworks by m8tec](https://github.com/m8tec/apple-music-animated-artworks), hosted at [artwork.m8tec.top](https://artwork.m8tec.top/). Its server source code is not bundled with this plugin.

SPW Animated Album Art and its independently written API client remain licensed under the [MIT License](LICENSE). No upstream software license was identified for the external project during review; this attribution does not grant permission or claim that the service is MIT-licensed. The project's MIT License does not cover the external service, downloaded Apple Music artwork, or third-party trademarks. Service use and artwork rights remain subject to the applicable terms and rights holders.

For details, please refer to the [Third-party notices](licenses/THIRD_PARTY_NOTICES.md) and [FFmpeg](licenses/FFmpeg-THIRD-PARTY.md) license statement.