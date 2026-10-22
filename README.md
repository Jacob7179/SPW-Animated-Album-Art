<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player Windows/Linux mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.4.5.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.4.5: Build Improvements and Version Compatibility

- Improved the build process.
- Removed the fixed Salt Player 1.19.0 restriction. Compatibility is now determined by runtime API and Hook feature checks.

## Third-party attribution and licensing

The online artwork feature uses the public API of [Apple Music Animated Artworks by m8tec](https://github.com/m8tec/apple-music-animated-artworks), hosted at [artwork.m8tec.top](https://artwork.m8tec.top/). Its server source code is not bundled with this plugin.

SPW Animated Album Art and its independently written API client are licensed under the [GNU AGPL v3.0](LICENSE) (`AGPL-3.0-only`). No upstream software license was identified for the external project during review; this attribution does not grant permission or claim that the service is MIT-licensed. The project's AGPL-3.0-only license does not cover the external service, downloaded Apple Music artwork, or third-party trademarks. Service use and artwork rights remain subject to the applicable terms and rights holders.

For details, please refer to the [Third-party notices](licenses/THIRD_PARTY_NOTICES.md) and [FFmpeg](licenses/FFmpeg-THIRD-PARTY.md) license statement.
## Project license

Copyright (c) 2026 Jacob. SPW Animated Album Art is licensed under the GNU Affero General Public License, version 3 only (`AGPL-3.0-only`). See [LICENSE](LICENSE) for the complete terms. Third-party components and artwork retain their own licenses and rights as described in [third-party notices](licenses/THIRD_PARTY_NOTICES.md).

## Information
Builds use official Compose Runtime 1.12.0 as a compile-only dependency before the merged host archive, so Kotlin can resolve top-level APIs even when host package metadata is incomplete. Compose Runtime is not bundled with the plugin; Salt Player supplies it at runtime. The PowerShell build expects this artifact in the Gradle cache.
