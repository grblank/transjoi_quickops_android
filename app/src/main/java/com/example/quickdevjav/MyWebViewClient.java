package com.example.quickdevjav;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;



public class MyWebViewClient extends WebViewClient {

    private Activity activity = null;

    public void WebViewClientImpl(Activity activity) {
        this.activity = activity;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest url) {
        if(url.getUrl().toString().indexOf("http://192.168.0.6:8000") > -1 ) return false;

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.getUrl().toString()));
        activity.startActivity(intent);
        return true;
    }

}