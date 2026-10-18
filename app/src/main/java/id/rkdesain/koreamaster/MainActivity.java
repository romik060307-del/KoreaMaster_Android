package id.rkdesain.koreamaster;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://koreamasterbyrk.netlify.app/";
    private static final int CREATE_EXPORT_REQUEST = 7014;
    private static final int MAX_HTML_BYTES = 2_500_000;
    private static final int MAX_EXPORT_BYTES = 5_000_000;

    private FrameLayout root;
    private WebView webView;
    private File latestHtmlFile;
    private String activeHtml = "";
    private boolean activeHtmlWasCached = false;
    private byte[] pendingExport;
    private String pendingExportMime = "application/json";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        latestHtmlFile = new File(getFilesDir(), "korea_master_latest.html");
        root = new FrameLayout(this);
        setContentView(root);
        activeHtmlWasCached = latestHtmlFile.isFile() && latestHtmlFile.length() > 100_000;
        activeHtml = readLocalHtml();
        createWebView();
        loadHtml(activeHtml);
        checkForNetlifyUpdate(activeHtml, activeHtmlWasCached);
    }

    private String readLocalHtml() {
        if (latestHtmlFile.isFile() && latestHtmlFile.length() > 100_000) {
            try (InputStream in = new java.io.FileInputStream(latestHtmlFile)) {
                return readUtf8Limited(in, MAX_HTML_BYTES);
            } catch (Exception ignored) { }
        }
        try (InputStream in = getAssets().open("index.html")) {
            return readUtf8Limited(in, MAX_HTML_BYTES);
        } catch (Exception e) {
            return "<!doctype html><html lang='id'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Korea Master</title><body style='font-family:sans-serif;padding:24px;background:#fff8e8'><h2>Korea Master</h2><p>Materi lokal belum dapat dibuka. Coba tutup lalu buka aplikasi lagi.</p></body></html>";
        }
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(0xFFFFF8E8);
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        webView.addJavascriptInterface(new ExportBridge(), "KoreaMasterBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) {
                if (title != null && title.toLowerCase(Locale.ROOT).contains("korea master")) {
                    setTitle("Korea Master");
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                        && uri.getHost() != null && !"koreamasterbyrk.netlify.app".equalsIgnoreCase(uri.getHost())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { }
                    return true;
                }
                return false;
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view == webView) {
                    try { root.removeView(view); view.destroy(); } catch (Exception ignored) { }
                    createWebView();
                    loadHtml(activeHtml);
                }
                return true;
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void loadHtml(String html) {
        activeHtml = html == null ? "" : html;
        String patched = installExportBridgeScript(activeHtml);
        webView.loadDataWithBaseURL(BASE_URL, patched, "text/html", "UTF-8", null);
    }

    // The original site is one self-contained HTML file. This small adapter only makes
    // its existing Blob/JSON export work with Android's Save As dialog.
    private String installExportBridgeScript(String html) {
        String script = "<script>(function(){try{"
                + "var map=new Map(),orig=URL.createObjectURL.bind(URL);"
                + "URL.createObjectURL=function(blob){var u=orig(blob);map.set(u,blob);return u;};"
                + "document.addEventListener('click',function(ev){var t=ev.target;var a=t&&t.closest?t.closest('a[download]'):null;"
                + "if(!a)return;var u=a.href||'';if(u.indexOf('blob:')!==0)return;var b=map.get(u);"
                + "if(!b||!window.KoreaMasterBridge||!window.KoreaMasterBridge.saveBase64ToFile)return;"
                + "ev.preventDefault();ev.stopImmediatePropagation();var r=new FileReader();"
                + "r.onloadend=function(){try{var z=String(r.result||'').split(',');if(z.length>1)window.KoreaMasterBridge.saveBase64ToFile(a.download||'korea_master.json',z[1],b.type||'application/json');}catch(e){}};"
                + "r.readAsDataURL(b);},true);"
                + "}catch(e){}})();</script>";
        int at = html.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
        if (at >= 0) return html.substring(0, at) + script + html.substring(at);
        return html + script;
    }

    private boolean hasUsableNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = cm.getActiveNetwork();
                if (active == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            }
            return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
        } catch (Exception ignored) { return false; }
    }

    private void checkForNetlifyUpdate(final String htmlAtLaunch, final boolean hadCacheAtLaunch) {
        if (!hasUsableNetwork()) return;
        Thread worker = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                SharedPreferences prefs = getSharedPreferences("netlify_update", MODE_PRIVATE);
                URL url = new URL(BASE_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5500);
                connection.setReadTimeout(8000);
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "KoreaMasterAndroid/1.0 (offline update checker)");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                connection.setRequestProperty("Cache-Control", "no-cache");
                String etag = prefs.getString("etag", "");
                if (!etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);
                long lastModified = prefs.getLong("last_modified", 0L);
                if (lastModified > 0) connection.setIfModifiedSince(lastModified);

                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_NOT_MODIFIED) return;
                if (status != HttpURLConnection.HTTP_OK) return;
                String finalHost = connection.getURL().getHost();
                if (!"koreamasterbyrk.netlify.app".equalsIgnoreCase(finalHost)) return;
                String remoteHtml;
                try (InputStream in = connection.getInputStream()) {
                    remoteHtml = readUtf8Limited(in, MAX_HTML_BYTES);
                }
                if (!isExpectedSiteHtml(remoteHtml)) return;

                String newHash = sha256(remoteHtml);
                String runningHash = sha256(htmlAtLaunch);
                boolean changed = !newHash.equals(runningHash);
                boolean needInitialCache = !latestHtmlFile.isFile();
                if (changed || needInitialCache) writeHtmlAtomically(remoteHtml);

                SharedPreferences.Editor edit = prefs.edit();
                String newEtag = connection.getHeaderField("ETag");
                if (newEtag != null && !newEtag.isEmpty()) edit.putString("etag", newEtag);
                long newModified = connection.getLastModified();
                if (newModified > 0) edit.putLong("last_modified", newModified);
                edit.putString("html_sha256", newHash).putLong("checked_at", System.currentTimeMillis()).apply();

                if (changed) {
                    runOnUiThread(() -> {
                        if (!hadCacheAtLaunch) {
                            activeHtmlWasCached = true;
                            loadHtml(remoteHtml);
                            Toast.makeText(MainActivity.this, "Versi Netlify terbaru berhasil dimuat.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Update tersimpan. Tutup lalu buka aplikasi lagi untuk menerapkannya.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception ignored) {
                // Offline/timeout/update error: keep using the local copy without interrupting study.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "korea-master-update");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean isExpectedSiteHtml(String html) {
        if (html == null || html.length() < 100_000 || html.length() > MAX_HTML_BYTES) return false;
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("<html") && lower.contains("</html>")
                && lower.contains("korea master") && lower.contains("source_vocab");
    }

    private void writeHtmlAtomically(String html) throws Exception {
        File temp = new File(getFilesDir(), "korea_master_latest.tmp");
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(html.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (latestHtmlFile.exists() && !latestHtmlFile.delete()) {
            throw new java.io.IOException("Tidak bisa mengganti cache lama");
        }
        if (!temp.renameTo(latestHtmlFile)) {
            try (FileOutputStream out = new FileOutputStream(latestHtmlFile, false)) {
                out.write(html.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            temp.delete();
        }
    }

    private static String readUtf8Limited(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) throw new java.io.IOException("Konten melebihi batas aman");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }

    private class ExportBridge {
        @JavascriptInterface
        public void saveBase64ToFile(String fileName, String base64, String mimeType) {
            try {
                if (base64 == null || base64.length() > (MAX_EXPORT_BYTES * 4L / 3L) + 5000L) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "File ekspor terlalu besar.", Toast.LENGTH_LONG).show());
                    return;
                }
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (bytes.length > MAX_EXPORT_BYTES) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "File ekspor terlalu besar.", Toast.LENGTH_LONG).show());
                    return;
                }
                String candidateName = fileName == null ? "korea_master.json" : new File(fileName).getName();
                candidateName = candidateName.replaceAll("[^A-Za-z0-9._-]", "_");
                if (candidateName.isEmpty()) candidateName = "korea_master.json";
                if (!candidateName.toLowerCase(Locale.ROOT).endsWith(".json")) candidateName += ".json";
                final String safeName = candidateName;
                pendingExport = bytes;
                pendingExportMime = (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("json"))
                        ? "application/json" : "text/plain";
                runOnUiThread(() -> openSaveDialog(safeName));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Ekspor gagal. Coba sekali lagi.", Toast.LENGTH_LONG).show());
            }
        }
    }

    private void openSaveDialog(String filename) {
        if (pendingExport == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(pendingExportMime);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try { startActivityForResult(intent, CREATE_EXPORT_REQUEST); }
        catch (ActivityNotFoundException e) {
            pendingExport = null;
            Toast.makeText(this, "Aplikasi pengelola berkas tidak tersedia.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_EXPORT_REQUEST) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                if (out == null) throw new java.io.IOException("Tidak dapat membuka berkas tujuan");
                out.write(pendingExport);
                out.flush();
                Toast.makeText(this, "JSON berhasil disimpan.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Gagal menyimpan JSON. Coba lokasi lain.", Toast.LENGTH_LONG).show();
            }
        }
        pendingExport = null;
    }

    @Override
    public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript("(function(){var s=document.querySelector('.page-section:not(.hidden)');return s?s.id:'page-home';})()", value -> {
            String page = value == null ? "page-home" : value.replace("\"", "");
            if (!"page-home".equals(page) && !"null".equals(page)) {
                webView.evaluateJavascript("if(typeof showPage==='function')showPage('home');else window.scrollTo(0,0);", null);
            } else {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try { root.removeView(webView); webView.stopLoading(); webView.removeJavascriptInterface("KoreaMasterBridge"); webView.destroy(); }
            catch (Exception ignored) { }
            webView = null;
        }
        super.onDestroy();
    }
}
