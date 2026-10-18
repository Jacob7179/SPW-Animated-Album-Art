# SPW-Animated-Album-Art

Salt Player 1.19.0 Windows mod. Put square_animated_artwork.mp4 beside the song and import AnimatedCover-2.0.3.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches and a 400 ms dissolve at loop boundaries. The dissolve softens mismatched endpoints without modifying the MP4.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.2.0: custom video filenames

Open the mod settings and enter up to five video filenames, including extensions. They are checked in order in the current song folder; blank entries are skipped and the first existing file is used. The first filename defaults to square_animated_artwork.mp4. Changes apply automatically within about one second while the player cover is active. If none exists, the normal cover is shown.
