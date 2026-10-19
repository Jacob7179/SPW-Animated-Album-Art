<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player 1.19.0 Windows mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.4.3.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.4.3: Album Art Transition Fix 2

Startup restoration does not reload the cover or call the native playback controller. For a pre-existing cover, a background task compares its retained native shader pixels with a private decode of the current song's static artwork. Results are accepted only if the image matches and the song/artwork generation has not changed. Unverified images remain native static covers until a naturally decoded cover provides a verified source.
