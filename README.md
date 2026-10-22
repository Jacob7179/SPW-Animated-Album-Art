<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player Windows/Linux mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.4.5.1.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.4.5.1: Build, Download, and Playback Fixes

- Fixed Compose API build errors by adding the official Compose Runtime as a compile-only dependency.
- Direct MP4 downloads now use Java HTTPS, avoiding differences in bundled FFmpeg TLS support.
- Full-video validation explicitly uses rawvideo; libx264 is not required for online downloads.
- Added a persistent user log alongside the existing plugin-directory log.
- Downloaded Apple MP4s are remuxed without re-encoding to normalize their start timestamp to zero and correct the reported duration.
- Held the opening video frame until it is drawn by the native cover, preventing playback from advancing unseen.
- New cache filenames use the `seek-v1-` prefix so older files with offset timestamps are downloaded again.
- Fixed Linux startup when the host class loader does not provide `CodeSource.location`; host discovery now falls back to class resources and the JVM classpath.
- Allowed recoverable timestamp warnings during MP4 remuxing on older Linux FFmpeg builds; full-video validation remains strict.
- Verified remuxed packet timing and retained the original MP4 if FFmpeg changes it; playback rebases the original timeline before frame-rate conversion.

## Third-party attribution and licensing

The online artwork feature uses the public API of [Apple Music Animated Artworks by m8tec](https://github.com/m8tec/apple-music-animated-artworks), hosted at [artwork.m8tec.top](https://artwork.m8tec.top/). Its server source code is not bundled with this plugin.

SPW Animated Album Art and its independently written API client are licensed under the [GNU AGPL v3.0](LICENSE) (`AGPL-3.0-only`). No upstream software license was identified for the external project during review; this attribution does not grant permission or claim that the service is MIT-licensed. The project's AGPL-3.0-only license does not cover the external service, downloaded Apple Music artwork, or third-party trademarks. Service use and artwork rights remain subject to the applicable terms and rights holders.

For details, please refer to the [Third-party notices](licenses/THIRD_PARTY_NOTICES.md) and [FFmpeg](licenses/FFmpeg-THIRD-PARTY.md) license statement.

## Project license

Copyright (c) 2026 Jacob. SPW Animated Album Art is licensed under the GNU Affero General Public License, version 3 only (`AGPL-3.0-only`). See [LICENSE](LICENSE) for the complete terms. Third-party components and artwork retain their own licenses and rights as described in [third-party notices](licenses/THIRD_PARTY_NOTICES.md).

## Building and compatibility

Requires JDK 25 and the configured Salt Player host archive. Run `build.bat` on Windows. Builds use official Compose Runtime 1.12.0 as a compile-only dependency so Kotlin can resolve top-level APIs even when host package metadata is incomplete. Compose Runtime is not bundled with the plugin; Salt Player supplies it at runtime. The alternative PowerShell build expects this artifact in the Gradle cache.

Place the Windows executable at `bin/ffmpeg.exe` and the Linux x86-64 executable at `bin/ffmpeg` before building. Packaging includes only these two executables and license notices, excluding other files from an FFmpeg distribution. On Linux, owner execute permission is restored automatically. The binary must match the system architecture and be on a filesystem that allows execution.

Native cover methods are located dynamically using source traces, JVM signatures and type features. Host archive discovery supports missing `CodeSource.location` values by checking class resources, class-loader URLs and the JVM classpath. Only locations containing the expected host class are accepted; an unavailable host produces a diagnostic instead of a null-pointer failure. There is no fixed Salt Player version restriction; missing or ambiguous API features produce diagnostics. This does not guarantee compatibility with every release.

## Online downloads and logs

Local video files take priority. When online fetching is enabled, the plugin searches using album artist and album name, downloads matching artwork into its data cache, and validates the complete video before publishing it. A failed lookup or download leaves the native static cover available. Direct MP4 downloads use Java HTTPS; HLS-only sources still require FFmpeg HTTPS support.

The persistent log is saved at:

- Linux: `$XDG_STATE_HOME/SPW-Animated-Album-Art/animated-cover.log`, or `~/.local/state/SPW-Animated-Album-Art/animated-cover.log` if unset.
- Windows: `%LOCALAPPDATA%/SPW-Animated-Album-Art/animated-cover.log`.

The plugin also attempts to write `animated-cover.log` in its installation directory. Logs include startup checks, search/download stages, missing metadata, HTTP failures and FFmpeg validation errors.

Linux playback still needs verification on the target system; build and live download tests were performed on Windows.


Downloaded Apple MP4s are remuxed without re-encoding to normalize their start timestamp to zero and rewrite the duration. For example, a 10-second source clip with timestamps 10–20 seconds becomes a normal 0–10 second MP4. New cache files use a `seek-v1-` prefix, so older files with damaged or offset timestamps are downloaded again. This does not add footage absent from the source.

Linux timestamp handling preserves packet timestamps during remuxing and checks packet contents, PTS, DTS, durations and timebase conversion against the original download. If the local FFmpeg changes timing or cannot remux the video, the original MP4 is retained instead and timestamps are rebased in the playback filter before frame-rate conversion. Full decoding validation still runs before caching. Thus source footage is preserved even on older Linux FFmpeg builds; a cache file retained through this fallback may still show the source timestamp offset in an external player.

For supported single-video Apple fragmented MP4 files, timestamp rebasing now happens directly in Java by subtracting the initial decode-time offset from each fragment. Encoded samples, sample durations, composition offsets and box positions are unchanged; this avoids platform-specific remuxer behavior. The remaining one-frame composition delay is handled by the playback `setpts` filter. Files with edit lists, multiple tracks, external fragment indexes or explicit nonzero header durations use the verified FFmpeg path instead. New `seek-v1-` cache filenames force older offset caches to be fetched again.

To support manual seeking in external players, supported Apple fragments are first rebased in Java, then remuxed into a regular MP4 with a rebuilt sample/keyframe index and fast-start metadata. Packet contents and timing are verified after remuxing. If the local FFmpeg cannot preserve timing, the rebased source remains usable by the plugin but external seeking may still be limited; this fallback is explicitly logged. New `seek-v1-` cache names avoid reusing the earlier fragmented cache files.
