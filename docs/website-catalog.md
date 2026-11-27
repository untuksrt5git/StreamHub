# Website catalog and playback

StreamHub keeps website catalogs on-device. A user-added website is registered by its original URL, while the resolved URL and canonical host are tracked separately so a redirect or domain move does not change the saved identity.

## Catalog pipeline

1. `CustomSites` resolves website metadata and follows HTTP redirects.
2. `WebsiteIndexer` performs a bounded static HTML scan (same-site pages, limited depth and request size).
3. `SiteScanActivity` opens the public site in a JavaScript-enabled WebView. It waits for client-rendered content and also supports manual scanning after the user opens a Videos/category page.
4. `SiteAdapters` identifies known website families (currently OMG, Supjav and LK21-family) and provides scan guidance. Unknown sites use the generic profile.
5. Results are merged into the site's local JSON index. A failed/empty JavaScript scan does not erase the previous catalog.

## Catalog item format

Newly scanned video entries use `streamhub.catalog.v1` and include:

- `id`: stable local identifier derived from the source page URL.
- `title`, `poster`, `description`.
- `pageUrl`: the page that lists or describes the video.
- `embedUrl`: the discovered player/embed URL when present.
- `sourceHost` / `brand`: the actual embed host, not automatically the parent website.
- `category`: best-effort `catalog`, `video`, `series`, or `episode`.
- `updatedAt` and `metadataSource`.

The catalog is stored under the app's private files directory. It is not uploaded to a third-party service.

## Playback choices

From a catalog card the user can choose:

- **Putar di StreamHub**: opens the website/player page in StreamHub's WebView. This is the default for embedded players.
- **Buka halaman video sumber**: opens the listing/detail page instead of the embed URL.
- **Coba player native**: passes the URL and the listing page as Referer to the native Media3 player. This is intended for direct media URLs; ordinary HTML embed pages may not work in a native player.

The native player is not a universal replacement for a site's own player. Some providers require JavaScript, cookies, signed URLs, DRM, or request headers that are only available inside the website session.

## Known limits and security

- The scanner does not log in, defeat CAPTCHA/anti-bot protections, bypass paywalls, or extract protected account sessions.
- DOM scanning depends on the page actually rendering its catalog. If the homepage is only a shell, navigate to Videos/Latest/category and press **Pindai halaman ini**.
- Adapter profiles are a lightweight registry, not full site-specific API scrapers. Site-specific selectors and endpoints should only be added after inspecting the site's public structure and testing them.
- The Guardian-style HLS proxy is **not part of this Android-only change**. A proxy requires a separately deployed backend. If added later, it must use HTTPS, a strict host allowlist, URL/IP validation against SSRF, short-lived opaque source tokens, bounded playlist/segment sizes, and must never put cookies or Referer values in URLs or logs.
- Domain discovery follows ordinary redirects and canonical URLs. It cannot predict an unpublished future domain change when the old domain no longer redirects or publishes a new address.
