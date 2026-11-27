package me.champdev.weblock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DuckDuckGo web search and portable list of user-added streaming websites. */
public class SearchWebActivity extends Activity {
    private static final int EXPORT_FILE=71, IMPORT_FILE=72;
    private LinearLayout results, saved;
    private EditText query;
    private TextView status;
    private final LinkedHashMap<String,JSONObject> found=new LinkedHashMap<>();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Home.BG);
        getWindow().setNavigationBarColor(Home.BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Home.BG);
        root.setPadding(Home.dp(this,14),Home.dp(this,12),Home.dp(this,14),Home.dp(this,12));
        TextView title=new TextView(this); title.setText("CARI FILM DI INTERNET"); title.setTextColor(Color.WHITE); title.setTextSize(21); title.setTypeface(null,1);
        root.addView(title);
        TextView hint=new TextView(this); hint.setText("Cari judul film atau serial melalui DuckDuckGo. Simpan situs hasil pencarian ke Website Saya."); hint.setTextColor(Home.MUTED); hint.setTextSize(13);
        root.addView(hint);
        query=new EditText(this); query.setSingleLine(true); query.setTextColor(Color.WHITE); query.setHintTextColor(Home.MUTED); query.setHint("Contoh: judul film atau nama situs");
        query.setPadding(14,8,14,8); query.setBackgroundColor(Home.CARD);
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,-2); qp.topMargin=Home.dp(this,12); root.addView(query,qp);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button search=button("Cari"); search.setOnClickListener(v->search()); actions.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        Button add=button("Tambah alamat"); add.setOnClickListener(v->manualAdd()); actions.addView(add,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);
        LinearLayout fileActions=new LinearLayout(this);
        Button export=button("Ekspor daftar"); export.setOnClickListener(v->exportList()); fileActions.addView(export,new LinearLayout.LayoutParams(0,-2,1));
        Button imp=button("Impor daftar"); imp.setOnClickListener(v->importList()); fileActions.addView(imp,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(fileActions);
        status=new TextView(this); status.setTextColor(Home.MUTED); status.setTextSize(12); status.setPadding(0,Home.dp(this,8),0,Home.dp(this,8)); root.addView(status);
        ScrollView scroll=new ScrollView(this); results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); scroll.addView(results);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        showSavedCount();
    }

    private Button button(String label) {
        Button b=new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setAllCaps(false); return b;
    }

    private void search() {
        String q=query.getText().toString().trim();
        if(q.length()<2){query.setError("Masukkan minimal 2 karakter");return;}
        status.setText("Mencari di DuckDuckGo…"); results.removeAllViews(); found.clear();
        App.io.execute(()->{
            String html="";
            try {
                String address="https://html.duckduckgo.com/html/?q="+Uri.encode(q);
                java.net.HttpURLConnection c=Warp.open(address); c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(12000); c.setReadTimeout(18000);
                c.setRequestProperty("Accept","text/html"); c.connect();
                try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[8192]; int n,total=0;
                    while((n=in.read(buf))>0 && total<900*1024){out.write(buf,0,n);total+=n;}
                    html=out.toString("UTF-8");
                } finally {c.disconnect();}
            } catch(Exception e) {
                final String msg=e.getMessage()==null?"koneksi gagal":e.getMessage();
                runOnUiThread(()->status.setText("Pencarian gagal: "+msg)); return;
            }
            List<JSONObject> items=parse(html);
            runOnUiThread(()->{
                if(items.isEmpty()){status.setText("Tidak ada hasil yang bisa dibaca. Coba kata kunci lain atau tambah alamat website secara manual.");return;}
                status.setText(items.size()+" hasil ditemukan. Pilih Tambah ke Website Saya pada hasil yang ingin disimpan.");
                for(JSONObject item:items) addResult(item);
            });
        });
    }

    private List<JSONObject> parse(String html) {
        LinkedHashMap<String,JSONObject> out=new LinkedHashMap<>();
        Pattern p=Pattern.compile("(?is)<a[^>]+class=[\"'][^\"']*result__a[^\"']*[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
        Matcher m=p.matcher(html==null?"":html);
        while(m.find()&&out.size()<30){
            String url=decodeEntities(m.group(1)), title=clean(m.group(2));
            try {
                Uri u=Uri.parse(url);
                if(url.startsWith("//"))url="https:"+url;
                if(url.startsWith("/l/?")) {
                    String target=Uri.parse("https://duckduckgo.com"+url).getQueryParameter("uddg");
                    if(target!=null)url=target;
                }
                u=Uri.parse(url);
                String scheme=u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.ROOT);
                if(!("http".equals(scheme)||"https".equals(scheme))||u.getHost()==null||u.getHost().contains("duckduckgo.com"))continue;
                url=u.buildUpon().fragment(null).build().toString();
                if(title.length()<2||out.containsKey(url))continue;
                JSONObject o=new JSONObject().put("title",title).put("url",url);
                out.put(url,o);
            }catch(Exception ignored){}
        }
        return new ArrayList<>(out.values());
    }

    private String clean(String s){return decodeEntities(s.replaceAll("(?is)<[^>]+>"," ").replaceAll("\\s+"," ").trim());}
    private String decodeEntities(String s){
        return s.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&lt;","<").replace("&gt;",">").replace("&#x2F;","/");
    }

    private void addResult(JSONObject item) {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(Home.dp(this,10),Home.dp(this,8),Home.dp(this,10),Home.dp(this,8)); card.setBackgroundColor(Home.CARD);
        TextView name=new TextView(this); name.setText(item.optString("title")); name.setTextColor(Color.WHITE); name.setTextSize(15); name.setTypeface(null,1); card.addView(name);
        TextView url=new TextView(this); url.setText(item.optString("url")); url.setTextColor(Home.ACCENT); url.setTextSize(11); card.addView(url);
        LinearLayout row=new LinearLayout(this);
        Button save=button("Tambah ke Website Saya"); save.setOnClickListener(v->addSite(item.optString("url")));
        Button open=button("Buka"); open.setOnClickListener(v->startActivity(WebActivity.open(this,item.optString("title"),item.optString("url"),item.optString("url"))));
        row.addView(save,new LinearLayout.LayoutParams(0,-2,1)); row.addView(open,new LinearLayout.LayoutParams(0,-2,1)); card.addView(row);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=Home.dp(this,6); results.addView(card,lp);
    }

    private void addSite(String url) {
        if(isSaved(url)){Toast.makeText(this,"Website sudah ada dalam daftar.",Toast.LENGTH_SHORT).show();return;}
        status.setText("Menyimpan website dan membuat indeks…");
        CustomSites.add(this,url,msg->{status.setText(msg);showSavedCount();});
    }

    private boolean isSaved(String url) {
        try {
            JSONObject root=new JSONObject(readFile("custom-sites.json")); JSONArray a=root.optJSONArray("sites");
            for(int i=0;a!=null&&i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&url.equals(o.optString("url")))return true;}
        }catch(Exception ignored){}
        return false;
    }

    private String readFile(String name) {
        try(InputStream in=new java.io.FileInputStream(new java.io.File(getFilesDir(),name));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString("UTF-8");
        }catch(Exception e){return "{}";}
    }

    private void showSavedCount() {
        try {JSONArray a=new JSONObject(readFile("custom-sites.json")).optJSONArray("sites");status.setText((a==null?0:a.length())+" website tersimpan. Daftar bisa diekspor untuk dipindahkan atau dicadangkan.");}
        catch(Exception ignored){}
    }

    private void manualAdd() {
        EditText in=new EditText(this);in.setSingleLine(true);in.setHint("https://alamat-website.com");in.setTextColor(Color.WHITE);
        new AlertDialog.Builder(this).setTitle("Tambah alamat website").setView(in)
            .setPositiveButton("Simpan",(d,w)->{String u=in.getText().toString().trim();if(!u.isEmpty())addSite(u);})
            .setNegativeButton("Batal",null).show();
    }

    private void exportList() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"streamhub-websites.json");
        startActivityForResult(i,EXPORT_FILE);
    }

    private void importList() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        startActivityForResult(i,IMPORT_FILE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try {
            if(requestCode==EXPORT_FILE) {
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new java.io.IOException("Tidak dapat membuat file");
                    JSONObject root=new JSONObject(readFile("custom-sites.json"));
                    root.put("format","streamhub-websites").put("exportedAt",System.currentTimeMillis());
                    out.write(root.toString(2).getBytes("UTF-8"));out.flush();
                }
                Toast.makeText(this,"Daftar website berhasil diekspor.",Toast.LENGTH_LONG).show();
            } else if(requestCode==IMPORT_FILE) {
                String json;
                try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    if(in==null)throw new java.io.IOException("File tidak dapat dibuka");
                    byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))>0&&total<2*1024*1024){out.write(b,0,n);total+=n;}json=out.toString("UTF-8");
                }
                JSONObject root=new JSONObject(json);JSONArray a=root.optJSONArray("sites");
                if(a==null)throw new IllegalArgumentException("Format JSON tidak memiliki daftar sites.");
                int added=0;
                for(int x=0;x<a.length();x++){
                    JSONObject o=a.optJSONObject(x);if(o==null)continue;
                    String address=o.optString("url","").trim();
                    if(!address.matches("(?i)^https?://.+")||isSaved(address))continue;
                    added++;CustomSites.add(this,address,null);
                }
                Toast.makeText(this,added+" alamat baru diimpor. Situs diproses di latar belakang.",Toast.LENGTH_LONG).show();
                showSavedCount();
            }
        }catch(Exception e){Toast.makeText(this,"Gagal memproses file: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
