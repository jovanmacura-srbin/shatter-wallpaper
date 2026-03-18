package com.shatter.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper

class ShatterWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ShatterEngine()

    inner class ShatterEngine : Engine() {

        private var webView: WebView? = null
        private val handler = Handler(Looper.getMainLooper())
        private var isDrawing = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            handler.post { setupWebView() }
        }

        private fun setupWebView() {
            webView = WebView(applicationContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!isDrawing) {
                            isDrawing = true
                            handler.post { drawLoop() }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    displayZoomControls = false
                    builtInZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                }
                loadUrl("file:///android_asset/home.html")
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int, height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            handler.post {
                webView?.layout(0, 0, width, height)
            }
        }

        override fun onVisibilityChanged(v
