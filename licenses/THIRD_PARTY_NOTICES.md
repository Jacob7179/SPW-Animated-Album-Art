# Third-party notices

## SPW Animated Album Art

Copyright (c) 2026 Jacob.

SPW Animated Album Art, including its independently written online artwork lookup and caching client, is licensed under the GNU Affero General Public License, version 3 only (AGPL-3.0-only). See LICENSE for the full license text.

## Online Apple Music animated artwork lookup

The optional online-fetch feature uses the public REST API provided by **Apple Music Animated Artworks**, maintained by **m8tec**:

- Project: https://github.com/m8tec/apple-music-animated-artworks
- Service: https://artwork.m8tec.top/
- Endpoint used: GET /api/v1/artwork/search?artist=...&album=...

The plugin submits album artist and album names, checks the returned metadata, and downloads the returned square artwork stream through FFmpeg into its local plugin cache. Users can disable online fetching in the plugin settings.

The service's server source code is not bundled with this plugin. This attribution does not assert that the external project is MIT-licensed or that its maintainer has granted permission. No upstream software license was identified when the integration was reviewed on 2026-10-19. Service access remains subject to any applicable provider terms and usage limits.

The plugin's AGPL-3.0-only license covers its own software. It does not license the external service, Apple Music content, downloaded artwork, or third-party trademarks. Rights in artwork remain with their respective rights holders; obtaining a file does not grant redistribution rights. This plugin is not affiliated with or endorsed by Apple or m8tec.

## FFmpeg

Bundled FFmpeg is governed by its own license notices in the licenses directory, separately from this project's AGPL-3.0-only license.
