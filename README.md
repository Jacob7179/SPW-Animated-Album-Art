<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player 1.19.0 Windows mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.3.0.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.2.0: custom video filenames

Open the mod settings and enter up to five video filenames, including extensions. They are checked in order in the current song folder; blank entries are skipped and the first existing file is used. The first filename defaults to square_animated_artwork.mp4. Changes apply automatically within about one second while the player cover is active. If none exists, the normal cover is shown.




Cover rendering: draw one primary video image with its native transformation and opacity. Rounded clipping uses 2.5 dp for previews up to 96 dp and 8.5 dp for larger covers, clamped for tiny previews. Reflection and additional image nodes are not redrawn as duplicate video layers.

Song changes keep the outgoing video playing through the native cover transition until the next video has its first decoded frame. A final-frame hold of one second is inserted between video loops, without blending. Decoder startup may extend the hold on slow systems. Frame presentation remains paced at 30 fps.
