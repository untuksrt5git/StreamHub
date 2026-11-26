package me.champdev.weblock;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;

/**
 * JavaScript DOM scanner for public site catalogs.
 * Keeps the page open so the user can navigate to a real catalog page and scan it manually.
 */
public final class SiteScanActivity extends Activity {
    private WebView web;
    private TextView status;
    private String siteUrl, siteName, siteId, indexFile, siteRootUrl;
    private SiteAdapters.Profile adapter;
    private boolean finished, scanning;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable scanWatchdog=()->{ if(scanning){scanning=false;if(status!=null)status.setText("Pemindaian tidak mendapat respons dari halaman. Tekan Pindai lagi atau buka kategori Videos terlebih dahulu.");} };

    private final String script =
        "(function(){try{" +
        "var bad=/(doubleclick|googlesyndication|adservice|popads|exoclick|adsterra|propellerads|advert|sponsor|avatar|logo|pixel|tracking|popup|placeholder|spacer)/i;" +
        "var junk=/(^|\\/)(category|categories|pornstars|stars|sites|tags?|search|login|register|favorites|about|contact)(\\/|$)/i;" +
        "function abs(x){try{return new URL(x,location.href).href;}catch(e){return '';}}" +
        "function txt(n){return (n&&n.textContent?n.textContent:'').replace(/\\s+/g,' ').trim();}" +
        "function goodTitle(t){t=(t||'').replace(/\\s+/g,' ').trim();return t.length>=5&&t.length<=180&&!/^(home|watch|play|video|videos|ads?|advertisement|next|previous|more|menu|login|register)$/i.test(t)&&!bad.test(t);}" +
        "var out=[],seen={};" +
        "var isSupjav=/supjav\\.com$/i.test(location.hostname);" +
        "var anchors=isSupjav?document.querySelectorAll('.posts.clearfix>.post>a.img[title],.posts>.post>a.img[title],.posts .post h3 a[rel=bookmark][itemprop=url],.posts .post h3 a[rel=bookmark],.post a.img[title]'):document.querySelectorAll('a[href]');" +
        "for(var i=0;i<anchors.length&&out.length<180;i++){" +
        "var a=anchors[i],img=a.querySelector('img');" +
        "if(!img){var p0=a.parentElement; if(p0&&p0.querySelectorAll('img').length===1)img=p0.querySelector('img');}" +
        "var box=a;for(var up0=0;up0<4&&box&&box.parentElement;up0++){if(/video|movie|post|thumb|poster|entry|item|card|media|film/i.test(String(box.className||'')+' '+String(box.parentElement.className||''))){box=box.parentElement;break;}box=box.parentElement;}" +
        "var styleText=((a.getAttribute('style')||'')+' '+(a.getAttribute('data-bg')||'')+' '+(a.getAttribute('data-background')||' ')+(box&&box.getAttribute?(' '+(box.getAttribute('style')||'')+' '+(box.getAttribute('data-bg')||'')+' '+(box.getAttribute('data-background')||'')):'')),bgMatch=styleText.match(/url\\(['\\\"]?([^'\\\")]+)['\\\"]?\\)/i);" +
        "var clsHint=(' '+(a.className||'')+' '+(a.parentElement&&a.parentElement.className||'')+' '+(box&&box.className||'')+' ').toLowerCase();" +
         // A catalog entry may be a text-only link or use a lazy/background thumbnail.
         "if(!isSupjav&&!img&&!bgMatch&&!/video|movie|post|thumb|poster|entry|item|card|media|film/.test(clsHint))continue;" +
        "var href=abs(a.getAttribute('href'));if(!href)continue;" +
        "var hu;try{hu=new URL(href);}catch(e){continue;}" +
        "if(!/^https?:$/.test(hu.protocol)||hu.hostname!==location.hostname||href.split('#')[0]===location.href.split('#')[0])continue;" +
        "if(junk.test(hu.pathname)||bad.test(hu.hostname+hu.pathname+' '+(a.className||'')))continue;" +
        "var poster=abs((img&&(img.currentSrc||img.getAttribute('data-src')||img.getAttribute('data-lazy-src')||img.getAttribute('data-original')||img.getAttribute('data-lazy')||img.getAttribute('data-original-src')||img.getAttribute('data-thumb')||img.getAttribute('src')||''))||(bgMatch&&bgMatch[1])||'');" +
        // A missing poster must never discard an otherwise valid video link.
         "if(poster&&bad.test(poster))poster='';" +
        "var w=img?(img.naturalWidth||parseInt(img.getAttribute('width')||'0',10)||img.getBoundingClientRect().width||0):(box.getBoundingClientRect().width||0);" +
        "var h=img?(img.naturalHeight||parseInt(img.getAttribute('height')||'0',10)||img.getBoundingClientRect().height||0):(box.getBoundingClientRect().height||0);" +
        "if(!isSupjav&&w>0&&w<100)continue;if(!isSupjav&&h>0&&h<70)continue;" +
        "var ratio=(w>0&&h>0)?w/h:1.4;if(!isSupjav&&(ratio>3.25||ratio<0.32))continue;" +
        "var card=a,cl='';" +
        "for(var up=0;up<5&&card&&card.parentElement;up++){cl=(' '+(card.className||'')+' '+(card.parentElement.className||'')+' ').toLowerCase();" +
        "if(/video|movie|post|thumb|poster|entry|item|card|media|film/.test(cl)){card=card.parentElement;break;}card=card.parentElement;}" +
        "var heading=card.querySelector('h1,h2,h3,h4,.title,.name,[class*=title]');" +
        "var title=(a.getAttribute('title')||a.getAttribute('aria-label')||(img?img.getAttribute('alt'):'')||txt(heading)||txt(a)||txt(card)).replace(/\\s+/g,' ').trim();" +
        "title=title.replace(/\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b/g,'').replace(/\\s+/g,' ').trim();" +
        "if(!goodTitle(title))continue;" +
        "var cardText=txt(card);if(cardText.length>500)cardText=cardText.substring(0,500);" +
        "var cls=(' '+(a.className||'')+' '+(card.className||'')+' ').toLowerCase();" +
        "var score=0;score+=2;if(w>=100&&h>=70)score+=1;if(/video|movie|post|thumb|poster|entry|item|card|media|film/.test(cls))score+=2;" +
        "if(title.length>=8)score+=1;if(cardText.length>=12&&cardText.length<=500)score+=1;" +
        "if(/\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b|views?|watch|播放/i.test(cardText))score+=1;" +
        "if(!isSupjav&&score<((img||bgMatch)?5:4))continue;" +
        "var key=href.split('#')[0];if(seen[key])continue;seen[key]=1;" +
        "var frame=card.querySelector('iframe[src]');var embed=frame?abs(frame.getAttribute('src')):href;" +
        "var host='';try{host=new URL(embed).hostname.replace(/^www\\./,'');}catch(e){}" +
        "out.push({title:title,poster:poster,pageUrl:href,embedUrl:embed,sourceHost:host,brand:host,description:cardText,logo:'',metadataSource:'javascript-dom'});" +
        "}" +
        "return JSON.stringify({page:location.href,title:document.title,items:out,images:anchors.length,adapter:isSupjav?'supjav':'generic'});" +
        "}catch(e){return JSON.stringify({error:String(e),items:[]});}})()";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        siteUrl=getIntent().getStringExtra("url");
        siteName=getIntent().getStringExtra("name");
        siteRootUrl=getIntent().getStringExtra("siteRootUrl");
        if(siteRootUrl==null||siteRootUrl.isEmpty())siteRootUrl=siteUrl;
        siteId=getIntent().getStringExtra("siteId");
        if(siteId==null||siteId.isEmpty())siteId=CustomSites.id(siteRootUrl);
        indexFile=getIntent().getStringExtra("indexFile");
        if(indexFile==null||indexFile.isEmpty())indexFile=WebsiteIndexer.fileName(siteId);
        if(siteUrl==null||siteUrl.isEmpty()){finish();return;}
        adapter=SiteAdapters.forUrl(siteUrl);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,17,31));

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button scanButton=new Button(this);
        scanButton.setText("Pindai sekarang");
        scanButton.setOnClickListener(v->scan(true));
        bar.addView(scanButton,new LinearLayout.LayoutParams(0,-2,1));
        Button stopButton=new Button(this);
        stopButton.setText("Stop");
        stopButton.setOnClickListener(v->{if(web!=null){web.stopLoading();status.setText("Pemuatan dihentikan. Anda tetap bisa memindai halaman yang sudah terbuka.");}});
        bar.addView(stopButton,new LinearLayout.LayoutParams(-2,-2));
        Button reloadButton=new Button(this);
        reloadButton.setText("Muat ulang");
        reloadButton.setOnClickListener(v->{if(web!=null){status.setText("Memuat ulang halaman…");web.reload();}});
        bar.addView(reloadButton,new LinearLayout.LayoutParams(-2,-2));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        status=new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("Memuat "+adapter.name+". Jika katalog belum terbaca, buka "+adapter.navigationHint+" lalu tekan Pindai halaman ini.");
        status.setPadding(18,10,18,10);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        root.addView(progress,new LinearLayout.LayoutParams(-1,4));

        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(true);
        web.setBackgroundColor(Color.BLACK);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){progress.setProgress(p);}
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m){
                android.util.Log.w("StreamHubScan",m.message()+" @ "+m.sourceId()+":"+m.lineNumber());
                if(m.messageLevel()==android.webkit.ConsoleMessage.MessageLevel.ERROR && status!=null){
                    status.setText("JavaScript situs memberi error: "+m.message());
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
                status.setText("Membuka "+url+" — tombol Pindai sekarang tetap bisa digunakan tanpa menunggu halaman selesai.");
            }
            @Override public void onPageCommitVisible(WebView v,String url){
                status.setText("Konten halaman sudah tampil. Tekan Pindai sekarang, atau buka Videos/kategori terlebih dahulu.");
                v.postDelayed(()->scan(false),4500);
            }
            @Override public void onPageFinished(WebView v,String url){
                status.setText("Halaman selesai dimuat: "+url+" — memeriksa isi halaman…");
                v.evaluateJavascript("(function(){var b=document.body;return JSON.stringify({ready:document.readyState,title:document.title,host:location.hostname,bodyText:b?(b.innerText||'').trim().length:0,html:b?b.innerHTML.length:0,frames:document.querySelectorAll('iframe').length,scripts:document.scripts.length});})()",diag->{
                    if(finished)return;
                    try{
                        String d=diag==null?"":diag;
                        status.setText("Pemeriksaan Supjav: "+d+" — jika bodyText dan html 0, halaman tidak dikirim/ditampilkan oleh situs atau WebView.");
                    }catch(Exception ignored){}
                });
                v.postDelayed(()->scan(false),3500);
            }
            @Override public void onReceivedHttpError(WebView v,android.webkit.WebResourceRequest request,android.webkit.WebResourceResponse response){
                if(request.isForMainFrame())status.setText("Server menolak halaman ("+response.getStatusCode()+" "+response.getReasonPhrase()+"). Ini bukan kegagalan pemilih katalog.");
            }
            @Override public void onReceivedError(WebView v,android.webkit.WebResourceRequest request,android.webkit.WebResourceError error){
                if(request.isForMainFrame())status.setText("Halaman gagal dimuat: "+error.getDescription()+". Coba Stop, Muat ulang, atau pindah ke domain aktif.");
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        web.loadUrl(siteUrl);
    }

    private void scan(boolean manual){
        if(finished||web==null||scanning)return;
        scanning=true;
        handler.removeCallbacks(scanWatchdog);
        handler.postDelayed(scanWatchdog,10000);
        status.setText(manual?("Memindai katalog "+adapter.name+" pada halaman ini…"):("Mencoba mendeteksi katalog "+adapter.name+"…"));
        web.evaluateJavascript(script,value->{
            handler.removeCallbacks(scanWatchdog);
            scanning=false;
            if(finished)return;
            try{
                Object parsed=new org.json.JSONTokener(value==null?"null":value).nextValue();
                String raw=parsed instanceof String?(String)parsed:String.valueOf(value);
                JSONObject result=new JSONObject(raw);
                JSONArray items=result.optJSONArray("items");
                if(items==null)items=new JSONArray();
                if(items.length()>0){
                    save(result,items);
                    status.setText("Ditemukan "+items.length()+" kartu video. Indeks disimpan. Anda dapat pindah ke halaman lain dan pindai lagi.");
                    Toast.makeText(this,"Katalog: "+items.length()+" item ditemukan",Toast.LENGTH_LONG).show();
                }else{
                    String msg=result.optString("error","");
                    status.setText("Belum ada kartu video yang cocok pada halaman ini ("+result.optInt("images",0)+" elemen kandidat diperiksa; adapter "+result.optString("adapter","generic")+"). Buka halaman Videos/kategori, tunggu konten tampil, lalu pindai lagi. "+(msg.isEmpty()?"":msg));
                    if(manual)Toast.makeText(this,"Belum menemukan kartu katalog pada halaman ini",Toast.LENGTH_LONG).show();
                }
            }catch(Exception e){
                status.setText("Gagal membaca hasil pemindaian: "+e.getMessage());
            }
        });
    }

    private void save(JSONObject result,JSONArray videos)throws Exception{
        File f=new File(getFilesDir(),indexFile);
        JSONObject root=new JSONObject();
        try{root=new JSONObject(Sources.read(f));}catch(Exception ignored){}
        // Older builds keyed the JS index by the redirected/current URL. Recover those
        // entries once when the stable website index is still empty.
        if(root.optJSONArray("videos")==null || root.optJSONArray("videos").length()==0){
            File legacy=new File(getFilesDir(),WebsiteIndexer.fileName(CustomSites.id(siteUrl)));
            if(!legacy.equals(f) && legacy.exists()){
                try{JSONObject previous=new JSONObject(Sources.read(legacy));
                    if(previous.optJSONArray("videos")!=null)root=previous;
                }catch(Exception ignored){}
            }
        }
        JSONArray pages=root.optJSONArray("pages");if(pages==null)pages=new JSONArray();
        JSONArray media=root.optJSONArray("media");if(media==null)media=new JSONArray();
        JSONArray embeds=root.optJSONArray("embeds");if(embeds==null)embeds=new JSONArray();
        JSONArray oldVideos=root.optJSONArray("videos");
        JSONArray merged=new JSONArray();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(int i=0;oldVideos!=null&&i<oldVideos.length();i++){
            JSONObject v=oldVideos.optJSONObject(i);if(v==null)continue;
            normalizeVideo(v);
            String key=v.optString("pageUrl","");
            if(key.isEmpty())key=v.optString("embedUrl","");
            if(seen.add(key))merged.put(v);
        }
        for(int i=0;i<videos.length();i++){
            JSONObject v=videos.optJSONObject(i);if(v==null)continue;
            normalizeVideo(v);
            String key=v.optString("pageUrl","");
            if(key.isEmpty())key=v.optString("embedUrl","");
            if(seen.add(key))merged.put(v);
            else if(!key.isEmpty()&&!v.optString("poster","").isEmpty()){
                for(int j=0;j<merged.length();j++){
                    JSONObject existing=merged.optJSONObject(j);
                    if(existing!=null&&key.equals(existing.optString("pageUrl",existing.optString("embedUrl","")))){
                        if(existing.optString("poster","").isEmpty())existing.put("poster",v.optString("poster"));
                        if(existing.optString("title","Video").equals("Video"))existing.put("title",v.optString("title","Video"));
                        break;
                    }
                }
            }
            String embed=v.optString("embedUrl","");
            if(!embed.isEmpty()&&!embeds.toString().contains(embed))embeds.put(embed);
        }
        String page=result.optString("page",siteUrl);
        boolean pageExists=false;
        for(int i=0;i<pages.length();i++){
            JSONObject p=pages.optJSONObject(i);
            if(p!=null&&page.equals(p.optString("url",""))){pageExists=true;break;}
        }
        if(!pageExists)pages.put(new JSONObject().put("url",page).put("title",result.optString("title",siteName))
            .put("description","Katalog dipindai dari DOM JavaScript").put("poster","").put("kind","catalog")
            .put("indexedAt",System.currentTimeMillis()));
        root.put("version",4).put("siteId",siteId).put("rootUrl",siteRootUrl)
            .put("schema","streamhub.catalog.v1").put("adapterId",adapter.id)
            .put("adapterName",adapter.name).put("adapterVersion",SiteAdapters.VERSION)
            .put("canonicalHost",SiteAdapters.host(result.optString("page",siteUrl)))
            .put("generatedAt",System.currentTimeMillis()).put("pages",pages).put("media",media)
            .put("embeds",embeds).put("videos",merged).put("status","indexed-js")
            .put("javascriptItems",merged.length())
            .put("limitations","Pemindaian DOM JavaScript pada halaman publik yang dibuka di WebView. Tidak melewati login, paywall, atau proteksi bot.");
        File tmp=new File(f.getParentFile(),f.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(root.toString(2).getBytes("UTF-8"));out.getFD().sync();}
        if(f.exists()&&!f.delete())throw new java.io.IOException("Gagal mengganti indeks lama");
        if(!tmp.renameTo(f))throw new java.io.IOException("Gagal menyimpan indeks");
    }


    private void normalizeVideo(JSONObject video) throws Exception {
        String page=video.optString("pageUrl","");
        String embed=video.optString("embedUrl","");
        String identity=page.isEmpty()?embed:page;
        video.put("schema","streamhub.catalog.v1")
            .put("id",Integer.toHexString(identity.hashCode()))
            .put("category",categoryFor(page))
            .put("sourceHost",SiteAdapters.host(embed).isEmpty()?SiteAdapters.host(page):SiteAdapters.host(embed))
            .put("updatedAt",System.currentTimeMillis());
        if(video.optString("title","").trim().isEmpty()) video.put("title","Video");
    }

    private String categoryFor(String address) {
        String p=address==null?"":address.toLowerCase(java.util.Locale.ROOT);
        if(p.contains("episode")||p.contains("season")||p.matches(".*[/_-]ep[-_]?\\\\d+.*")) return "episode";
        if(p.contains("series")||p.contains("drama")||p.contains("tv-show")) return "series";
        if(p.contains("movie")||p.contains("film")||p.contains("video")||p.contains("watch")) return "video";
        return "catalog";
    }

    @Override protected void onDestroy(){
        finished=true;handler.removeCallbacksAndMessages(null);
        if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.destroy();web=null;}
        super.onDestroy();
    }
}
