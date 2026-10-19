<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

Salt Player 1.19.0 Windows mod. Put square_animated_artwork.mp4 beside the song and import plugin-com.spw.animated.album.art-0.4.0.spmod.

Video replaces the cover pixels in the native cover canvas. The brightness filter and its preference have been removed; decoded source colors are rendered directly. Existing dim_video settings are ignored.

Includes live cover-coordinate resolution for cover/lyrics switches. Video loops directly without blending the end into the beginning.

Bundled FFmpeg licensing is in licenses/. Build with build.ps1 and JDK 25; requires the host archive and cached Kotlin 2.4.20 compiler.

## Version 0.2.0: custom video filenames

Open the mod settings and enter up to five video filenames, including extensions. They are checked in order in the current song folder; blank entries are skipped and the first existing file is used. The first filename defaults to square_animated_artwork.mp4. Changes apply automatically within about one second while the player cover is active. If none exists, the normal cover is shown.

## Version 0.3.0: Optimization

Cover rendering: replace pixels inside the native cover painter. Salt Player retains control of the small and large cover transitions, transformations, opacity and reflection. Each outgoing and incoming painter retains its own video stream. Corner clipping is inherited from Salt Player; the mod applies no custom small or large corner radius. No separate video overlay is drawn.

Song changes keep each native painter bound to its original song and video stream throughout the transition. A painter never borrows a frame from the next song. Moving between small and large cover layouts preserves that binding. A final-frame hold of one second is inserted between video loops, without blending. Decoder startup may extend the hold on slow systems. Frame presentation remains paced at 30 fps.

Video reflection: the native live reflection follows the video and cover transition. Enable Video reflection in mod settings; the default is on. Changes apply without restarting the decoder.

Loop pause setting: Pause 1 second between loops defaults to on. Turn it off to remove the added delay. Changing it updates running decoders and disabling it cancels an active hold.




## Version 0.4.0: Optional online Apple Music artwork

`Fetch online animated album art` is on by default. Existing saved off preferences remain off. When enabled, lookup order is the five configured local filenames, the plugin's completed album cache, then the third-party search API at https://artwork.m8tec.top/api/v1/artwork/search. Album artist is preferred over track artist. Artist and album must match the returned metadata after case/punctuation normalization; edition words are retained, so Deluxe/Remastered editions are not silently treated as the base album. Conservative matching may skip otherwise usable covers.

Search and conversion run on a background worker. Only the square `url` field is used. FFmpeg creates a silent H.264 MP4, validates its first frame, and atomically publishes it to the cache. Each native cover painter keeps its own song binding, so late results cannot replace another song's outgoing transition. Missing metadata, no match, HTTP errors and conversion errors leave the native static cover active. No matches/mismatches are retried after 24 hours; failures after 10 minutes during the current plugin session. Duplicate jobs for an album are suppressed.

Cache files are keyed by a SHA-256 hash of normalized album artist and full album name under `cache/animated-artwork` beside the plugin's config.json (or inside the plugin directory on older configuration APIs). Music folders are never modified. Cached MP4s survive restarts. Turning the switch off stops queued work and active conversion, removes incomplete downloads, and returns to local-only lookup. Search uses 10-second connect and 25-second read timeouts; conversion is limited to 90 seconds, 120 seconds of video and 100 MiB output. Cache files remain until removed manually while the mod is disabled.

Enabling this setting sends album artist and album names to artwork.m8tec.top and downloads media from Apple's CDN. This optional service may be unavailable or return no exact match. Source/API documentation: https://github.com/m8tec/apple-music-animated-artworks . The mod remains usable with local videos without that service.

Regression tests: `tests/OnlineArtworkTest.java` covers disabled-worker behavior, per-album deduplication and cache reuse, metadata matching, late-result isolation, cancellation and cleanup. `tests/OnlineArtworkLiveTest.java` is an opt-in network smoke test requiring a cache directory and FFmpeg executable as arguments.

### Third-party attribution and licensing

The online artwork feature uses the public API of [Apple Music Animated Artworks by m8tec](https://github.com/m8tec/apple-music-animated-artworks), hosted at [artwork.m8tec.top](https://artwork.m8tec.top/). Its server source code is not bundled with this plugin.

SPW Animated Album Art and its independently written API client remain licensed under the [MIT License](LICENSE). No upstream software license was identified for the external project during review; this attribution does not grant permission or claim that the service is MIT-licensed. The project's MIT License does not cover the external service, downloaded Apple Music artwork, or third-party trademarks. Service use and artwork rights remain subject to the applicable terms and rights holders.

See [Third-party notices](licenses/THIRD_PARTY_NOTICES.md) for details. FFmpeg's separate license notices are provided in [licenses/](licenses/).

