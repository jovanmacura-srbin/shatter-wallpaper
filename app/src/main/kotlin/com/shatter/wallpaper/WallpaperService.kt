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
        private var width = 1080
        private var height = 1920

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            handler.post { setupWebView() }
        }

        private fun setupWebView() {
            val wv = WebView(applicationContext)
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    isDrawing = true
                    handler.post { drawLoop() }
                }
            }
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.allowFileAccess = true
            wv.settings.allowContentAccess = true
            wv.settings.useWideViewPort = true
            wv.settings.loadWithOverviewMode = true
            wv.layout(0, 0, width, height)
            wv.loadUrl("file:///android_asset/home.html")
            webView = wv
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            handler.post { webView?.layout(0, 0, w, h) }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                if (!isDrawing) {
                    isDrawing = true
                    handler.post { drawLoop() }
                }
            } else {
                isDrawing = false
                handler.removeCallbacksAndMessages(null)
            }
        }

        private fun drawLoop() {
            if (!isDrawing) return
            try {
                val canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    try {
                        webView?.draw(canvas)
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                }
            } catch (e: Exception) { }
            handler.postDelayed({ drawLoop() }, 33)
        }

        override fun onDestroy() {
            super.onDestroy()
            isDrawing = false
            handler.removeCallbacksAndMessages(null)
            handler.post { webView?.destroy() }
        }
    }
}
