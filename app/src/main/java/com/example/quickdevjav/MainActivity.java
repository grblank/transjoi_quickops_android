package com.example.quickdevjav;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message; // Adicionar esta importação para resolver o erro
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;

import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    /*-- CUSTOMIZE --*/
    /*-- you can customize these options for your convenience --*/
    private static final String webview_url   = "https://quickops.transjoi.com.br/";    // web address or local file location you want to open in webview
    private static String file_type     = "image/*";    // file types to be allowed for upload
    private final boolean multiple_files      = false;         // allowing multiple file upload

    /*-- MAIN VARIABLES --*/
    WebView webView;
    private static final String TAG = MainActivity.class.getSimpleName();

    private String cam_file_data = null;        // for storing camera file information
    private ValueCallback<Uri> file_data;       // data/header received after file selection
    private ValueCallback<Uri[]> file_path;     // received file(s) temp. location
    private Uri cameraImageUri = null;          // To store the camera image URI

    private final static int file_req_code = 1;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent){
        super.onActivityResult(requestCode, resultCode, intent);

        Uri[] results = null;

        /*-- if file request cancelled; exited camera. we need to send null value to make future attempts workable --*/
        if (resultCode == Activity.RESULT_CANCELED) {
            if (file_path != null) {
                file_path.onReceiveValue(null);
                file_path = null;
            }
            return;
        }

        /*-- continue if response is positive --*/
        if(resultCode== Activity.RESULT_OK){
            if(file_path == null){
                return;
            }

            ClipData clipData = null;
            String stringData = null;

            try {
                if (intent != null) {
                    clipData = intent.getClipData();
                    stringData = intent.getDataString();
                }
            } catch (Exception e){
                Log.e(TAG, "Error getting intent data: " + e.getMessage());
            }

            if (clipData == null && stringData == null && cameraImageUri != null) {
                // Camera result
                results = new Uri[]{cameraImageUri};
                Log.d(TAG, "Camera image URI: " + cameraImageUri.toString());
            } else {
                if (clipData != null) { // multiple files selected
                    final int numSelectedFiles = clipData.getItemCount();
                    results = new Uri[numSelectedFiles];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        results[i] = clipData.getItemAt(i).getUri();
                    }
                } else if (stringData != null) {
                    // Single file selected from gallery
                    results = new Uri[]{Uri.parse(stringData)};
                } else if (intent != null && intent.getData() != null) {
                    // Fallback for single file selection
                    results = new Uri[]{intent.getData()};
                } else if (intent != null && intent.getExtras() != null && intent.getExtras().get("data") != null) {
                    // Some devices return the bitmap thumbnail instead of URI
                    try {
                        Bitmap bitmap = (Bitmap) intent.getExtras().get("data");
                        if (bitmap != null) {
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
                            String path = MediaStore.Images.Media.insertImage(
                                    getContentResolver(), bitmap, "Title", null);
                            if (path != null) {
                                results = new Uri[]{Uri.parse(path)};
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling camera thumbnail: " + e.getMessage());
                    }
                }
            }
        }

        if (file_path != null) {
            file_path.onReceiveValue(results);
            file_path = null;
        }
    }

    private static final String APP_VERSION = "13032025"; // Defina a versão do aplicativo aqui
    private static final String APP_VERSION_PARAM = "app_version=13032025";

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @SuppressLint({"SetJavaScriptEnabled", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState){
        file_permission();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        assert webView != null;
        WebSettings webSettings = webView.getSettings();

        // Otimizações de performance
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Removido método obsoleto setRenderPriority
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        // Remover métodos obsoletos de cache
        // webSettings.setAppCacheEnabled(true);  // Método obsoleto
        // webSettings.setAppCachePath(appCachePath);  // Método obsoleto
        // webSettings.setAppCacheMaxSize(10 * 1024 * 1024); // Método obsoleto

        // Configurar cache de forma compatível
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setDatabasePath(getApplicationContext().getDir("databases", Context.MODE_PRIVATE).getPath());
        }

        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        // Suporte a PWA
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Acelerar carregamento
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(false);
        }

        // Habilitar suporte a armazenamento
        webSettings.setDomStorageEnabled(true);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_OFF);
        }

        // Otimizar scrolling
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        // Cache moderno
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // NOVO: Configuração para handling de redirecionamentos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // NOVO: Garantir que páginas sejam salvas no histórico corretamente
        webSettings.setSaveFormData(true);
        webSettings.setSavePassword(true);
        webSettings.setSupportMultipleWindows(false);

        // NOVO: Definir UserAgent customizado para evitar problemas de redirecionamento
        String defaultUserAgent = webView.getSettings().getUserAgentString();
        webView.getSettings().setUserAgentString(defaultUserAgent + " QuickOpsApp");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptThirdPartyCookies(webView, true);
        cm.acceptCookie();

        // Adicionar header fixo com a versão do aplicativo
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectCSS(view);
                CookieManager cm = CookieManager.getInstance();
                cm.flush();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl){
                webView.loadUrl(webview_url);
            }

            // CORREÇÃO: Reescrita da implementação do tratamento de URLs para resolver o problema de redirecionamento
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "Tentando carregar URL: " + url);

                // Verificar se é URL externa que deve ser aberta em app nativo
                if (url.startsWith("tel:") ||
                        url.startsWith("whatsapp:") ||
                        url.startsWith("mailto:") ||
                        url.startsWith("sms:") ||
                        url.startsWith("geo:") ||
                        url.endsWith(".apk")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }

                try {
                    Uri uri = Uri.parse(url);
                    String host = uri.getHost();

                    // Se for nosso domínio, processar internamente na WebView
                    if (host != null && host.contains("transjoi.com.br")) {
                        Log.d(TAG, "URL interna detectada, carregando na WebView: " + url);
                        // IMPORTANTE: Retornar false para permitir que a WebView processe a URL internamente
                        return false;
                    } else {
                        // URL externa - abrir no navegador
                        Log.d(TAG, "URL externa detectada, abrindo no navegador: " + url);
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar URL: " + e.getMessage());
                    return false; // Em caso de erro, deixe a WebView tentar processar
                }
            }

            // Novo método para injetar header com a versão do aplicativo, aplicado a cada requisição
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("GET".equalsIgnoreCase(request.getMethod())) {
                    String originalUrl = request.getUrl().toString();
                    String modifiedUrl = originalUrl.contains("?")
                            ? originalUrl + "&" + APP_VERSION_PARAM
                            : originalUrl + "?" + APP_VERSION_PARAM;
                    try {
                        java.net.URL url = new java.net.URL(modifiedUrl);
                        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                        // Copiar os headers originais
                        Map<String, String> originalHeaders = request.getRequestHeaders();
                        if (originalHeaders != null) {
                            for (Map.Entry<String, String> entry : originalHeaders.entrySet()) {
                                connection.setRequestProperty(entry.getKey(), entry.getValue());
                            }
                        }
                        // Adicionar o header "App-Version"
                        connection.setRequestProperty("App-Version", APP_VERSION);
                        // Adicionar os cookies para preservar a sessão
                        String cookie = CookieManager.getInstance().getCookie(modifiedUrl);
                        if (cookie != null) {
                            connection.setRequestProperty("Cookie", cookie);
                        }
                        connection.connect();

                        String contentType = connection.getContentType();
                        String mimeType = "text/html";
                        String encoding = "utf-8";
                        if (contentType != null) {
                            String[] parts = contentType.split(";");
                            if (parts.length > 0) {
                                mimeType = parts[0];
                            }
                            for (String part : parts) {
                                part = part.trim();
                                if (part.startsWith("charset=")) {
                                    encoding = part.substring("charset=".length());
                                }
                            }
                        }
                        return new WebResourceResponse(mimeType, encoding, connection.getInputStream());
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao interceptar requisição: " + e.getMessage());
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.loadUrl(webview_url);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                geo_permission();
                super.onGeolocationPermissionsShowPrompt(origin, callback);
                callback.invoke(origin, true, false);
            }

            // Implementação do método onShowFileChooser para suporte à câmera
            @SuppressLint("QueryPermissionsNeeded")
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                return MainActivity.this.onShowFileChooser(webView, filePathCallback, fileChooserParams);
            }

            // NOVO: Manipular janelas de redirecionamento
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView newWebView = new WebView(MainActivity.this);
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        webView.loadUrl(url);
                        return true;
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();

                return true;
            }
        });
    }

    /*-- callback reporting if error occurs --*/
    public class Callback extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            // Injetar CSS para comportamento nativo
            injectCSS(view);

            // Sincronizar cookies
            CookieManager cm = CookieManager.getInstance();
            cm.flush();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl){
            webView.loadUrl(webview_url);
        }

        // CORREÇÃO: Reescrita da implementação do tratamento de URLs para resolver o problema de redirecionamento
        @SuppressLint("DefaultLocale")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "Tentando carregar URL: " + url);

            // Verificar se é URL externa que deve ser aberta em app nativo
            if (url.startsWith("tel:") ||
                    url.startsWith("whatsapp:") ||
                    url.startsWith("mailto:") ||
                    url.startsWith("sms:") ||
                    url.startsWith("geo:") ||
                    url.endsWith(".apk")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }

            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();

                // Se for nosso domínio, processar internamente na WebView
                if (host != null && host.contains("transjoi.com.br")) {
                    Log.d(TAG, "URL interna detectada, carregando na WebView: " + url);
                    // IMPORTANTE: Retornar false para permitir que a WebView processe a URL internamente
                    return false;
                } else {
                    // URL externa - abrir no navegador
                    Log.d(TAG, "URL externa detectada, abrindo no navegador: " + url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao processar URL: " + e.getMessage());
                return false; // Em caso de erro, deixe a WebView tentar processar
            }
        }
    }

    // Injetar CSS para melhorar UI nativa
    private void injectCSS(WebView webView) {
        try {
            String css = "* { -webkit-tap-highlight-color: transparent; } " +
                    "body { -webkit-touch-callout: none; user-select: none; } " +
                    "input, textarea { -webkit-user-select: auto; } " +
                    "::-webkit-scrollbar { display: none; }";

            webView.evaluateJavascript(
                    "(function() {" +
                            "var style = document.createElement('style');" +
                            "style.type = 'text/css';" +
                            "style.innerHTML = '" + css + "';" +
                            "document.head.appendChild(style);" +
                            "})();", null);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao injetar CSS: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();

        // Ativar JavaScript de volta se estiver pausado
        webView.getSettings().setJavaScriptEnabled(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    public boolean geo_permission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            webView.goBack();
            return false;
        }else{
            return true;
        }
    }

    /*-- creating new image file here --*/
    private File create_image() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "img_" + timeStamp + "_";
        File storageDir;

        try {
            // Criar diretório em uma localização mais segura
            storageDir = new File(getApplicationContext().getCacheDir(), "camera_photos");

            // Make sure the directory exists
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + storageDir);
            }

            // Create the file
            File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);

            // Log para debug
            Log.d(TAG, "Arquivo criado em: " + imageFile.getAbsolutePath());

            // Criar URI usando FileProvider
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",  // MODIFICADO: use .fileprovider em vez de .provider
                    imageFile);

            Log.d(TAG, "URI da câmera: " + cameraImageUri.toString());

            return imageFile;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao criar arquivo de imagem: " + e.getMessage(), e);
            throw e;
        }
    }


    /*-- back/down key handling --*/
    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event){
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
    }

    /*-- handling input[type="file"] requests for android API 21+ --*/
    @SuppressLint("QueryPermissionsNeeded")
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        if (file_permission()) {
            if (file_path != null) {
                file_path.onReceiveValue(null);
                file_path = null;
            }

            file_path = filePathCallback;

            try {
                // MODIFICADO: Implementação mais simples e robusta
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                File photoFile = create_image();

                if (photoFile != null && cameraImageUri != null) {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);

                    // Garantir que a app da câmera possa gravar no nosso arquivo
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    // Verificar se alguma app da câmera pode lidar com isso
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(takePictureIntent, file_req_code);
                        return true;
                    } else {
                        // Tentar iniciar sem verificação de resolveActivity
                        startActivityForResult(takePictureIntent, file_req_code);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir câmera: " + e.getMessage(), e);
                if (file_path != null) {
                    file_path.onReceiveValue(null);
                    file_path = null;
                }
                Toast.makeText(this, "Erro ao abrir a câmera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Salvar estado da webview
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restaurar estado da webview
        webView.restoreState(savedInstanceState);
    }

    // Atualizar o método de verificação de permissões para garantir que a câmera tenha todas as permissões necessárias
    public boolean file_permission() {
        Log.d(TAG, "Verificando permissões de câmera e armazenamento");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "Solicitando permissões para Android 13+");
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.CAMERA}, 1);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "Solicitando permissões para Android < 13");
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 1);
                return false;
            }
        }

        Log.d(TAG, "Todas as permissões estão concedidas");
        return true;
    }

    // Implementar o método onRequestPermissionsResult para lidar com respostas de solicitação de permissão
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            boolean allPermissionsGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Log.d(TAG, "Todas as permissões concedidas");
                // Você pode tentar iniciar a câmera novamente aqui se desejar
            } else {
                Log.d(TAG, "Algumas permissões foram negadas");
                Toast.makeText(this, "Permissões necessárias para o funcionamento adequado do aplicativo", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Adicionar método para instalar APK
    private void installAPK(Uri apkUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    // Atualizar método shouldOverrideUrlLoading para lidar com download de APK
    @SuppressLint("DefaultLocale")
    private boolean shouldOverrideUrlLoading(WebView view, String url) {
        Log.d(TAG, "Tentando carregar URL: " + url);

        // Verificar se é URL externa que deve ser aberta em app nativo
        if (url.startsWith("tel:") ||
                url.startsWith("whatsapp:") ||
                url.startsWith("mailto:") ||
                url.startsWith("sms:") ||
                url.startsWith("geo:") ||
                url.endsWith(".apk")) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }

        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();

            // Se for nosso domínio, processar internamente na WebView
            if (host != null && host.contains("transjoi.com.br")) {
                Log.d(TAG, "URL interna detectada, carregando na WebView: " + url);
                // IMPORTANTE: Retornar false para permitir que a WebView processe a URL internamente
                return false;
            } else {
                // URL externa - abrir no navegador
                Log.d(TAG, "URL externa detectada, abrindo no navegador: " + url);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao processar URL: " + e.getMessage());
            return false; // Em caso de erro, deixe a WebView tentar processar
        }
    }

}