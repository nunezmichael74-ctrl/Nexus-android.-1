package com.nexusknowledge.app.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Pestaña Comunidad: la red social de preguntas y respuestas de la app anterior (assets/index.html),
 * sin conexión. Votos, reputación, historia y proyectos se guardan en el localStorage del WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createCommunityWebView(context: Context): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true      // localStorage: los datos persisten entre aperturas
    settings.allowFileAccess = true        // solo para leer los assets propios
    settings.allowContentAccess = false
    settings.textZoom = 100
    webChromeClient = WebChromeClient()    // confirm() de «Restablecer datos»
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "http" || uri.scheme == "https") {
                // Enlaces externos (fuentes) al navegador del móvil.
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) {}
                return true
            }
            return false
        }
    }
    loadUrl("file:///android_asset/index.html")
}

@Composable
fun CommunityScreen(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
    )
}
