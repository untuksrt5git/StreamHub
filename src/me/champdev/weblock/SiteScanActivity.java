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
    private String siteUrl, siteName, siteId;
    private boolean finished, scanning;

    private final String script =
        "(function(){try{" +
        "var bad=/(doubleclick|googlesyndication|adservice|popads|exoclick|adsterra|propellerads|advert|sponsor|avatar|logo|pixel|tracking|popup|placeholder|spacer)/i;" +
        "var junk=/(^|\\/)(category|categories|pornstars|stars|sites|tags?|search|login|register|favorites|about|contact)(\\/|$)/i;" +
        "function abs(x){try{return new URL(x,location.href).href;}catch(e){return '';}}" +
        "function txt(n){return (n&&n.textContent?n.textContent:'').replace(/\\s+/g,' ').trim();}" +
        "function goodTitle(t){t=(t||'').replace(/\\s+/g,' ').trim();return t.length>=5&&t.length<=180&&!/^(home|watch|play|video|videos|ads?|advertisement|next|previous|more|menu|login|register)$/i.test(t)&&!bad.test(t);}" +
        "var out=[],seen={};" +
        "var anchors=document.querySelectorAll('a[href]');" +
        "for(var i=0;i<anchors.length&&out.length<180;i++){" +
        "var a=anchors[i],img=a.querySelector('img');" +
        "if(!img){var p0=a.parentElement; if(p0&&p0.querySelectorAll('img').length===1)img=p0.querySelector('img');}" +
        "if(!img)continue;" +
        "var href=abs(a.getAttribute('href'));if(!href)continue;" +
        "var hu;try{hu=new URL(href);}catch(e){continue;}" +
        "if(!/^https?:$/.test(hu.protocol)||hu.hostname!==location.hostname||href.split('#')[0]===location.href.split('#')[0])continue;" +
        "if(junk.test(hu.pathname)||bad.test(hu.hostname+hu.pathname+' '+(a.className||'')))continue;" +
        "var poster=abs(img.currentSrc||img.getAttribute('data-src')||img.getAttribute('data-lazy-src')||img.getAttribute('data-original')||img.getAttribute('data-lazy')||img.getAttribute('src')||'');" +
        "if(!poster||bad.test(poster))continue;" +
        "var w=img.naturalWidth||parseInt(img.getAttribute('width')||'0',10)||img.getBoundingClientRect().width||0;" +
        "var h=img.naturalHeight||parseInt(img.getAttribute('height')||'0',10)||img.getBoundingClientRect().height||0;" +
        "if(w>0&&w<100)continue;if(h>0&&h<70)continue;" +
        "var ratio=(w>0&&h>0)?w/h:1.4;if(ratio>3.25||ratio<0.32)continue;" +
        "var card=a,cl='';" +
        "for(var up=0;up<5&&card&&card.parentElement;up++){cl=(' '+(card.className||'')+' '+(card.parentElement.className||'')+' ').toLowerCase();" +
        "if(/video|movie|post|thumb|poster|entry|item|card|media|film/.test(cl)){card=card.parentElement;break;}card=card.parentElement;}" +
        "var heading=card.querySelector('h1,h2,h3,h4,.title,.name,[class*=title]');" +
        "var title=(a.getAttribute('title')||a.getAttribute('aria-label')||img.getAttribute('alt')||txt(heading)||txt(a)||txt(card)).replace(/\\s+/g,' ').trim();" +
        "title=title.replace(/\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b/g,'').replace(/\\s+/g,' ').trim();" +
        "if(!goodTitle(title))continue;" +
        "var cardText=txt(card);if(cardText.length>500)cardText=cardText.substring(0,500);" +
        "var cls=(' '+(a.className||'')+' '+(card.className||'')+' ').toLowerCase();" +
        "var score=0;score+=2;if(w>=100&&h>=70)score+=1;if(/video|movie|post|thumb|poster|entry|item|card|media|film/.test(cls))score+=2;" +
        "if(title.length>=8)score+=1;if(cardText.length>=12&&cardText.length<=500)score+=1;" +
        "if(/\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b|views?|watch|播放/i.test(cardText))score+=1;" +
        "if(score<5)continue;" +
        "var key=href+'|'+poster;if(seen[key])continue;seen[key]=1;" +
        "var frame=card.querySelector('iframe[src]');var embed=frame?abs(frame.getAttribute('src')):href;" +
        "var host='';try{host=new URL(embed).hostname.replace(/^www\\./,'');}catch(e){}" +
        "out.push({title:title,poster:poster,pageUrl:href,embedUrl:embed,sourceHost:host,brand:host,description:cardText,logo:'',metadataSource:'javascript-dom'});" +
        "}" +
        "return JSON.stringify({page:location.href,title:document.title,items:out,images:anchors.length});" +
        "}catch(e){return JSON.stringify({error:String(e),items:[]});}})()";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        siteUrl=getIntent().getStringExtra("url");
        siteName=getIntent().getStringExtra("name");
        if(siteUrl==null||siteUrl.isEmpty()){finish();return;}
        siteId=CustomSites.id(siteUrl);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,17,31));

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button scanButton=new Button(this);
        scanButton.setText("Pindai halaman ini");
        scanButton.setOnClickListener(v->scan(true));
        bar.addView(scanButton,new LinearLayout.LayoutParams(0,-2,1));
        Button reloadButton=new Button(this);
        reloadButton.setText("Muat ulang");
        reloadButton.setOnClickListener(v->{if(web!=null)web.reload();});
        bar.addView(reloadButton,new LinearLayout.LayoutParams(-2,-2));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        status=new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("Memuat website. Jika katalog belum terbaca, buka menu Videos/kategori lalu tekan Pindai halaman ini.");
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
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){
                status.setText("Halaman siap: "+url+" — menunggu katalog dirender. Anda juga bisa pindah ke halaman Videos lalu tekan Pindai.");
                v.postDelayed(()->scan(false),8500);
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        web.loadUrl(siteUrl);
    }

    private void scan(boolean manual){
        if(finished||web==null||scanning)return;
        scanning=true;
        status.setText(manual?"Memindai kartu video pada halaman ini…":"Mencoba mendeteksi katalog pada halaman yang terbuka…");
        web.evaluateJavascript(script,value->{
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
                    status.setText("Belum ada kartu video yang cocok pada halaman ini ("+result.optInt("images",0)+" tautan diperiksa). Buka menu Videos atau kategori di website, tunggu sampai thumbnail tampil, lalu tekan Pindai halaman ini. "+(msg.isEmpty()?"":msg));
                    if(manual)Toast.makeText(this,"Belum menemukan kartu katalog pada halaman ini",Toast.LENGTH_LONG).show();
                }
            }catch(Exception e){
                status.setText("Gagal membaca hasil pemindaian: "+e.getMessage());
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
        JSONArray oldVideos=root.optJSONArray("videos");
        JSONArray merged=new JSONArray();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(int i=0;oldVideos!=null&&i<oldVideos.length();i++){
            JSONObject v=oldVideos.optJSONObject(i);if(v==null)continue;
            String key=v.optString("pageUrl","")+"|"+v.optString("poster","");
            if(seen.add(key))merged.put(v);
        }
        for(int i=0;i<videos.length();i++){
            JSONObject v=videos.optJSONObject(i);if(v==null)continue;
            String key=v.optString("pageUrl","")+"|"+v.optString("poster","");
            if(seen.add(key))merged.put(v);
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
        root.put("version",3).put("siteId",siteId).put("rootUrl",siteUrl)
            .put("generatedAt",System.currentTimeMillis()).put("pages",pages).put("media",media)
            .put("embeds",embeds).put("videos",merged).put("status","indexed-js")
            .put("javascriptItems",merged.length())
            .put("limitations","Pemindaian DOM JavaScript pada halaman publik yang dibuka di WebView. Tidak melewati login, paywall, atau proteksi bot.");
        File tmp=new File(f.getParentFile(),f.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(root.toString(2).getBytes("UTF-8"));out.getFD().sync();}
        if(f.exists()&&!f.delete())throw new java.io.IOException("Gagal mengganti indeks lama");
        if(!tmp.renameTo(f))throw new java.io.IOException("Gagal menyimpan indeks");
    }

    @Override protected void onDestroy(){
        if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.destroy();web=null;}
        super.onDestroy();
    }
}
