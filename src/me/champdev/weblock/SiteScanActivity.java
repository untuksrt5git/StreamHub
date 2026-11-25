package me.champdev.weblock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/** Visible WebView scanner for public catalogs rendered by JavaScript. It does not bypass logins or bot checks. */
public final class SiteScanActivity extends Activity {
    private WebView web;
    private TextView status;
    private String siteUrl, siteName, siteId;
    private boolean finished;
    private final String script =
        "(function(){try{" +
        "var bad=/(doubleclick|googlesyndication|adservice|popads|exoclick|adsterra|propellerads|banner|advert|sponsor|avatar|logo|pixel|tracking|popup)/i;" +
        "var selectors=['.posts.clearfix > .post > a.img[title]','.post','.post-item','.video-item','.movie-item','.anime-item','.drama-item','.item','.card','.thumb','.poster','.entry'];" +
        "var nodes=[];selectors.forEach(function(s){try{document.querySelectorAll(s).forEach(function(n){if(nodes.indexOf(n)<0)nodes.push(n);});}catch(e){}});" +
        "document.querySelectorAll('a[href]').forEach(function(a){if(a.querySelector('img')&&nodes.length<900){var p=a.closest('article,.post,.item,.card,.video-item,.movie-item,.entry')||a;if(nodes.indexOf(p)<0)nodes.push(p);}});" +
        "var out=[],seen={};nodes.forEach(function(n){if(out.length>=180)return;" +
        "var a=n.matches&&n.matches('a[href]')?n:n.querySelector('a[href]');if(!a)return;" +
        "var u=new URL(a.href,location.href);if(!/^https?:$/.test(u.protocol)||bad.test(u.hostname+u.pathname)||u.href===location.href)return;" +
        "var im=n.querySelector('img')||a.querySelector('img');var poster='';if(im){poster=im.currentSrc||im.getAttribute('data-src')||im.getAttribute('data-lazy-src')||im.getAttribute('data-original')||im.getAttribute('src')||'';}" +
        "if(!poster){var bg=n.querySelector('[style*=background]');if(bg){var m=(bg.getAttribute('style')||'').match(/url\\(['\"]?([^'\")]+)['\"]?\\)/i);if(m)poster=m[1];}}" +
        "try{poster=new URL(poster,location.href).href;}catch(e){poster='';}" +
        "if(!poster||bad.test(poster))return;" +
        "var t=(a.getAttribute('title')||a.getAttribute('aria-label')||n.querySelector('h1,h2,h3,h4,.title,.name')?.textContent||im?.getAttribute('alt')||a.textContent||'').replace(/\\s+/g,' ').trim();" +
        "if(t.length<2||/^(home|watch|play|video|ads?|advertisement|next|previous)$/i.test(t)||bad.test(t))return;" +
        "var key=u.href+'|'+poster;if(seen[key])return;seen[key]=1;" +
        "var frame=n.querySelector('iframe[src]');var embed=frame?new URL(frame.getAttribute('src'),location.href).href:u.href;" +
        "var host='';try{host=new URL(embed).hostname.replace(/^www\\./,'');}catch(e){}" +
        "out.push({title:t,poster:poster,pageUrl:u.href,embedUrl:embed,sourceHost:host,brand:host,description:'',logo:'',metadataSource:'javascript-dom'});" +
        "});return JSON.stringify({page:location.href,title:document.title,items:out});" +
        "}catch(e){return JSON.stringify({error:String(e),items:[]});}})()";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        siteUrl=getIntent().getStringExtra("url");
        siteName=getIntent().getStringExtra("name");
        if(siteUrl==null||siteUrl.isEmpty()){finish();return;}
        siteId=CustomSites.id(siteUrl);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,17,31));
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("Memuat website untuk memindai katalog JavaScript…");status.setPadding(18,12,18,12);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress,new LinearLayout.LayoutParams(-1,4));
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(true);web.setBackgroundColor(Color.BLACK);
        web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int p){progress.setProgress(p);}});
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){
                status.setText("Halaman siap. Menunggu katalog selesai dirender…");
                v.postDelayed(()->scan(),4500);
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        web.loadUrl(siteUrl);
    }

    private void scan(){
        if(finished||web==null)return;
        status.setText("Mengambil judul, poster, tautan, dan sumber pemutar…");
        web.evaluateJavascript(script,value->{
            if(finished)return;
            try{
                String raw=new org.json.JSONTokener(value==null?"null":value).nextValue() instanceof String
                    ? (String)new org.json.JSONTokener(value).nextValue() : value;
                JSONObject result=new JSONObject(raw);
                JSONArray items=result.optJSONArray("items");
                if(items==null)items=new JSONArray();
                save(result,items);
                finished=true;
                status.setText("Selesai: "+items.length()+" item katalog disimpan secara lokal.");
                Toast.makeText(this,"Indeks JavaScript: "+items.length()+" item",Toast.LENGTH_LONG).show();
                setResult(RESULT_OK); 
                status.postDelayed(this::finish,1200);
            }catch(Exception e){
                finished=true;status.setText("Pemindaian gagal membaca hasil halaman: "+e.getMessage());
            }
        });
    }

    private void save(JSONObject result,JSONArray videos)throws Exception{
        File f=new File(getFilesDir(),WebsiteIndexer.fileName(siteId));
        JSONObject root=new JSONObject();
        try{root=new JSONObject(Sources.read(f));}catch(Exception ignored){}
        JSONArray pages=root.optJSONArray("pages");if(pages==null)pages=new JSONArray();
        JSONArray media=root.optJSONArray("media");if(media==null)media=new JSONArray();
        JSONArray embeds=root.optJSONArray("embeds");if(embeds==null)embeds=new JSONArray();
        // Keep the static scan's results when JS extraction finds no usable cards.
        if(videos.length()>0){
            root.put("videos",videos);
            String page=result.optString("page",siteUrl);
            pages.put(new JSONObject().put("url",page).put("title",result.optString("title",siteName))
                .put("description","Katalog dirender oleh JavaScript").put("poster","").put("kind","catalog")
                .put("indexedAt",System.currentTimeMillis()));
            for(int i=0;i<videos.length();i++){
                JSONObject v=videos.optJSONObject(i);if(v==null)continue;
                String embed=v.optString("embedUrl","");
                if(!embed.isEmpty()&&!embeds.toString().contains(embed))embeds.put(embed);
            }
        }
        root.put("version",3).put("siteId",siteId).put("rootUrl",siteUrl)
            .put("generatedAt",System.currentTimeMillis()).put("pages",pages).put("media",media)
            .put("embeds",embeds).put("status",videos.length()>0?"indexed-js":root.optString("status","unavailable"))
            .put("javascriptItems",videos.length())
            .put("limitations","Pemindaian DOM JavaScript pada halaman publik yang dibuka di WebView. Tidak melewati login, paywall, atau proteksi bot.");
        File tmp=new File(f.getParentFile(),f.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(root.toString(2).getBytes("UTF-8"));out.getFD().sync();}
        if(f.exists()&&!f.delete())throw new java.io.IOException("Gagal mengganti indeks lama");
        if(!tmp.renameTo(f))throw new java.io.IOException("Gagal menyimpan indeks");
    }

    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.destroy();web=null;}super.onDestroy();}
}
