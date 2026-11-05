package com.inkd.reader;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.view.View;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private static final int PICK_EPUB = 42;
    private WebView web;
    private final ArrayList<String> chapters = new ArrayList<>();
    private int chapter = 0, fontSize = 19, theme = 0;
    private String title = "INKD";
    private FrameLayout frame;
    private TextView status;
    private String lastBook = "";
    private LinearLayout toolbar, bottom;
    private boolean controlsVisible = false;
    private int page = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f0e5);
        toolbar = new LinearLayout(this); toolbar.setGravity(17);
        Button open = button("EPUB", () -> pick());
        Button smaller = button("A−", () -> {fontSize=Math.max(12,fontSize-2); render();});
        Button bigger = button("A+", () -> {fontSize=Math.min(34,fontSize+2); render();});
        Button colors = button("☼", () -> {theme=(theme+1)%3; render();});
        toolbar.addView(open); toolbar.addView(smaller); toolbar.addView(bigger); toolbar.addView(colors);
        root.addView(toolbar);
        frame = new FrameLayout(this);
        web = new WebView(this); web.setBackgroundColor(0xfff5f0e5);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void turn(int dir) { runOnUiThread(() -> move(dir)); }
            @JavascriptInterface public void chapter(int dir) { runOnUiThread(() -> changeChapter(dir)); }
            @JavascriptInterface public void menu() { runOnUiThread(() -> toggleControls()); }
            @JavascriptInterface public void progress(int current, int total) { runOnUiThread(() -> {
                page = current;
                status.setText(chapters.isEmpty()?"Bibliothèque": "Chapitre " +(chapter+1)+"/"+chapters.size()+"  ·  Page "+(current+1)+"/"+total);
            }); }
        }, "InkBridge");
        web.setWebViewClient(new WebViewClient());
        frame.addView(web);
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        bottom = new LinearLayout(this); bottom.setGravity(17);
        bottom.addView(button("◀", () -> move(-1)));
        status = new TextView(this); status.setTextColor(0xff333333); status.setGravity(17);
        bottom.addView(status,new LinearLayout.LayoutParams(0,55,1));
        bottom.addView(button("▶", () -> move(1)));
        root.addView(bottom);
        setContentView(root);
        toolbar.setVisibility(View.GONE);
        bottom.setVisibility(View.GONE);
        render();
    }
    private void toggleControls() {
        controlsVisible = !controlsVisible;
        toolbar.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        bottom.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
    }
    private Button button(String text, Runnable click) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v->click.run()); return b; }
    private void pick() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/epub+zip"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/epub+zip","application/octet-stream"}); startActivityForResult(i,PICK_EPUB); }
    @Override protected void onActivityResult(int request,int result,Intent data) { super.onActivityResult(request,result,data); if(request==PICK_EPUB&&result==RESULT_OK&&data!=null) { try { load(data.getData()); } catch(Exception e) { Toast.makeText(this,"EPUB non pris en charge : "+e.getMessage(),Toast.LENGTH_LONG).show(); } } }
    private static byte[] read(InputStream in) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) { out.write(b,0,n); if(out.size()>50_000_000) throw new IOException("Livre trop volumineux"); } return out.toByteArray(); }
    private static Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder b=f.newDocumentBuilder();
        b.setEntityResolver((publicId,systemId)->new org.xml.sax.InputSource(new java.io.StringReader("")));
        return b.parse(new ByteArrayInputStream(bytes));
    }
    private static Element first(Document d,String tag) { return (Element)d.getElementsByTagNameNS("*",tag).item(0); }
    private static String normalized(String base,String rel) { try { return new java.net.URI(null,null,"/"+base,null).resolve(rel).normalize().getPath().substring(1); } catch(Exception e) {return rel;} }
    private void load(Uri uri) throws Exception {
        Map<String,byte[]> files=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(getContentResolver().openInputStream(uri))) { ZipEntry entry; int count=0; while((entry=zip.getNextEntry())!=null) { if(!entry.isDirectory() && ++count<2000) {byte[] bytes=read(zip); files.put(entry.getName(),bytes);} zip.closeEntry(); } }
        byte[] container=files.get("META-INF/container.xml"); if(container==null) throw new IOException("container.xml absent");
        Document cont=parse(container); Element rootfile=first(cont,"rootfile"); if(rootfile==null) throw new IOException("OPF absent");
        String opf=rootfile.getAttribute("full-path"); Document packageDoc=parse(files.get(opf));
        String base=opf.contains("/")?opf.substring(0,opf.lastIndexOf('/')+1):"";
        Element t=first(packageDoc,"title"); title=t==null?"Mon livre":t.getTextContent();
        HashMap<String,String> manifest=new HashMap<>(); NodeList items=packageDoc.getElementsByTagNameNS("*","item");
        for(int j=0;j<items.getLength();j++) {Element el=(Element)items.item(j); manifest.put(el.getAttribute("id"),normalized(base,el.getAttribute("href")));}
        ArrayList<String> parsed=new ArrayList<>(); NodeList refs=packageDoc.getElementsByTagNameNS("*","itemref");
        for(int j=0;j<refs.getLength();j++) {Element ref=(Element)refs.item(j); String path=manifest.get(ref.getAttribute("idref")); byte[] bytes=files.get(path); if(bytes==null)continue;
            try {Document doc=parse(bytes); Element body=first(doc,"body"); if(body!=null) {StringBuilder sb=new StringBuilder(); NodeList children=body.getChildNodes(); for(int k=0;k<children.getLength();k++) appendNode(children.item(k),sb); if(sb.length()>0)parsed.add(sb.toString());}} catch(Exception ignored){} }
        if(parsed.isEmpty()) throw new IOException("Aucun chapitre XHTML lisible");
        chapters.clear(); chapters.addAll(parsed); chapter=0; lastBook=uri.toString();
        chapter=getPreferences(0).getInt("position_"+lastBook,0); chapter=Math.min(chapter,chapters.size()-1); page=0; render();
    }
    private static String escape(String s) {return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void appendNode(Node node,StringBuilder sb) {if(node.getNodeType()==Node.TEXT_NODE) {sb.append(escape(node.getNodeValue()));return;} if(node.getNodeType()!=Node.ELEMENT_NODE)return;
        String name=node.getLocalName(); if(name==null)return; name=name.toLowerCase(Locale.ROOT);
        String tags=" p h1 h2 h3 h4 h5 h6 div span em strong b i blockquote ul ol li br hr pre code sup sub ";
        boolean allowed=tags.contains(" "+name+" "); if(allowed)sb.append('<').append(name).append('>');
        NodeList children=node.getChildNodes(); for(int i=0;i<children.getLength();i++)appendNode(children.item(i),sb);
        if(allowed&&!name.equals("br")&&!name.equals("hr"))sb.append("</").append(name).append('>');
    }
    private void move(int dir) {
        if(chapters.isEmpty()) return;
        web.evaluateJavascript("window.inkdTurn && window.inkdTurn("+dir+")", null);
    }
    private void changeChapter(int dir) {
        int next=chapter+dir;
        if(next<0||next>=chapters.size())return;
        chapter=next; page=0;
        getPreferences(0).edit().putInt("position_"+lastBook,chapter).apply();
        render();
    }
    private void render() {
        String bg=theme==0?"#f5f0e5":theme==1?"#ffffff":"#000000";
        String fg=theme==2?"#c9c9c9":"#252525";
        String body=chapters.isEmpty()?"<h1>INKD</h1><p>Ta liseuse Android, avec un effet E-Ink.</p><p>Appuie sur <b>EPUB</b> pour ouvrir un livre.</p>":chapters.get(chapter);
        status.setText(chapters.isEmpty()?"Bibliothèque":(chapter+1)+" / "+chapters.size()); status.setTextColor(theme==2?0xffcccccc:0xff333333);
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"+
            "<style>*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:"+bg+";color:"+fg+"}"+
            "body{font-family:Georgia,serif;font-size:"+fontSize+"px;line-height:1.65}"+
            "#reader{height:100dvh;width:100vw;overflow:hidden;position:relative}"+
            "#pages{height:100%;width:100%;padding:30px 27px 42px;column-width:calc(100vw - 54px);column-gap:54px;column-fill:auto;overflow:hidden;transition:none}"+
            "p{text-align:justify;margin:0 0 1em}h1,h2,h3{line-height:1.25;text-align:left;break-after:avoid}img{max-width:100%}"+
            "#ghost{position:fixed;inset:0;pointer-events:none;opacity:0;z-index:8;background:"+bg+"}"+
            "#flash{position:fixed;inset:0;pointer-events:none;opacity:0;z-index:9;background:"+(theme==2?"#fff":"#111")+"}"+
            "#hint{position:fixed;bottom:9px;left:0;right:0;text-align:center;font:11px sans-serif;opacity:.35;pointer-events:none}"+
            "</style></head><body><div id='reader'><div id='pages'>"+body+"</div></div><div id='ghost'></div><div id='flash'></div><div id='hint'>INKD</div>"+
            "<script>const pages=document.getElementById('pages'),ghost=document.getElementById('ghost'),flash=document.getElementById('flash');"+
            "let index=0,total=1,busy=false,x=0,y=0;"+
            "function measure(){let old=index;pages.style.transform='none';pages.scrollLeft=0;"+
            "let w=pages.clientWidth;total=Math.max(1,Math.ceil(pages.scrollWidth/w));index=Math.min(old,total-1);go(false)}"+
            "function go(animate){let w=pages.clientWidth;pages.scrollLeft=index*w;InkBridge.progress(index,total)}"+
            "function turn(dir){if(busy)return;let next=index+dir;if(next<0){InkBridge.chapter(-1);return;}if(next>=total){InkBridge.chapter(1);return;}"+
            "busy=true;let old=pages.cloneNode(true);old.id='ghostcopy';old.style.cssText='position:absolute;inset:0;overflow:hidden;background:"+bg+";';"+
            "ghost.innerHTML='';ghost.appendChild(old);old.scrollLeft=index*pages.clientWidth;ghost.style.opacity='0.48';"+
            "index=next;go(false);flash.style.opacity='.72';"+
            "flash.animate([{opacity:.72},{opacity:.25,offset:.35},{opacity:0}],{duration:520,easing:'ease-out'});"+
            "ghost.animate([{opacity:.48},{opacity:.22,offset:.5},{opacity:0}],{duration:680,easing:'ease-out'}).onfinish=()=>{ghost.style.opacity='0';ghost.innerHTML='';busy=false};"+
            "setTimeout(()=>{flash.style.opacity='0'},520)}"+
            "window.inkdTurn=turn;window.addEventListener('resize',measure);"+
            "document.addEventListener('touchstart',e=>{x=e.touches[0].clientX;y=e.touches[0].clientY},{passive:true});"+
            "document.addEventListener('touchend',e=>{let dx=e.changedTouches[0].clientX-x,dy=e.changedTouches[0].clientY-y;if(Math.abs(dx)>65&&Math.abs(dx)>Math.abs(dy))turn(dx<0?1:-1)},{passive:true});"+
            "document.addEventListener('click',e=>{if(window.getSelection().toString())return;let z=e.clientX/innerWidth;if(z<.27)turn(-1);else if(z>.73)turn(1);else InkBridge.menu()});"+
            "setTimeout(measure,120);</script></body></html>";
        web.loadDataWithBaseURL("https://inkd.local/",html,"text/html","UTF-8",null);
    }
}
