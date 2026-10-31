package ua.massage.pay;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private class Bridge {
        @JavascriptInterface
        public void request(final String id, final String json) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int status = 0;
                    String out;
                    try {
                        JSONObject r = new JSONObject(json);
                        URL u = new URL(r.getString("url"));
                        if (!"https".equals(u.getProtocol()) || !"api.alteg.io".equals(u.getHost())) {
                            throw new Exception("Разрешены только запросы к api.alteg.io");
                        }
                        HttpURLConnection c = (HttpURLConnection) u.openConnection();
                        c.setRequestMethod(r.optString("method", "GET"));
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(30000);
                        JSONObject h = r.optJSONObject("headers");
                        if (h != null) {
                            Iterator<String> it = h.keys();
                            while (it.hasNext()) {
                                String k = it.next();
                                c.setRequestProperty(k, h.getString(k));
                            }
                        }
                        if (r.has("body") && !r.isNull("body")) {
                            byte[] b = r.getString("body").getBytes(StandardCharsets.UTF_8);
                            c.setDoOutput(true);
                            OutputStream os = c.getOutputStream();
                            os.write(b);
                            os.close();
                        }
                        status = c.getResponseCode();
                        InputStream is = status >= 400 ? c.getErrorStream() : c.getInputStream();
                        out = is == null ? "" : readAll(is);
                        c.disconnect();
                    } catch (Exception e) {
                        status = 0;
                        out = String.valueOf(e.getMessage());
                    }
                    final String js = "window.__nativeCb(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(out) + ")";
                    web.post(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript(js, null);
                        }
                    });
                }
            }).start();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
}
