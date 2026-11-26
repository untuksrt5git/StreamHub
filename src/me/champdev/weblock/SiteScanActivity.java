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
    private int scanGeneration, scanPasses, scanStableBottom, scanLastHeight=-1;
    private int resourceHttpErrors, resourceNetworkErrors;
    private int domNullRetries;
    private String lastResourceFailure="";
    private String lastAutoScanUrl="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable scanWatchdog=()->{ if(scanning){scanning=false;scanGeneration++;if(status!=null)status.setText("Pemindaian melewati batas waktu. Sebagian gambar mungkin tertahan oleh situs atau jaringan. Coba lagi setelah halaman stabil.");} };

    private final String script =
        "(function(){try{" +
        "var bad=/(doubleclick|googlesyndication|adservice|popads|exoclick|adsterra|propellerads|advert|sponsor|avatar|logo|pixel|tracking|popup|placeholder|spacer)/i;" +
        "var junk=/(^|\\/)(category|categories|pornstars?|pornstar|stars?|actress|actors?|performers?|cast|models?|celebrities|sites|tags?|search|login|register|favorites|about|contact)(\\/|$)/i;" +
        "function abs(x){try{return x?new URL(String(x).trim(),location.href).href:'';}catch(e){return '';}}" +
        "function txt(n){return(n&&n.textContent?n.textContent:'').replace(/\\s+/g,' ').trim();}" +
        "function goodTitle(t){t=(t||'').replace(/\\s+/g,' ').trim();return t.length>=2&&t.length<=180&&!/^(home|watch|play|video|videos|ads?|advertisement|next|previous|more|menu|login|register|body a site|a site|site body|body)$/i.test(t)&&!/^(body|html|document)\\b/i.test(t)&&!/(^|\\s)(body a site|this site|site body)(\\s|$)/i.test(t)&&!bad.test(t);}" +
        "function cardClass(n){var c=' '+String(n&&n.className||'')+' ';return /(^|[\\s_-])(post|video|movie|item|entry|thumb|thumbnail|film|clip|card|media)([\\s_-]|$)/i.test(c);}" +
        "function sameHost(a,b){a=(a||'').toLowerCase().replace(/^www\\./,'');b=(b||'').toLowerCase().replace(/^www\\./,'');return a===b||a.endsWith('.'+b)||b.endsWith('.'+a);}" +
        "function firstSrcset(v){if(!v)return '';var parts=String(v).split(','),best='',score=-1;for(var z=0;z<parts.length;z++){var p=parts[z].trim().split(/\\s+/),d=p[1]||'1',n=parseFloat(d);if(/w$/i.test(d))n=parseFloat(d);else if(/x$/i.test(d))n*=1000;if(!isFinite(n))n=0;if(n>=score){score=n;best=p[0]||'';}}return best;}" +
        "function cleanPoster(v){var u=abs(v);try{var h=new URL(u).hostname;if(/(^|\\.)img\\.supjav\\.com$/i.test(h))u=u.replace(/!\\d+x\\d+(?=\\.(?:jpe?g|png|webp)(?:[?#]|$))/i,'');}catch(e){}return u;}" +
        "function imageUrl(im){if(!im)return '';var names=['data-original','data-original-src','data-src','data-lazy-src','data-lazy','data-thumb','data-thumbnail','data-poster','data-image','data-url','data-bg','data-background','data-srcset','data-lazy-srcset','srcset'];for(var k=0;k<names.length;k++){var v=im.getAttribute(names[k]);if(v){if(names[k].indexOf('srcset')>=0)v=firstSrcset(v);var u=cleanPoster(v);if(u&&!bad.test(u)&&!/^data:image\\/(gif|svg)/i.test(u))return u;}}var cur=cleanPoster(im.currentSrc||'');if(cur&&!bad.test(cur)&&!/^data:image\\/(gif|svg)/i.test(cur)&&im.naturalWidth>0)return cur;var src=cleanPoster(im.getAttribute('src')||'');if(src&&!bad.test(src)&&!/^data:image\\/(gif|svg)/i.test(src)&&im.naturalWidth>0)return src;return '';}" +
        "function bgUrl(n){if(!n)return '';var vals=[];try{var s=n.getAttribute('style')||'',m=s.match(/background(?:-image)?\\s*:\\s*url\\((['\"]?)(.*?)\\1\\)/i);if(m&&m[2])vals.push(m[2]);var cs=window.getComputedStyle?getComputedStyle(n):null,b=cs&&(cs.backgroundImage||cs.getPropertyValue('background-image'));if(b&&b!=='none'){var re=/url\\((['\"]?)(.*?)\\1\\)/ig,x;while((x=re.exec(b))!==null)if(x[2])vals.push(x[2]);}['data-bg','data-background','data-background-image','data-lazy-background','data-poster','data-src','data-original'].forEach(function(k){var v=n.getAttribute(k);if(v)vals.push(v);});}catch(e){}for(var q=0;q<vals.length;q++){var u=cleanPoster(vals[q]);if(u&&!bad.test(u)&&!/^data:image\\/(gif|svg)/i.test(u))return u;}return '';}" +
        "var out=[],seen={},anchors=document.querySelectorAll('a[href]'),isSupjav=/supjav\\.com$/i.test(location.hostname),checked=0,cardCandidates=0;" +
        "for(var i=0;i<anchors.length;i++){" +
        "var a=anchors[i],href=abs(a.getAttribute('href'));checked++;if(!href)continue;" +
        "var hu;try{hu=new URL(href);}catch(e){continue;}if(!/^https?:$/.test(hu.protocol)||!sameHost(hu.hostname,location.hostname)||href.split('#')[0]===location.href.split('#')[0])continue;" +
        "if(junk.test(hu.pathname)||bad.test(hu.hostname+hu.pathname+' '+(a.className||'')))continue;" +
        "var candidate=null,fallback=null,cur=a;" +
        "for(var up=0;up<10&&cur&&cur.parentElement;up++){var p=cur.parentElement,tag=String(p.tagName||'').toLowerCase(),ac=p.querySelectorAll('a[href]').length,imc=p.querySelectorAll('img,source,[style],[data-bg],[data-original],[data-poster]').length,hasHead=!!p.querySelector('h1,h2,h3,h4,.title,.name,[class*=title],[itemprop=name]');" +
        "if(!fallback&&ac<=8&&(imc>0||hasHead))fallback=p;" +
        "var strongCard=/(^|[\\s_-])(post|video|movie|item|entry|film|clip|card|media)([\\s_-]|$)/i.test(' '+String(p.className||'')+' ');" +
        "if(!candidate&&(tag==='article'||strongCard)){candidate=p;if(hasHead&&imc>0)break;}else if(!candidate&&cardClass(p))candidate=p;" +
        "cur=p;}" +
        "var card=candidate||fallback||a.parentElement||a;cardCandidates++;" +
        "var img=a.querySelector('img')||card.querySelector('img'),poster=imageUrl(img)||bgUrl(a)||bgUrl(card);" +
        "if(!poster){var imageNodes=card.querySelectorAll('img,source,[style],[data-bg],[data-background],[data-src],[data-lazy-src],[data-original],[data-poster],[data-thumb]');var bestArea=0;" +
        "for(var bi=0;bi<imageNodes.length;bi++){var node=imageNodes[bi],u=imageUrl(node)||bgUrl(node);if(!u)continue;var rr=node.getBoundingClientRect?node.getBoundingClientRect():{width:0,height:0};var area=Math.max(0,rr.width)*Math.max(0,rr.height);if(!poster||area>bestArea){poster=u;bestArea=area;}}" +
        "if(!poster&&a.parentElement)poster=bgUrl(a.parentElement);}" +
        "var clsHint=(' '+(a.className||'')+' '+(card.className||'')+' '+(a.parentElement&&a.parentElement.className||'')+' ').toLowerCase();" +
        "if(!poster)continue;" +"if(!isSupjav&&!img&&!/(^|[\\s_-])(video|movie|post|thumb|thumbnail|poster|entry|item|card|media|film|clip)([\\s_-]|$)/.test(clsHint))continue;" +
        "var w=img?(img.naturalWidth||parseInt(img.getAttribute('width')||'0',10)||img.getBoundingClientRect().width||0):(card.getBoundingClientRect().width||0),h=img?(img.naturalHeight||parseInt(img.getAttribute('height')||'0',10)||img.getBoundingClientRect().height||0):(card.getBoundingClientRect().height||0);" +
        "if(!isSupjav&&w>0&&w<60)continue;if(!isSupjav&&h>0&&h<45)continue;" +
        "var heading=card.querySelector('h1,h2,h3,h4,.title,.name,[class*=title],[itemprop=name]'),titles=[a.getAttribute('title'),a.getAttribute('data-title'),a.getAttribute('aria-label'),img&&img.getAttribute('alt'),heading&&txt(heading),txt(a),txt(card)],title='';" +
        "for(var ti=0;ti<titles.length;ti++){if(goodTitle(titles[ti])){title=String(titles[ti]).replace(/\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b/g,'').replace(/\\s+/g,' ').trim();if(goodTitle(title))break;title='';}}" +
        "if(!title){var parts=hu.pathname.split('/').filter(Boolean),slug=parts.length?parts[parts.length-1]:'';try{slug=decodeURIComponent(slug);}catch(e){}slug=slug.replace(/\\.(html?|php|aspx?)$/i,'').replace(/[-_]+/g,' ').replace(/\\s+/g,' ').trim();title=goodTitle(slug)?slug:('Video '+(out.length+1));}var cardText=txt(card);if(cardText.length>500)cardText=cardText.substring(0,500);" +
        "var key=href.split('#')[0];if(seen[key])continue;seen[key]=1;" +
        "var frame=card.querySelector('iframe[src]'),embed=frame?abs(frame.getAttribute('src')):href,host='';try{host=new URL(embed).hostname.replace(/^www\\./,'');}catch(e){}" +
        "out.push({title:title,poster:poster||'',posterReferer:location.href,pageUrl:href,embedUrl:embed,sourceHost:host,brand:host,description:cardText,logo:'',metadataSource:'javascript-dom'});" +
        "}" +
        "var imgs=document.images||[],loaded=0,failed=0,lazy=0;for(var ii=0;ii<imgs.length;ii++){var im=imgs[ii];if(im.naturalWidth>0)loaded++;else if(im.complete&&(im.currentSrc||im.src))failed++;if(im.getAttribute('loading')==='lazy'||im.getAttribute('data-src')||im.getAttribute('data-lazy-src')||im.getAttribute('data-original')||im.getAttribute('data-srcset'))lazy++;}" +
        "var posterCount=0;for(var oi=0;oi<out.length;oi++)if(out[oi].poster)posterCount++;" +
        "return {page:location.href,title:document.title,items:out,images:anchors.length,anchorsChecked:checked,cardsExamined:cardCandidates,posters:posterCount,missingPosters:out.length-posterCount,imageElements:imgs.length,imagesLoaded:loaded,imagesFailed:failed,lazyImages:lazy,pageHeight:Math.max(document.body?document.body.scrollHeight:0,document.documentElement?document.documentElement.scrollHeight:0),adapter:isSupjav?'supjav':'generic'};" +
        "}catch(e){return {error:String(e),items:[]};}})()";

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
        stopButton.setOnClickListener(v->{if(scanning){scanning=false;scanGeneration++;handler.removeCallbacks(scanWatchdog);}if(web!=null){status.setText("Pemindaian dihentikan. Pemuatan halaman dan gambar tetap berjalan; tekan Pindai sekarang jika sudah siap.");}});
        bar.addView(stopButton,new LinearLayout.LayoutParams(-2,-2));
        Button reloadButton=new Button(this);
        reloadButton.setText("Muat ulang");
        reloadButton.setOnClickListener(v->{if(scanning){scanning=false;scanGeneration++;handler.removeCallbacks(scanWatchdog);}if(web!=null){status.setText("Memuat ulang halaman…");web.reload();}});
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
        // Keep the scanner close to its original working WebView configuration.
        // Only retain same-WebView handling so links such as Supjav More+ keep working.
        android.webkit.WebSettings scanSettings=web.getSettings();
        scanSettings.setJavaScriptEnabled(true);
        scanSettings.setDomStorageEnabled(true);
        scanSettings.setDatabaseEnabled(true);
        scanSettings.setLoadsImagesAutomatically(true);
        scanSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        scanSettings.setMediaPlaybackRequiresUserGesture(false);
        scanSettings.setSupportMultipleWindows(false); // Keep More+ navigation in this WebView.
        scanSettings.setBuiltInZoomControls(true);
        scanSettings.setDisplayZoomControls(false);
        scanSettings.setUseWideViewPort(true);
        scanSettings.setLoadWithOverviewMode(true);
        if(android.os.Build.VERSION.SDK_INT>=21){
            scanSettings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        }
        // Match the normal browser page: some catalog sites reject the default Android WebView UA.
        scanSettings.setUserAgentString(scanSettings.getUserAgentString().replace("; wv","").replace(" Version/4.0",""));
        web.setBackgroundColor(Color.BLACK);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){progress.setProgress(p);}
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m){
                android.util.Log.w("StreamHubScan",m.message()+" @ "+m.sourceId()+":"+m.lineNumber());
                if(m.messageLevel()==android.webkit.ConsoleMessage.MessageLevel.ERROR && status!=null
                    && !m.message().toLowerCase().contains("permission policy violation")
                    && !m.message().toLowerCase().contains("not allowed in this document")
                    && !m.message().toLowerCase().contains("accelerometer")
                    && !m.message().toLowerCase().contains("gyroscope")){
                    status.setText("JavaScript situs memberi error: "+m.message());
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
                resourceHttpErrors=0;resourceNetworkErrors=0;lastResourceFailure="";
                lastAutoScanUrl="";
                v.evaluateJavascript("window.__shPosterWarmupCancel=true",null);
                status.setText("Membuka "+url+" — poster akan dimuat bertahap; pemindaian katalog tetap manual.");
            }
            @Override public void onPageCommitVisible(WebView v,String url){
                status.setText("Konten halaman sudah tampil. Halaman dibiarkan berjalan dahulu; tekan Pindai sekarang jika sudah siap.");
            }
            @Override public void onPageFinished(WebView v,String url){
                // Warm up lazy posters independently of the manual catalog scan.
                // This only scrolls the current page to trigger its own lazy-image handlers,
                // then restores the original position; it never extracts or saves catalog data.
                startPosterWarmup(v);
                status.setText("Halaman siap: "+url+" — poster dimuat bertahap. Tekan Pindai sekarang untuk menyimpan katalog.");
                v.evaluateJavascript("(function(){var b=document.body;return JSON.stringify({ready:document.readyState,title:document.title,host:location.hostname,bodyText:b?(b.innerText||'').trim().length:0,html:b?b.innerHTML.length:0,frames:document.querySelectorAll('iframe').length,scripts:document.scripts.length});})()",diag->{
                    if(!finished&&diag!=null) android.util.Log.d("StreamHubScan","Page ready "+diag);
                });
            }
            @Override public void onReceivedHttpError(WebView v,android.webkit.WebResourceRequest request,android.webkit.WebResourceResponse response){
                if(request.isForMainFrame())status.setText("Server menolak halaman ("+response.getStatusCode()+" "+response.getReasonPhrase()+"). Ini bukan kegagalan pemilih katalog.");
                else{resourceHttpErrors++;lastResourceFailure=response.getStatusCode()+" "+request.getUrl().getHost();}
            }
            @Override public void onReceivedError(WebView v,android.webkit.WebResourceRequest request,android.webkit.WebResourceError error){
                if(request.isForMainFrame())status.setText("Halaman gagal dimuat: "+error.getDescription()+". Coba Stop, Muat ulang, atau pindah ke domain aktif.");
                else{resourceNetworkErrors++;lastResourceFailure=String.valueOf(error.getDescription())+" @ "+request.getUrl().getHost();}
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        web.loadUrl(siteUrl);
    }

    /**
     * Trigger common lazy-image mechanisms as soon as a page opens, without running
     * the indexer. The page is restored to its original scroll position afterwards.
     * A real user gesture cancels the warm-up so it never fights manual browsing.
     */
    private void startPosterWarmup(WebView v){
        String js="(function(){try{if(window.__shPosterWarmupRunning)return 'busy';"+
            "window.__shPosterWarmupRunning=true;window.__shPosterWarmupCancel=false;"+
            "var original=window.pageYOffset||0,steps=0,stable=0,lastHeight=0;"+
            "function promote(){var all=document.querySelectorAll('img,source'),vh=Math.max(240,window.innerHeight||600);"+
            "for(var i=0;i<all.length;i++){var n=all[i],r=n.getBoundingClientRect();if(r.top>vh*1.8||r.bottom< -vh*.5)continue;"+
            "var src=n.getAttribute('data-original')||n.getAttribute('data-original-src')||n.getAttribute('data-src')||n.getAttribute('data-lazy-src')||n.getAttribute('data-lazy')||n.getAttribute('data-image')||n.getAttribute('data-poster')||n.getAttribute('data-thumb');"+
            "var set=n.getAttribute('data-srcset')||n.getAttribute('data-lazy-srcset')||n.getAttribute('data-original-srcset');"+
            "if(set&&!n.getAttribute('srcset'))n.setAttribute('srcset',set);"+
             "var current=n.getAttribute('src')||'',broken=n.tagName.toLowerCase()==='img'&&n.complete&&n.naturalWidth===0,placeholder=/^(about:blank|data:image\\/(gif|svg))/i.test(current)||/(placeholder|spacer|blank|no-image|default-thumb)/i.test(current);"+
             "if(src&&n.tagName.toLowerCase()==='img'&&(!current||broken||placeholder)){n.setAttribute('src',src);n.setAttribute('loading','eager');promoted++;}"+
            "if(n.tagName.toLowerCase()==='img'){try{n.loading='eager';}catch(e){}}"+
            "}}"+
            "function cancel(){window.__shPosterWarmupCancel=true;}"+
            "window.addEventListener('touchstart',cancel,{once:true,passive:true});window.addEventListener('wheel',cancel,{once:true,passive:true});window.addEventListener('keydown',cancel,{once:true});"+
            "function tick(){if(window.__shPosterWarmupCancel){window.__shPosterWarmupRunning=false;return;}"+
            "promote();var vh=Math.max(240,window.innerHeight||600),height=Math.max(document.body?document.body.scrollHeight:0,document.documentElement?document.documentElement.scrollHeight:0);"+
            "window.scrollBy(0,Math.max(220,Math.floor(vh*.78)));steps++;"+
            "if(height>0&&(window.pageYOffset+vh>=height-12)){if(height===lastHeight)stable++;else stable=1;}else stable=0;lastHeight=height;"+
            "if((stable>=2&&steps>=2)||steps>=36){setTimeout(function(){if(!window.__shPosterWarmupCancel)window.scrollTo(0,original);window.__shPosterWarmupRunning=false;},900);return;}"+
            "setTimeout(tick,420);}setTimeout(tick,900);return 'started';"+
            "}catch(e){window.__shPosterWarmupRunning=false;return String(e);}})()";
        v.evaluateJavascript(js,result->android.util.Log.d("StreamHubScan","Poster warm-up "+result));
    }

    private JSONObject parseJsObject(String value)throws Exception{
        if(value==null)throw new org.json.JSONException("JavaScript tidak mengembalikan hasil (null)");
        Object parsed=new org.json.JSONTokener(value).nextValue();
        if(parsed instanceof JSONObject)return (JSONObject)parsed;
        if(parsed instanceof String){
            String raw=((String)parsed).trim();
            if(raw.isEmpty()||"null".equals(raw))throw new org.json.JSONException("Hasil JavaScript kosong/null");
            Object inner=new org.json.JSONTokener(raw).nextValue();
            if(inner instanceof JSONObject)return (JSONObject)inner;
            throw new org.json.JSONException("Hasil JavaScript bukan objek JSON: "+inner.getClass().getSimpleName());
        }
        if(parsed==JSONObject.NULL)throw new org.json.JSONException("Hasil JavaScript bernilai null, bukan objek JSON");
        throw new org.json.JSONException("Format hasil JavaScript tidak didukung: "+parsed.getClass().getSimpleName());
    }

    private void scan(boolean manual){
        if(finished||web==null||scanning)return;
        scanning=true;
        final int generation=++scanGeneration;
        scanPasses=0;scanStableBottom=0;scanLastHeight=-1;domNullRetries=0;
        handler.removeCallbacks(scanWatchdog);
        handler.postDelayed(scanWatchdog,90000);
        // Stop the automatic poster warm-up before scanning. Both routines scroll the same
        // WebView; running them together can make the scanner miss lazy-loaded cards.
        web.evaluateJavascript("window.__shPosterWarmupCancel=true;window.__shPosterWarmupRunning=false;",null);
        status.setText("Menyiapkan pemindaian "+adapter.name+" — memicu pemuatan gambar dan konten bertahap…");
        waitForPageReady(generation,manual,0);
    }

    private boolean isScanCurrent(int generation){
        return !finished&&scanning&&generation==scanGeneration&&web!=null;
    }

    private void waitForPageReady(int generation,boolean manual,int attempt){
        if(!isScanCurrent(generation))return;
        String probe="JSON.stringify({ready:document.readyState,images:document.images?document.images.length:0,loaded:(function(){var n=0,a=document.images||[];for(var i=0;i<a.length;i++)if(a[i].complete&&a[i].naturalWidth>0)n++;return n;})(),height:Math.max(document.body?document.body.scrollHeight:0,document.documentElement?document.documentElement.scrollHeight:0)})";
        web.evaluateJavascript(probe,value->{
            if(!isScanCurrent(generation))return;
            String readyState="unknown"; int imageCount=0,loadedCount=0;
            try{
                JSONObject state=parseJsObject(value);
                readyState=state.optString("ready","unknown");
                imageCount=state.optInt("images",0);
                loadedCount=state.optInt("loaded",0);
            }catch(Exception ignored){}
            boolean ready="interactive".equalsIgnoreCase(readyState)||"complete".equalsIgnoreCase(readyState);
            if(!ready&&attempt<15){
                status.setText("Menunggu halaman siap… pemeriksaan "+(attempt+1)+"/15 • status: "+readyState+
                    " • gambar: "+loadedCount+"/"+imageCount);
                handler.postDelayed(()->waitForPageReady(generation,manual,attempt+1),800);
                return;
            }
            web.evaluateJavascript("(function(){window.__shScanOriginalY=window.scrollY||0;window.scrollTo(0,0);return 'ready';})()",ignored->{
                if(!isScanCurrent(generation))return;
                handler.postDelayed(()->scanScrollStep(generation,manual),500);
            });
        });
    }

    private void scanScrollStep(int generation,boolean manual){
        if(!isScanCurrent(generation))return;
        String stepScript="(function(){try{var vh=Math.max(240,window.innerHeight||600),step=Math.max(240,Math.floor(vh*.72));" +
            "window.scrollBy(0,step);var imgs=document.querySelectorAll('img[loading=lazy],img[data-src],img[data-lazy-src],img[data-original],img[data-srcset]');" +
            "for(var i=0;i<imgs.length;i++){var r=imgs[i].getBoundingClientRect();if(r.top<vh*1.6&&r.bottom>-vh*.6){try{imgs[i].loading='eager';}catch(e){}}}" +
            "var h=Math.max(document.body?document.body.scrollHeight:0,document.documentElement?document.documentElement.scrollHeight:0);" +
            "return JSON.stringify({y:window.pageYOffset||document.documentElement.scrollTop||0,height:h,view:vh,images:document.images?document.images.length:0,links:document.querySelectorAll('a[href]').length});" +
            "}catch(e){return JSON.stringify({error:String(e),height:0,y:0,view:600});}})()";
        web.evaluateJavascript(stepScript,value->{
            if(!isScanCurrent(generation))return;
            try{
                JSONObject p=parseJsObject(value);
                scanPasses++;
                int height=p.optInt("height",0),y=p.optInt("y",0),view=p.optInt("view",600);
                boolean atBottom=height>0&&y+view>=height-16;
                if(atBottom){if(height==scanLastHeight)scanStableBottom++;else scanStableBottom=1;}
                else scanStableBottom=0;
                scanLastHeight=height;
                if(scanPasses==1||scanPasses%4==0)status.setText("Memuat bertahap… langkah "+scanPasses +
                    " • "+p.optInt("links",0)+" tautan • "+p.optInt("images",0)+" elemen gambar");
                boolean stable=atBottom&&scanStableBottom>=3;
                if(stable){
                    status.setText("Pemuatan bertahap selesai setelah "+scanPasses+" langkah. Menunggu gambar dan mengumpulkan tautan…");
                    activateLazyImages(generation,manual,false);
                }else{
                    handler.postDelayed(()->scanScrollStep(generation,manual),650);
                }
            }catch(Exception e){
                finishScanError(generation,"Gagal memeriksa proses scroll: "+e.getMessage());
            }
        });
    }

    // Promote common lazy-load attributes to real image requests after scrolling the page.
    // This is limited to public image URLs already exposed by the page; it does not bypass site access controls.
    private void activateLazyImages(int generation,boolean manual,boolean scrollLimited){
        if(!isScanCurrent(generation))return;
        String activate="(function(){try{var promoted=0,backgrounds=0;"+
            "function first(v){if(!v)return '';return String(v).split(',')[0].trim().split(/\\s+/)[0];}"+
            "var nodes=document.querySelectorAll('img,source');"+
            "for(var i=0;i<nodes.length;i++){var n=nodes[i];"+
            "var src=n.getAttribute('data-original')||n.getAttribute('data-original-src')||n.getAttribute('data-src')||n.getAttribute('data-lazy-src')||n.getAttribute('data-lazy')||n.getAttribute('data-image')||n.getAttribute('data-poster')||n.getAttribute('data-thumb');"+
            "var set=n.getAttribute('data-original-srcset')||n.getAttribute('data-srcset')||n.getAttribute('data-lazy-srcset');"+
            "if(set&&!n.getAttribute('srcset')){n.setAttribute('srcset',set);promoted++;}"+
            "var current=n.getAttribute('src')||'',broken=n.tagName.toLowerCase()==='img'&&n.complete&&n.naturalWidth===0,placeholder=/^(about:blank|data:image\\/(gif|svg))/i.test(current)||/(placeholder|spacer|blank|no-image|default-thumb)/i.test(current);"+
            "if(src&&n.tagName.toLowerCase()==='img'&&(!current||broken||placeholder)){n.setAttribute('src',src);n.setAttribute('loading','eager');promoted++;}"+
            "}"+
            "var bg=document.querySelectorAll('[data-bg],[data-background],[data-background-image]');"+
            "for(var j=0;j<bg.length;j++){var el=bg[j],u=el.getAttribute('data-bg')||el.getAttribute('data-background')||el.getAttribute('data-background-image');"+
            "if(u&&!el.style.backgroundImage){el.style.backgroundImage='url('+JSON.stringify(u).slice(1,-1)+')';backgrounds++;}}"+
            "try{window.dispatchEvent(new Event('scroll'));window.dispatchEvent(new Event('resize'));}catch(e){}"+
            "return JSON.stringify({promoted:promoted,backgrounds:backgrounds,images:document.images.length});"+
            "}catch(e){return JSON.stringify({error:String(e)});}})()";
        status.setText("Mengaktifkan sumber gambar tertunda…");
        web.evaluateJavascript(activate,value->{
            if(!isScanCurrent(generation))return;
            if(value==null || "null".equals(value.trim())){
                status.setText("WebView tidak mengembalikan hasil aktivasi gambar; mencoba pemindaian DOM tanpa aktivasi tambahan…");
                handler.postDelayed(()->runDomScan(generation,manual,scrollLimited),500);
                return;
            }
            handler.postDelayed(()->waitForImages(generation,manual,scrollLimited,0),1200);
        });
    }

    private void waitForImages(int generation,boolean manual,boolean scrollLimited,int attempt){
        if(!isScanCurrent(generation))return;
        String state="(function(){var a=document.images||[],pending=0,loaded=0,failed=0;for(var i=0;i<a.length;i++){var x=a[i];if(x.naturalWidth>0)loaded++;else if(x.complete&&(x.currentSrc||x.src))failed++;else if(x.currentSrc||x.src)pending++;}return JSON.stringify({pending:pending,loaded:loaded,failed:failed,total:a.length});})()";
        web.evaluateJavascript(state,value->{
            if(!isScanCurrent(generation))return;
            try{
                JSONObject p=parseJsObject(value);int pending=p.optInt("pending",0);
                if(pending>0&&attempt<12){
                    status.setText("Menunggu gambar dimuat… "+p.optInt("loaded",0)+"/"+p.optInt("total",0)+" berhasil, "+pending+" masih berjalan");
                    handler.postDelayed(()->waitForImages(generation,manual,scrollLimited,attempt+1),900);
                }else runDomScan(generation,manual,scrollLimited);
            }catch(Exception e){runDomScan(generation,manual,scrollLimited);}
        });
    }

    private void runDomScan(int generation,boolean manual,boolean scrollLimited){
        if(!isScanCurrent(generation))return;
        web.evaluateJavascript(script,value->{
            if(!isScanCurrent(generation))return;
            if(value==null || "null".equals(value.trim())){
                if(domNullRetries<3){
                    domNullRetries++;
                    status.setText("Hasil JavaScript belum diterima. Mencoba ulang pembacaan katalog "+domNullRetries+"/3…");
                    handler.postDelayed(()->runDomScan(generation,manual,scrollLimited),1400);
                    return;
                }
                status.setText("Hasil pemindaian utama kosong setelah 3 percobaan. Memeriksa apakah JavaScript halaman masih berjalan…");
                web.evaluateJavascript("JSON.stringify({page:location.href,title:document.title,ready:document.readyState,images:document.images?document.images.length:0,links:document.querySelectorAll('a[href]').length,body:!!document.body,bodyText:document.body?(document.body.innerText||'').length:0})",diag->{
                    if(!isScanCurrent(generation))return;
                    handler.removeCallbacks(scanWatchdog);
                    scanning=false;
                    String detail=diag==null?"diagnostik juga null":diag;
                    status.setText("Scanner tidak menerima hasil JavaScript katalog. Pemeriksaan halaman: "+detail+
                        ". Coba muat ulang halaman, tunggu katalog tampil, lalu pindai lagi. Jika berulang, kirim teks ini.");
                });
                return;
            }
            handler.removeCallbacks(scanWatchdog);
            scanning=false;
            try{
                JSONObject result=parseJsObject(value);
                result.put("scrollPasses",scanPasses);
                result.put("scrollLimitReached",scrollLimited);
                result.put("resourceHttpErrors",resourceHttpErrors);
                result.put("resourceNetworkErrors",resourceNetworkErrors);
                result.put("lastResourceFailure",lastResourceFailure);
                web.evaluateJavascript("window.scrollTo(0,window.__shScanOriginalY||0);'restored';",ignored->{});
                JSONArray items=result.optJSONArray("items");
                if(items==null)items=new JSONArray();
                int posters=result.optInt("posters",0),missing=result.optInt("missingPosters",items.length()-posters);
                String metrics="Tautan diperiksa "+result.optInt("anchorsChecked",0)+
                    " • gambar dimuat "+result.optInt("imagesLoaded",0)+"/"+result.optInt("imageElements",0)+
                    " • gagal "+result.optInt("imagesFailed",0)+" • poster "+posters+"/"+items.length()+
                    " • tanpa poster "+missing+" • scroll "+scanPasses+(scrollLimited?" (batas tercapai)":"")+
                    " • error resource HTTP "+resourceHttpErrors+", jaringan "+resourceNetworkErrors;
                if(items.length()>0){
                    save(result,items);
                    status.setText("Ditemukan "+items.length()+" kartu video. Indeks disimpan.\n"+metrics+
                        (missing>0?"\nTautan tanpa poster tetap disimpan.":"")+
                        "\nAnda dapat membuka halaman lain dan memindai lagi.");
                    Toast.makeText(this,"Katalog: "+items.length()+" item ditemukan",Toast.LENGTH_LONG).show();
                }else{
                    String msg=result.optString("error","");
                    status.setText("Belum ada kartu video yang cocok. "+metrics+
                        " • kandidat kartu "+result.optInt("cardsExamined",0)+
                        " • adapter "+result.optString("adapter","generic")+
                        (lastResourceFailure.isEmpty()?"":" • resource terakhir gagal: "+lastResourceFailure)+
                        (msg.isEmpty()?"":" • "+msg));
                    if(manual)Toast.makeText(this,"Belum menemukan kartu katalog pada halaman ini",Toast.LENGTH_LONG).show();
                }
            }catch(Exception e){
                status.setText("Gagal membaca hasil pemindaian: "+e.getMessage());
            }
        });
    }

    private void finishScanError(int generation,String message){
        if(!isScanCurrent(generation))return;
        scanning=false;scanGeneration++;handler.removeCallbacks(scanWatchdog);
        status.setText(message);
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
        java.util.LinkedHashMap<String,Integer> positions=new java.util.LinkedHashMap<>();
        // URL is the only identity. Records without a URL are kept independently.
        for(int i=0;oldVideos!=null&&i<oldVideos.length();i++){
            JSONObject old=oldVideos.optJSONObject(i);if(old==null)continue;
            normalizeVideo(old);
            String key=videoIdentity(old);
            if(key.isEmpty()){merged.put(old);continue;}
            if(!positions.containsKey(key)){positions.put(key,merged.length());merged.put(old);}
        }
        // Fresh scan data replaces the whole matching record, never fields found by title.
        // A missing poster may reuse the previous poster only for this exact page URL.
        for(int i=0;i<videos.length();i++){
            JSONObject fresh=videos.optJSONObject(i);if(fresh==null)continue;
            normalizeVideo(fresh);
            String key=videoIdentity(fresh);
            if(key.isEmpty()){merged.put(fresh);continue;}
            Integer at=positions.get(key);
            if(at==null){positions.put(key,merged.length());merged.put(fresh);}
            else {
                JSONObject old=merged.optJSONObject(at);
                if(old!=null){
                    String oldPoster=old.optString("poster","");
                    String oldReferer=old.optString("posterReferer","");
                    if(fresh.optString("poster","").isEmpty()&&!oldPoster.isEmpty()){
                        fresh.put("poster",oldPoster);
                        if(fresh.optString("posterReferer","").isEmpty()&&!oldReferer.isEmpty())fresh.put("posterReferer",oldReferer);
                    }
                    merged.put(at,fresh);
                }
            }
        }
        for(int i=0;i<merged.length();i++){
            JSONObject v=merged.optJSONObject(i);
            String embed=v==null?"":v.optString("embedUrl","");
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
            .put("lastScanDiagnostics",new JSONObject().put("anchorsChecked",result.optInt("anchorsChecked",0))
                .put("cardsExamined",result.optInt("cardsExamined",0)).put("posters",result.optInt("posters",0))
                .put("missingPosters",result.optInt("missingPosters",0)).put("imageElements",result.optInt("imageElements",0))
                .put("imagesLoaded",result.optInt("imagesLoaded",0)).put("imagesFailed",result.optInt("imagesFailed",0))
                .put("lazyImages",result.optInt("lazyImages",0)).put("scrollPasses",result.optInt("scrollPasses",0))
                .put("scrollLimitReached",result.optBoolean("scrollLimitReached",false))
                .put("resourceHttpErrors",result.optInt("resourceHttpErrors",0))
                .put("resourceNetworkErrors",result.optInt("resourceNetworkErrors",0))
                .put("lastResourceFailure",result.optString("lastResourceFailure","")))
            .put("javascriptItems",merged.length())
            .put("limitations","Pemindaian DOM JavaScript pada halaman publik yang dibuka di WebView. Tidak melewati login, paywall, atau proteksi bot.");
        File tmp=new File(f.getParentFile(),f.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(root.toString(2).getBytes("UTF-8"));out.getFD().sync();}
        if(f.exists()&&!f.delete())throw new java.io.IOException("Gagal mengganti indeks lama");
        if(!tmp.renameTo(f))throw new java.io.IOException("Gagal menyimpan indeks");
    }


    /** URL identity only: titles are display text and are not unique. */
    private String videoIdentity(JSONObject video) {
        String page=video.optString("pageUrl","").trim();
        String embed=video.optString("embedUrl","").trim();
        String value=!page.isEmpty()?page:embed;
        if(value.isEmpty())return "";
        int hash=value.indexOf('#');
        if(hash>=0)value=value.substring(0,hash);
        return value.trim();
    }

    private String stableVideoId(String identity) {
        if(identity==null||identity.isEmpty())return "";
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder();
            for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));
            return out.toString();
        } catch(Exception e) { return Integer.toHexString(identity.hashCode()); }
    }

    private void normalizeVideo(JSONObject video) throws Exception {
        String page=video.optString("pageUrl","").trim();
        String embed=video.optString("embedUrl","").trim();
        String identity=videoIdentity(video);
        video.put("schema","streamhub.catalog.v1")
            .put("id",stableVideoId(identity))
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
