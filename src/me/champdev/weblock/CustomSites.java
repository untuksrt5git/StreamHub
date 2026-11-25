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
            try {
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
                    JSONObject idx = new JSONObject().put("version", 1).put("sources",
                        new JSONArray().put(new JSONObject().put("type", "custom-sites").put("url", "custom-sites.json")));
                    atomicWrite(new File(app.getFilesDir(), "user-index.json"), idx.toString(2));
                    renameFavorite(address, item.optString("name"));
                }
            } catch (Exception ignored) { }
            if (callback != null) {
                String msg = "Website ditambahkan: " + item.optString("name")
                    + (item.optBoolean("metadataFetched") ? ". Metadata dan indeks JSON tersimpan." :
                    ". Tersimpan sebagai website; metadata tidak dapat diambil saat ini.");
                App.main.post(() -> callback.done(msg));
            }
        });
    }

    private static JSONObject metadata(String address) {
        String html = "", finalUrl = address;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(address).openConnection();
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
        String iconPath = match(html, "(?is)<link[^>]+rel=[\\"'][^>]*icon[^>]*href=[\\"']([^\\"']+)");
        if (iconPath.isEmpty()) iconPath = "/favicon.ico";
        String icon;
        try { icon = new URL(u.toString(), iconPath).toString(); }
        catch (Exception e) { icon = u.getScheme() + "://" + host + "/favicon.ico"; }
        JSONObject o = new JSONObject();
        try {
            o.put("id", id(address)).put("name", clean(name)).put("url", address).put("currentUrl", finalUrl)
             .put("canonicalUrl", match(html, "(?is)<link[^>]+rel=[\\"']canonical[\\"'][^>]+href=[\\"']([^\\"']+)"))
             .put("icon", icon).put("description", clean(description)).put("category", "website")
             .put("tags", new JSONArray().put("custom")).put("aliases", new JSONArray())
             .put("autoUpdate", true).put("metadataFetched", !html.isEmpty())
             .put("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) { }
        return o;
    }

    private static String meta(String html, String key) {
        String v = match(html, "(?is)<meta[^>]+(?:property|name)=[\\"']" + Pattern.quote(key) +
                "[\\"'][^>]+content=[\\"']([^\\"']*)[\\"']");
        if (v.isEmpty()) v = match(html, "(?is)<meta[^>]+content=[\\"']([^\\"']*)[\\"'][^>]+(?:property|name)=[\\"']" + Pattern.quote(key) + "[\\"']");
        return clean(v);
    }
    private static String match(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s == null ? "" : s);
        return m.find() ? clean(m.group(1)) : "";
    }
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("(?is)<[^>]+>", " ").replace("&amp;", "&")
            .replace("&quot;", "\\"").replace("&#39;", "'").replaceAll("\\s+", " ").trim();
    }
    private static String id(String s) {
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
    private static void renameFavorite(String url, String name) throws Exception {
        if (name == null || name.isEmpty()) return;
        JSONArray a = new JSONArray(App.prefs.getString("favs", "[]")), out = new JSONArray();
        for (int i=0;i<a.length();i++) {
            JSONObject o = a.getJSONObject(i);
            if (url.equals(o.optString("url"))) o.put("name", name);
            out.put(o);
        }
        App.prefs.edit().putString("favs", out.toString()).apply();
    }
}
