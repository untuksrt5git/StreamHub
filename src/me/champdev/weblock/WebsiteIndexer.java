package me.champdev.weblock;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded, same-site HTML indexer for user-added websites.
 * It deliberately does not execute JavaScript or bypass logins/anti-bot pages.
 */
final class WebsiteIndexer {
    private static final int MAX_PAGES = 18;
    private static final int MAX_BYTES = 320 * 1024;
    private static final int MAX_LINKS = 180;
    private static final int MAX_MEDIA = 40;
    private static final int MAX_ENRICHED_EMBEDS = 12;
    private static final int MAX_DEPTH = 2;
    private static final long TIMEOUT = 7000L;

    private static final Pattern ANCHOR = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern HREF = Pattern.compile("(?is)\\bhref\\s*=\\s*(['\\\"])(.*?)\\1");
    private static final Pattern TITLE = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title\\s*>");
    private static final Pattern H1 = Pattern.compile("(?is)<h1\\b[^>]*>(.*?)</h1\\s*>");
    private static final Pattern LINK_TAG = Pattern.compile("(?is)<link\\b([^>]*)>");
    private static final Pattern META = Pattern.compile("(?is)<meta\\b([^>]+)>");
    private static final Pattern ATTR = Pattern.compile("(?is)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(['\\\"])(.*?)\\2");
    private static final Pattern MEDIA_TAG = Pattern.compile("(?is)<(?:video|source)\\b([^>]*)>");
    private static final Pattern EMBED_TAG = Pattern.compile("(?is)<iframe\\b([^>]*)>");
    private static final Pattern SRC = Pattern.compile("(?is)\\bsrc\\s*=\\s*(['\\\"])(.*?)\\1");
    private static final Pattern CANONICAL = Pattern.compile("(?is)<link\\b([^>]*rel\\s*=\\s*(['\\\"])canonical\\2[^>]*)>");
    private static final Pattern SKIP = Pattern.compile("(?i)(login|logout|register|signup|account|profile|privacy|terms|contact|about|javascript:|mailto:|tel:|\\.pdf$|\\.(jpg|jpeg|png|gif|webp|css|js|woff2?|ttf|svg|ico)$)");

    private WebsiteIndexer() { }

    static String fileName(String id) {
        String safe = id == null ? "site" : id.replaceAll("[^a-zA-Z0-9_-]", "");
        return "site-index-" + safe + ".json";
    }

