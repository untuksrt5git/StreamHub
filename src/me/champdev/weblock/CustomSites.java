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
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local registry for user-added websites. Catalog extraction needs a site-specific adapter. */
final class CustomSites {
    interface Callback { void done(String message); }
    private CustomSites() { }

    static void add(Context context, String raw, Callback callback) {
        final String address;
        try {
            String value = raw.trim();
            if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
            Uri uri = Uri.parse(value);
            if (uri.getHost() == null || uri.getHost().isEmpty() || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
            address = uri.buildUpon().fragment(null).build().toString();
        } catch (RuntimeException e) {
            if (callback != null) callback.done("Alamat website tidak valid.");
            return;
        }
        Sites.addCustom(address);
        Context app = context.getApplicationContext();
        App.io.execute(() -> {
            JSONObject item = metadata(address);
            JSONObject scan = WebsiteIndexer.scan(app, item.optString("id"), item.optString("currentUrl", address));
            try {
                item.put("indexFile", WebsiteIndexer.fileName(item.optString("id")))
                    .put("indexedPages", scan.optJSONArray("pages") == null ? 0 : scan.optJSONArray("pages").length())
                    .put("indexedMedia", scan.optJSONArray("media") == null ? 0 : scan.optJSONArray("media").length())
                    .put("indexStatus", scan.optString("status", "unavailable"))
                    .put("lastIndexedAt", System.currentTimeMillis())
                    .put("currentUrl", scan.optString("finalUrl", item.optString("currentUrl", address)));
                synchronized (CustomSites.class) {
                    File f = new File(app.getFilesDir(), "custom-sites.json");
                    JSONObject root = read(f);
                    JSONArray old = root.optJSONArray("sites");
                    JSONArray sites = new JSONArray();
                    boolean found = false;
                    for (int i = 0; old != null && i < old.length(); i++) {
                        JSONObject v = old.optJSONObject(i);
                        if (v == null) continue;
                        if (address.equals(v.optString("url"))) { sites.put(item); found = true; }
                        else sites.put(v);
                    }
                    if (!found) sites.put(item);
                    root.put("version", 1).put("updatedAt", System.currentTimeMillis()).put("sites", sites);
                    atomicWrite(f, root.toString(2));
                    JSONArray indexSources = new JSONArray();
                    indexSources.put(new JSONObject().put("type", "custom-sites").put("url", "custom-sites.json"));
                    for (int i = 0; i < sites.length(); i++) {
                        JSONObject entry = sites.optJSONObject(i);
                        if (entry == null || entry.optString("indexFile").isEmpty()) continue;
                        indexSources.put(new JSONObject().put("type", "site-pages")
                            .put("siteId", entry.optString("id")).put("name", entry.optString("name"))
                            .put("websiteUrl", entry.optString("url")).put("url", entry.optString("indexFile")));
                    }
                    JSONObject idx = new JSONObject().put("version", 1).put("updatedAt", System.currentTimeMillis())
                        .put("sources", indexSources);
                    atomicWrite(new File(app.getFilesDir(), "user-index.json"), idx.toString(2));
                    renameFavorite(address, item.optString("name"), item.optString("currentUrl"), item.optString("indexFile"));
                }
            } catch (Exception ignored) { }
            if (callback != null) {
                int pageCount = scan.optJSONArray("pages") == null ? 0 : scan.optJSONArray("pages").length();
                int mediaCount = scan.optJSONArray("media") == null ? 0 : scan.optJSONArray("media").length();
                String msg = "Website ditambahkan: " + item.optString("name") + ". "
                    + pageCount + " halaman dan " + mediaCount + " tautan media ditemukan. "
                    + (pageCount == 0 ? "Situs mungkin memakai JavaScript, login, atau proteksi bot." : "Indeks JSON lokal tersimpan.");
                App.main.post(() -> callback.done(msg));
            }
        });
    }

    /** Refresh up to three stale custom sites per app start. Redirects are followed so moved domains update in the local index. */
    static void refresh(Context context) {
        long now = System.currentTimeMillis();
        if (now - App.prefs.getLong("customSitesRefreshAt", 0) < 12L * 3600 * 1000) return;
        App.prefs.edit().putLong("customSitesRefreshAt", now).apply();
        Context app = context.getApplicationContext();
        App.io.execute(() -> {
            synchronized (CustomSites.class) {
                File file = new File(app.getFilesDir(), "custom-sites.json");
                JSONObject root = read(file);
                JSONArray sites = root.optJSONArray("sites");
                if (sites == null) return;
                JSONArray updated = new JSONArray();
                int refreshed = 0;
                for (int i = 0; i < sites.length(); i++) {
                    JSONObject old = sites.optJSONObject(i);
                    if (old == null) continue;
                    JSONObject item = old;
                    long last = old.optLong("lastIndexedAt", 0);
                    // Version 2 enriches iframe pages with their own title/poster/logo. Upgrade older cached indexes once.
                    String oldIndexName = old.optString("indexFile", WebsiteIndexer.fileName(old.optString("id")));
                    JSONObject oldIndex = read(new File(app.getFilesDir(), oldIndexName));
                    boolean needsMetadataUpgrade = oldIndex.optInt("version", 0) < 2;
                    if (refreshed < 3 && old.optBoolean("autoUpdate", true) &&
                            (needsMetadataUpgrade || now - last > 24L * 3600 * 1000)) {
                        String original = old.optString("url");
                        JSONObject fresh = metadata(original);
                        if (fresh.optBoolean("metadataFetched")) {
                            JSONObject scan = WebsiteIndexer.scan(app, fresh.optString("id"), fresh.optString("currentUrl", original));
                            try {
                                int pageCount = scan.optJSONArray("pages") == null ? 0 : scan.optJSONArray("pages").length();
                                int mediaCount = scan.optJSONArray("media") == null ? 0 : scan.optJSONArray("media").length();
                                String beforeHost = Uri.parse(original).getHost();
                                String afterHost = Uri.parse(fresh.optString("currentUrl", original)).getHost();
                                fresh.put("indexFile", WebsiteIndexer.fileName(fresh.optString("id")))
                                    .put("indexedPages", pageCount).put("indexedMedia", mediaCount)
                                    .put("indexStatus", scan.optString("status", "unavailable"))
                                    .put("lastIndexedAt", now).put("lastCheckedAt", now)
                                    .put("domainChanged", beforeHost != null && afterHost != null && !beforeHost.equalsIgnoreCase(afterHost));
                                item = fresh;
                                renameFavorite(original, fresh.optString("name"), fresh.optString("currentUrl"), fresh.optString("indexFile"));
                                refreshed++;
                            } catch (Exception ignored) { }
                        }
                    }
                    updated.put(item);
                }
                try {
                    root.put("version", 1).put("updatedAt", now).put("sites", updated);
                    atomicWrite(file, root.toString(2));
                    JSONArray refs = new JSONArray().put(new JSONObject().put("type", "custom-sites").put("url", "custom-sites.json"));
                    for (int i = 0; i < updated.length(); i++) {
                        JSONObject entry = updated.optJSONObject(i);
                        if (entry == null || entry.optString("indexFile").isEmpty()) continue;
                        refs.put(new JSONObject().put("type", "site-pages").put("siteId", entry.optString("id"))
                            .put("name", entry.optString("name")).put("websiteUrl", entry.optString("url"))
                            .put("url", entry.optString("indexFile")));
                    }
                    JSONObject manifest = new JSONObject().put("version", 1).put("updatedAt", now).put("sources", refs);
                    atomicWrite(new File(app.getFilesDir(), "user-index.json"), manifest.toString(2));
                } catch (Exception ignored) { }
            }
        });
    }

    private static JSONObject metadata(String address) {
        String html = "", finalUrl = address;
        HttpURLConnection c = null;
        try {
            c = Warp.open(address);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Safari/537.36");
            c.connect(); finalUrl = c.getURL().toString();
            String type = c.getContentType();
            if (c.getResponseCode() >= 200 && c.getResponseCode() < 400 &&
                    (type == null || type.toLowerCase(Locale.ROOT).contains("html"))) {
                try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] b = new byte[8192]; int n, total = 0;
                    while ((n = in.read(b)) > 0 && total < 384 * 1024) { out.write(b, 0, n); total += n; }
                    html = out.toString("UTF-8");
                }
            }
        } catch (Exception ignored) { } finally { if (c != null) c.disconnect(); }
        Uri u = Uri.parse(finalUrl);
        String host = u.getHost() == null ? "website" : u.getHost().replaceFirst("(?i)^www\\.", "");
        String title = match(html, "(?is)<title[^>]*>(.*?)</title>");
        String name = meta(html, "og:site_name");
        if (name.isEmpty()) name = title;
        if (name.isEmpty()) name = host;
        String description = meta(html, "description");
        if (description.isEmpty()) description = meta(html, "og:description");
        String iconPath = match(html, "(?is)<link[^>]+rel=[\"'][^>]*icon[^>]*href=[\"']([^\"']+)");
        if (iconPath.isEmpty()) iconPath = "/favicon.ico";
        String icon;
        try { icon = new URL(new URL(u.toString()), iconPath).toString(); }
        catch (Exception e) { icon = u.getScheme() + "://" + host + "/favicon.ico"; }
        String canonical = absolute(finalUrl, match(html, "(?is)<link[^>]+rel=[\"']canonical[\"'][^>]+href=[\"']([^\"']+)"));
        String currentUrl = finalUrl;
        try {
            java.net.URL canonicalAddress = new java.net.URL(canonical);
            if (("http".equalsIgnoreCase(canonicalAddress.getProtocol()) || "https".equalsIgnoreCase(canonicalAddress.getProtocol()))
                    && canonicalAddress.getHost() != null && !canonicalAddress.getHost().isEmpty()
                    && u.getHost() != null && !canonicalAddress.getHost().equalsIgnoreCase(u.getHost()))
                currentUrl = canonical;
        } catch (Exception ignored) { }
        JSONObject o = new JSONObject();
        try {
            o.put("id", id(address)).put("name", clean(name)).put("url", address).put("currentUrl", currentUrl)
             .put("canonicalUrl", canonical)
             .put("domainChanged", !Uri.parse(address).getHost().equalsIgnoreCase(Uri.parse(currentUrl).getHost()))
             .put("icon", icon).put("description", clean(description)).put("category", "website")
             .put("tags", new JSONArray().put("custom")).put("aliases", new JSONArray())
             .put("autoUpdate", true).put("metadataFetched", !html.isEmpty())
             .put("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) { }
        return o;
    }

    private static String absolute(String base, String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try { return new java.net.URL(new java.net.URL(base), value.trim()).toString(); }
        catch (Exception e) { return value.trim(); }
    }

    private static String meta(String html, String key) {
        String v = match(html, "(?is)<meta[^>]+(?:property|name)=[\"']" + Pattern.quote(key) +
                "[\"'][^>]+content=[\"']([^\"']*)[\"']");
        if (v.isEmpty()) v = match(html, "(?is)<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+(?:property|name)=[\"']" + Pattern.quote(key) + "[\"']");
        return clean(v);
    }
    private static String match(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s == null ? "" : s);
        return m.find() ? clean(m.group(1)) : "";
    }
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("(?is)<[^>]+>", " ").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replaceAll("\\s+", " ").trim();
    }
    static String id(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder("site-");
            for (int i=0;i<8;i++) out.append(String.format(Locale.ROOT,"%02x",b[i]));
            return out.toString();
        } catch (Exception e) { return "site-" + Integer.toHexString(s.hashCode()); }
    }
    private static JSONObject read(File f) {
        try (InputStream in = new java.io.FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n; while ((n=in.read(b))>0) out.write(b,0,n);
            return new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) { return new JSONObject(); }
    }
    private static void atomicWrite(File f, String s) throws Exception {
        File tmp = new File(f.getParentFile(), f.getName()+".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(s.getBytes("UTF-8")); out.getFD().sync(); }
        if (f.exists() && !f.delete()) throw new java.io.IOException("replace failed");
        if (!tmp.renameTo(f)) throw new java.io.IOException("rename failed");
    }
    private static void renameFavorite(String url, String name, String currentUrl, String indexFile) throws Exception {
        JSONArray a = new JSONArray(App.prefs.getString("favs", "[]")), out = new JSONArray();
        for (int i=0;i<a.length();i++) {
            JSONObject o = a.getJSONObject(i);
            if (url.equals(o.optString("url"))) {
                if (name != null && !name.isEmpty()) o.put("name", name);
                if (currentUrl != null && !currentUrl.isEmpty()) o.put("currentUrl", currentUrl);
                if (indexFile != null && !indexFile.isEmpty()) o.put("indexFile", indexFile);
            }
            out.put(o);
        }
        App.prefs.edit().putString("favs", out.toString()).apply();
    }
}
