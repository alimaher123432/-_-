package iq.edu.hasanschool;

import android.app.Activity;
import android.app.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4101;
    private static final int SAVE_FILE_REQUEST = 4102;
    private static final String APP_HOST = "app.local";
    private static final String APP_URL = "https://" + APP_HOST + "/index.html";

    private WebView webView;
    private ImageView splash;
    private ValueCallback<Uri[]> fileChooserCallback;
    private byte[] pendingSaveBytes;
    private String pendingSaveMime = "application/octet-stream";
    private String pendingSaveName = "file";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        splash = new ImageView(this);
        splash.setImageResource(R.drawable.school_logo);
        splash.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        splash.setBackgroundColor(Color.WHITE);
        int pad = dp(28);
        splash.setPadding(pad, pad, pad, pad);
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        setContentView(root);
        configureWebView();
        webView.loadUrl(APP_URL);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (APP_HOST.equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || path.equals("/") || path.equals("/index.html")) {
                        try {
                            InputStream in = getAssets().open("index.html");
                            return new WebResourceResponse("text/html", "UTF-8", in);
                        } catch (IOException e) {
                            return new WebResourceResponse("text/plain", "UTF-8",
                                    new ByteArrayInputStream("Unable to load app".getBytes()));
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (APP_HOST.equals(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAndroidHooks();
                new Handler().postDelayed(() -> {
                    if (splash != null && splash.getParent() != null) {
                        splash.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                            ViewGroup p = (ViewGroup) splash.getParent();
                            if (p != null) p.removeView(splash);
                            splash = null;
                        }).start();
                    }
                }, 450);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(resolveMime(params));
                if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "تعذّر فتح الملفات", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
    }

    private String resolveMime(WebChromeClient.FileChooserParams params) {
        if (params != null) {
            String[] types = params.getAcceptTypes();
            if (types != null) {
                for (String t : types) {
                    if (t != null && !t.trim().isEmpty() && !t.equals("*/*")) return t;
                }
            }
        }
        return "*/*";
    }

    private void injectAndroidHooks() {
        String js = "(function(){" +
                "if(window.__androidHooksInstalled)return;window.__androidHooksInstalled=true;" +
                "window.print=function(){AndroidBridge.printPage();};" +
                "document.addEventListener('click',function(e){" +
                "var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;" +
                "if(!a||!a.href)return;" +
                "if(a.href.indexOf('blob:')===0){e.preventDefault();e.stopPropagation();" +
                "fetch(a.href).then(function(r){return r.blob();}).then(function(b){" +
                "var fr=new FileReader();fr.onload=function(){AndroidBridge.saveBase64File(fr.result,a.download||'file',b.type||'application/octet-stream');};fr.readAsDataURL(b);" +
                "}).catch(function(){});}" +
                "},true);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    String job = "امتحان - منصة إعدادية الشهيد حسن نصر الله";
                    pm.print(job, webView.createPrintDocumentAdapter(job), null);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذّرت الطباعة", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void saveBase64File(String dataUrl, String fileName, String mime) {
            try {
                int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
                String b64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                pendingSaveBytes = Base64.decode(b64, Base64.DEFAULT);
                pendingSaveName = sanitizeFileName(fileName);
                pendingSaveMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                runOnUiThread(() -> launchSavePicker());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "تعذّر تجهيز الملف للحفظ", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "file";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void launchSavePicker() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(pendingSaveMime);
        i.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
        try {
            startActivityForResult(i, SAVE_FILE_REQUEST);
        } catch (Exception e) {
            pendingSaveBytes = null;
            Toast.makeText(this, "تعذّر فتح نافذة الحفظ", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileChooserCallback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
            return;
        }

        if (requestCode == SAVE_FILE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) {
                        out.write(pendingSaveBytes);
                        out.flush();
                        Toast.makeText(this, "تم حفظ الملف", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    Toast.makeText(this, "تعذّر حفظ الملف", Toast.LENGTH_SHORT).show();
                }
            }
            pendingSaveBytes = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
