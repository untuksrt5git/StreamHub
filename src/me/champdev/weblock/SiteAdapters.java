package me.champdev.weblock;

import android.net.Uri;
import java.util.Locale;

/** Offline website adapter registry. Profiles guide scanning; no access controls are bypassed. */
final class SiteAdapters {
    static final int VERSION = 1;
    static final class Profile {
        final String id, name, host, navigationHint;
        Profile(String id, String name, String host, String hint) {
            this.id=id; this.name=name; this.host=host; this.navigationHint=hint;
        }
    }
    private SiteAdapters() { }
    static Profile forUrl(String address) {
        String h=host(address);
        if (h.equals("supjav.com") || h.endsWith(".supjav.com"))
            return new Profile("supjav","Supjav",h,"Videos / kategori");
        if (h.equals("omg.xxx") || h.equals("omg.porn") || h.endsWith(".omg.xxx") || h.endsWith(".omg.porn"))
            return new Profile("omg","OMG",h,"Videos / Latest / kategori");
        if (h.contains("lk21") || h.contains("layarkaca"))
            return new Profile("lk21-family","LK21",h,"Film / Series / genre");
        return new Profile("generic",h.isEmpty()?"Website":h,h,"Videos / kategori / halaman katalog");
    }
    static String host(String address) {
        try {
            String h=Uri.parse(address).getHost();
            return h==null?"":h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.","");
        } catch (Exception e) { return ""; }
    }
}
