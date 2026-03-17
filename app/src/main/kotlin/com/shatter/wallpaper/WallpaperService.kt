package com.shatter.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

class ShatterWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ShatterEngine()

    inner class ShatterEngine : Engine() {

        private var webView: WebView? = null
        private val handler = Handler(Looper.getMainLooper())

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            handler.post {
                webView = WebView(applicationContext).apply {
                    webViewClient = WebViewClient()
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        displayZoomControls = false
                        builtInZoomControls = false
                    }
                    loadUrl("file:///android_asset/home.html")
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            handler.post {
                webView?.layout(0, 0, width, height)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                handler.post { drawFrame() }
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    webView?.draw(canvas)
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
            handler.postDelayed({ drawFrame() }, 33) // ~30fps
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            handler.post { webView?.destroy() }
        }
    }
}
