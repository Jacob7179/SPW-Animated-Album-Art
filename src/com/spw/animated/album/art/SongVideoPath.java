package com.spw.animated.album.art;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;

/** Accept both normal paths and the raw/encoded file URLs returned by Salt Player. */
public final class SongVideoPath {
    private SongVideoPath() {}
    public static Path songPath(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.regionMatches(true, 0, "file:", 0, 5)) return Paths.get(value);
        URI uri;
        try { uri = new URI(value); }
        catch (URISyntaxException rawUrl) {
            // Salt Player can emit raw spaces and literal percent signs in file URLs.
            // Build the URI from its decoded path so those characters are quoted safely.
            String rest = value.substring(5);
            String authority = null;
            if (rest.startsWith("//")) {
                rest = rest.substring(2);
                int slash = rest.indexOf('/');
                if (slash < 0) return null;
                authority = rest.substring(0, slash);
                if (authority.isEmpty()) authority = null;
                rest = rest.substring(slash);
            }
            try { uri = new URI("file", authority, rest, null); }
            catch (URISyntaxException invalid) { throw new IllegalArgumentException("Invalid song file URL", invalid); }
        }
        return Paths.get(uri);
    }
    public static Path find(String value) {
        Path song = songPath(value);
        if (song == null) return null;
        Path folder = song.toAbsolutePath().getParent();
        if (folder == null) return null;
        Path video = folder.resolve("square_animated_artwork.mp4");
        return Files.isRegularFile(video) ? video : null;
    }
}