    static JSONObject scan(Context context, String siteId, String startUrl) {
        JSONObject result = new JSONObject();
        JSONArray pages = new JSONArray(), media = new JSONArray(), embeds = new JSONArray(), videos = new JSONArray();
        try {
            URL start = new URL(startUrl);
            String rootHost = host(start.getHost());
            ArrayList<Task> queue = new ArrayList<>();
            Set<String> queued = new LinkedHashSet<>(), visited = new LinkedHashSet<>();
            queue.add(new Task(start.toString(), 0));
            queued.add(cleanUrl(start.toString()));
            int checked = 0, failed = 0;

            while (!queue.isEmpty() && checked < MAX_PAGES) {
                Task task = queue.remove(0);
                String key = cleanUrl(task.url);
                if (!visited.add(key)) continue;
                Page page = fetch(task.url);
                checked++;
                if (page == null) { failed++; continue; }
                if (task.depth == 0) rootHost = host(new URL(page.url).getHost()); // follow a site's redirect before crawling its new domain

                JSONObject entry = new JSONObject();
                entry.put("url", page.url).put("title", page.title.isEmpty() ? label(page.url) : page.title)
                    .put("description", page.description).put("poster", page.poster)
                    .put("kind", classify(page.url, page.title)).put("indexedAt", System.currentTimeMillis());
                pages.put(entry);

                for (String embed : page.embeds) {
                    if (embeds.length() < MAX_MEDIA && !embeds.toString().contains(embed)) embeds.put(embed);
                    if (isLikelyAdEmbed(embed)) continue;
                    boolean duplicateVideo = false;
                    for (int vi = 0; vi < videos.length(); vi++) {
                        JSONObject oldVideo = videos.optJSONObject(vi);
                        if (oldVideo != null && embed.equals(oldVideo.optString("embedUrl"))) { duplicateVideo = true; break; }
                    }
                    if (!duplicateVideo && videos.length() < MAX_MEDIA) {
                        // Inspect the iframe document: the parent's social image is often a banner/ad.
                        Page embedded = videos.length() < MAX_ENRICHED_EMBEDS ? fetchEmbedMetadata(embed) : null;
                        String title = firstUseful(embedded == null ? "" : embedded.ogTitle,
                            embedded == null ? "" : embedded.heading, embedded == null ? "" : embedded.title,
                            page.ogTitle, page.heading, page.title, label(page.url));
                        String poster = firstPoster(embedded == null ? "" : embedded.poster, page.poster);
                        String description = firstUseful(embedded == null ? "" : embedded.description, page.description);
                        String logo = firstUseful(embedded == null ? "" : embedded.logo, page.logo);
                        String brand = firstUseful(embedded == null ? "" : embedded.siteName, host(new URL(embed).getHost()));
                        videos.put(new JSONObject().put("title", title).put("poster", poster).put("logo", logo)
                            .put("brand", brand).put("description", description).put("pageUrl", page.url)
                            .put("embedUrl", embed).put("sourceHost", host(new URL(embed).getHost()))
                            .put("metadataSource", embedded == null ? "parent-page-fallback" : "embed-page"));
                    }
                }

                for (String m : page.media) {
                    if (media.length() >= MAX_MEDIA) break;
                    boolean exists = false;
                    for (int i = 0; i < media.length(); i++) if (m.equals(media.optString(i))) { exists = true; break; }
                    if (!exists) media.put(m);
                }

                if (task.depth >= MAX_DEPTH || queue.size() >= MAX_LINKS) continue;
                for (String link : page.links) {
                    if (queued.size() >= MAX_LINKS || queue.size() >= MAX_LINKS) break;
                    try {
                        URL u = new URL(new URL(page.url), link);
                        String h = host(u.getHost());
                        String path = u.getPath() == null ? "" : u.getPath();
                        if (!("http".equalsIgnoreCase(u.getProtocol()) || "https".equalsIgnoreCase(u.getProtocol()))) continue;
                        if (!rootHost.equals(h) || SKIP.matcher(path).find()) continue;
                        String normalized = cleanUrl(u.toString());
                        if (queued.add(normalized)) queue.add(new Task(u.toString(), task.depth + 1));
                    } catch (Exception ignored) { }
                }
            }

            result.put("version", 2).put("siteId", siteId).put("rootUrl", startUrl)
                .put("finalUrl", visited.isEmpty() ? startUrl : pages.length() > 0 ? pages.optJSONObject(0).optString("url", startUrl) : startUrl)
                .put("generatedAt", System.currentTimeMillis()).put("checkedPages", checked)
                .put("failedPages", failed).put("pages", pages).put("media", media).put("embeds", embeds).put("videos", videos)
                .put("status", pages.length() > 0 ? "indexed" : "unavailable")
                .put("limitations", "Indeks HTML statis; situs yang merender katalog hanya melalui JavaScript, login, atau proteksi bot mungkin tidak terbaca.");
            File out = new File(context.getFilesDir(), fileName(siteId));
            atomicWrite(out, result.toString(2));
        } catch (Exception e) {
            try { result.put("version", 2).put("siteId", siteId).put("rootUrl", startUrl)
                .put("generatedAt", System.currentTimeMillis()).put("status", "error")
                .put("error", e.getClass().getSimpleName()).put("pages", pages).put("media", media); }
            catch (Exception ignored) { }
        }
        return result;
    }

