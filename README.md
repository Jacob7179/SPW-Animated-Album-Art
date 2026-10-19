<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player 1.19.0 Windows mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.4.2.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.4.2: Album Art Transition Fix

Song changes keep each native painter bound to its original song and video stream throughout the transition. Ownership is captured from the native image decoder request and carried from the exact bitmap to its mipmap shader, rather than sampled from current playback when an image first appears. This prevents rapid A→B→C switches and late image loads from assigning C's video to A's outgoing cover. Unknown or ambiguous image sources use the native static cover.

Rapid-switch regression checks: tests/CoverOriginsTest.java verifies out-of-order A-B-C decoding and immutable image ownership; tests/NativeCoverOriginsTest.java checks the same bitmap-to-shader identity chain with Salt Player's actual native mipmap painter.

Startup restoration: the first video-bearing cover is reloaded once through Salt Player's native controller to tag any image decoded before plugin startup. This is a single initialization refresh, not repeated video-frame cover updates.

