<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player Windows/Linux mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.4.5.1.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.4.5.1: Build Improvements and Version Compatibility

- Fixed Compose API build errors by adding the official Compose Runtime as a compile-only dependency.
- Direct MP4 downloads now use Java HTTPS, avoiding differences in bundled FFmpeg TLS support.
- Full-video validation explicitly uses rawvideo; libx264 is not required for online downloads.
- Added a persistent user log alongside the existing plugin-directory log.

## Third-party attribution and licensing

The online artwork feature uses the public API of [Apple Music Animated Artworks by m8tec](https://github.com/m8tec/apple-music-animated-artworks), hosted at [artwork.m8tec.top](https://artwork.m8tec.top/). Its server source code is not bundled with this plugin.

SPW Animated Album Art and its independently written API client are licensed under the [GNU AGPL v3.0](LICENSE) (`AGPL-3.0-only`). No upstream software license was identified for the external project during review; this attribution does not grant permission or claim that the service is MIT-licensed. The project's AGPL-3.0-only license does not cover the external service, downloaded Apple Music artwork, or third-party trademarks. Service use and artwork rights remain subject to the applicable terms and rights holders.

For details, please refer to the [Third-party notices](licenses/THIRD_PARTY_NOTICES.md) and [FFmpeg](licenses/FFmpeg-THIRD-PARTY.md) license statement.

## Project license

Copyright (c) 2026 Jacob. SPW Animated Album Art is licensed under the GNU Affero General Public License, version 3 only (`AGPL-3.0-only`). See [LICENSE](LICENSE) for the complete terms. Third-party components and artwork retain their own licenses and rights as described in [third-party notices](licenses/THIRD_PARTY_NOTICES.md).

## Building and compatibility

Requires JDK 25 and the configured Salt Player host archive. Run `build.bat` on Windows. Builds use official Compose Runtime 1.12.0 as a compile-only dependency so Kotlin can resolve top-level APIs even when host package metadata is incomplete. Compose Runtime is not bundled with the plugin; Salt Player supplies it at runtime. The alternative PowerShell build expects this artifact in the Gradle cache.

Place the Windows executable at `bin/ffmpeg.exe` and the Linux x86-64 executable at `bin/ffmpeg` before building. Packaging includes only these two executables and license notices, excluding other files from an FFmpeg distribution. On Linux, owner execute permission is restored automatically. The binary must match the system architecture and be on a filesystem that allows execution.

Native cover methods are located dynamically using source traces, JVM signatures and type features. There is no fixed Salt Player version restriction; missing or ambiguous API features produce diagnostics. This does not guarantee compatibility with every release.

## Online downloads and logs

Local video files take priority. When online fetching is enabled, the plugin searches using album artist and album name, downloads matching artwork into its data cache, and validates the complete video before publishing it. A failed lookup or download leaves the native static cover available. Direct MP4 downloads use Java HTTPS; HLS-only sources still require FFmpeg HTTPS support.

The persistent log is saved at:

- Linux: `$XDG_STATE_HOME/SPW-Animated-Album-Art/animated-cover.log`, or `~/.local/state/SPW-Animated-Album-Art/animated-cover.log` if unset.
- Windows: `%LOCALAPPDATA%/SPW-Animated-Album-Art/animated-cover.log`.

The plugin also attempts to write `animated-cover.log` in its installation directory. Logs include startup checks, search/download stages, missing metadata, HTTP failures and FFmpeg validation errors.

Linux playback still needs verification on the target system; build and live download tests were performed on Windows.