    private static Page fetch(String address) {
        HttpURLConnection c = null;
        try {
            c = Warp.open(address);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout((int) TIMEOUT);
            c.setReadTimeout((int) TIMEOUT);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Safari/537.36");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            int code = c.getResponseCode();
            String type = c.getContentType();
            if (code < 200 || code >= 400 || (type != null && !type.toLowerCase(Locale.ROOT).contains("html"))) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[8192];
                int n, total = 0;
                while ((n = in.read(b)) > 0 && total < MAX_BYTES) { out.write(b, 0, n); total += n; }
            }
            String html = out.toString("UTF-8");
            Page p = new Page();
            p.url = c.getURL().toString();
            p.title = clean(first(TITLE, html));
            p.ogTitle = meta(html, "og:title");
            if (p.ogTitle.isEmpty()) p.ogTitle = jsonLd(html, "name");
            p.heading = clean(first(H1, html));
            p.siteName = meta(html, "og:site_name");
            p.logo = logo(html, p.url);
            if (p.logo.isEmpty()) p.logo = absolute(p.url, jsonLd(html, "logo"));
            p.description = meta(html, "description");
            if (p.description.isEmpty()) p.description = meta(html, "og:description");
            p.poster = absolute(p.url, meta(html, "og:image"));
            if (p.poster.isEmpty()) p.poster = absolute(p.url, meta(html, "twitter:image"));
            if (p.poster.isEmpty()) p.poster = absolute(p.url, jsonLd(html, "image"));
            if (p.poster.isEmpty()) p.poster = videoPoster(html, p.url);
            if (!goodPoster(p.poster)) p.poster = "";
            p.links.addAll(links(html));
            p.media.addAll(media(html, p.url));
            p.embeds.addAll(embeds(html, p.url));
            return p;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Bounded fetch of an iframe's own HTML; never crawls its links or nested frames. */
    private static Page fetchEmbedMetadata(String address) {
        HttpURLConnection c = null;
        try {
            c = Warp.open(address);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(3500);
            c.setReadTimeout(3500);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Safari/537.36");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            int code = c.getResponseCode();
            String type = c.getContentType();
            if (code < 200 || code >= 400 || (type != null && !type.toLowerCase(Locale.ROOT).contains("html"))) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192]; int n, total = 0;
                while ((n = in.read(buf)) > 0 && total < 192 * 1024) {
                    int take = Math.min(n, 192 * 1024 - total); out.write(buf, 0, take); total += take;
                }
            }
            String html = out.toString("UTF-8");
            Page p = new Page(); p.url = c.getURL().toString();
            p.title = clean(first(TITLE, html)); p.ogTitle = meta(html, "og:title");
            if (p.ogTitle.isEmpty()) p.ogTitle = jsonLd(html, "name");
            p.heading = clean(first(H1, html)); p.siteName = meta(html, "og:site_name");
            p.logo = logo(html, p.url);
            if (p.logo.isEmpty()) p.logo = absolute(p.url, jsonLd(html, "logo"));
            p.description = meta(html, "og:description");
            if (p.description.isEmpty()) p.description = meta(html, "description");
            p.poster = absolute(p.url, meta(html, "og:image"));
            if (p.poster.isEmpty()) p.poster = absolute(p.url, meta(html, "twitter:image"));
            if (p.poster.isEmpty()) p.poster = absolute(p.url, jsonLd(html, "image"));
            if (p.poster.isEmpty()) p.poster = videoPoster(html, p.url);
            if (!goodPoster(p.poster)) p.poster = "";
            return p;
        } catch (Exception ignored) { return null; }
        finally { if (c != null) c.disconnect(); }
    }

    /** Read Schema.org JSON-LD fields (including nested ImageObject url values). */
    private static String jsonLd(String html, String wanted) {
        Matcher scripts = Pattern.compile("(?is)<script\\\\b[^>]*type\\\\s*=\\\\s*(['\\\"])[^'\\\"]*ld\\\\+json[^'\\\"]*\\\\1[^>]*>(.*?)</script\\\\s*>").matcher(html);
        while (scripts.find()) {
            try {
                String value = jsonLdValue(new JSONObject(scripts.group(2)), wanted, 0);
                if (!value.isEmpty()) return value;
            } catch (Exception ignored) { }
        }
        return "";
    }

    private static String jsonLdValue(Object node, String wanted, int depth) {
        if (node == null || depth > 8) return "";
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            Object value = object.opt(wanted);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
            if (value instanceof JSONObject) {
                JSONObject image = (JSONObject) value;
                String url = image.optString("url", image.optString("contentUrl", ""));
                if (!url.isEmpty()) return url;
            }
            if (value instanceof JSONArray) {
                String found = jsonLdValue(value, wanted, depth + 1);
                if (!found.isEmpty()) return found;
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String found = jsonLdValue(object.opt(keys.next()), wanted, depth + 1);
                if (!found.isEmpty()) return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                String found = jsonLdValue(array.opt(i), wanted, depth + 1);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private static String videoPoster(String html, String base) {
        Matcher tags = MEDIA_TAG.matcher(html);
        while (tags.find()) {
            String value = attr(tags.group(1), "poster");
            if (!value.isEmpty()) {
                String absolute = absolute(base, value);
                if (goodPoster(absolute)) return absolute;
            }
        }
        return "";
    }

    private static String logo(String html, String base) {
        String value = meta(html, "og:logo");
        if (value.isEmpty()) {
            Matcher m = LINK_TAG.matcher(html);
            while (m.find()) if (attr(m.group(1), "rel").toLowerCase(Locale.ROOT).contains("icon")) {
                value = attr(m.group(1), "href"); if (!value.isEmpty()) break;
            }
        }
        return absolute(base, value);
    }

    private static String absolute(String base, String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try { return new URL(new URL(base), decode(value.trim())).toString(); }
        catch (Exception e) { return ""; }
    }

    private static String firstUseful(String... values) {
        for (String value : values) {
            String v = clean(value);
            if (!v.isEmpty() && !v.matches("(?i)^(home|watch|play|video|stream|embed|untitled|loading|advertisement|ads?)$")) return v;
        }
        return "";
    }

    private static String firstPoster(String... values) {
        for (String value : values) if (goodPoster(value)) return value;
        return "";
    }

    private static boolean goodPoster(String value) {
        if (value == null || !value.matches("(?i)^https?://.+")) return false;
        String s = value.toLowerCase(Locale.ROOT);
        return !s.matches(".*(doubleclick|googlesyndication|adservice|popads|exoclick|ads?[-_/ .]|banner[-_/ .]|advert|tracking|pixel\\.).*");
    }

    private static boolean isLikelyAdEmbed(String value) {
        try {
            URL u = new URL(value); String h = host(u.getHost()), p = u.getPath().toLowerCase(Locale.ROOT);
            return h.matches(".*(doubleclick|googlesyndication|adservice|popads|exoclick|adsterra|propellerads).*")
                || p.matches(".*(adserver|/ads?[/_-]|banner|popup|tracking).*");
        } catch (Exception e) { return true; }
    }

    private static Set<String> links(String html) {
        Set<String> out = new LinkedHashSet<>();
        Matcher a = ANCHOR.matcher(html);
        while (a.find() && out.size() < MAX_LINKS) {
            Matcher h = HREF.matcher(a.group(1));
            if (!h.find()) continue;
            String link = decode(h.group(2).trim());
            if (link.isEmpty() || link.startsWith("#")) continue;
            if (link.matches("(?i)^(javascript|mailto|tel|data):.*")) continue;
            out.add(link);
        }
        return out;
    }

    private static Set<String> media(String html, String base) {
        Set<String> out = new LinkedHashSet<>();
        Matcher tag = MEDIA_TAG.matcher(html);
        while (tag.find() && out.size() < MAX_MEDIA) {
            Matcher src = SRC.matcher(tag.group(1));
            if (!src.find()) continue;
            String value = decode(src.group(2).trim());
            try {
                URL u = new URL(new URL(base), value);
                String path = u.getPath().toLowerCase(Locale.ROOT);
                if (path.matches(".*\\.(m3u8|mpd|mp4|webm|m4v|flv)$")) out.add(u.toString());
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static Set<String> embeds(String html, String base) {
        Set<String> out = new LinkedHashSet<>();
        Matcher tag = EMBED_TAG.matcher(html);
        while (tag.find() && out.size() < MAX_MEDIA) {
            Matcher src = SRC.matcher(tag.group(1));
            if (!src.find()) continue;
            String value = decode(src.group(2).trim());
            try {
                URL u = new URL(new URL(base), value);
                if ("http".equalsIgnoreCase(u.getProtocol()) || "https".equalsIgnoreCase(u.getProtocol()))
                    out.add(u.toString());
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static String meta(String html, String wanted) {
        Matcher m = META.matcher(html);
        while (m.find()) {
            String name = attr(m.group(1), "name");
            String prop = attr(m.group(1), "property");
            if (wanted.equalsIgnoreCase(name) || wanted.equalsIgnoreCase(prop)) return clean(attr(m.group(1), "content"));
        }
        return "";
    }

    private static String attr(String attrs, String key) {
        Matcher m = ATTR.matcher(attrs);
        while (m.find()) if (key.equalsIgnoreCase(m.group(1))) return m.group(3);
        return "";
    }

    private static String first(Pattern p, String value) {
        Matcher m = p.matcher(value == null ? "" : value);
        return m.find() ? m.group(1) : "";
    }

    private static String classify(String url, String title) {
        String s = (url + " " + title).toLowerCase(Locale.ROOT);
        if (s.matches(".*(episode|season|\\/ep[-_ ]?\\d+|\\/anime).*")) return "episode";
        if (s.matches(".*(movie|film|watch|nonton|stream|play).*")) return "video";
        if (s.matches(".*(series|drama|tv-show|tv).*")) return "series";
        return "page";
    }

    private static String label(String url) {
        try {
            String p = new URL(url).getPath();
            if (p == null || p.isEmpty() || "/".equals(p)) return new URL(url).getHost();
            String v = p.substring(p.lastIndexOf('/') + 1).replaceAll("[-_]+", " ");
            return v.isEmpty() ? new URL(url).getHost() : v;
        } catch (Exception e) { return url; }
    }

    private static String clean(String s) {
        if (s == null) return "";
        return decode(s.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim());
    }

    private static String decode(String s) {
        return s == null ? "" : s.replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static String host(String h) {
        return h == null ? "" : h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }

    private static String cleanUrl(String value) {
        try {
            Uri u = Uri.parse(value);
            return u.buildUpon().fragment(null).build().toString().replaceAll("/+$", "");
        } catch (Exception e) { return value; }
    }

    private static void atomicWrite(File f, String value) throws Exception {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(value.getBytes("UTF-8"));
            out.getFD().sync();
        }
        if (f.exists() && !f.delete()) throw new java.io.IOException("replace failed");
        if (!tmp.renameTo(f)) throw new java.io.IOException("rename failed");
    }

    private static final class Task {
        final String url; final int depth;
        Task(String u, int d) { url = u; depth = d; }
    }
    private static final class Page {
        String url = "", title = "", ogTitle = "", heading = "", siteName = "", logo = "", description = "", poster = "";
        final Set<String> links = new LinkedHashSet<>(), media = new LinkedHashSet<>(), embeds = new LinkedHashSet<>();
    }
}
