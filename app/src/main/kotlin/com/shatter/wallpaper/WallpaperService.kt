package com.shatter.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings

class ShatterWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ShatterEngine()

    inner class ShatterEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var webView: WebView? = null
        private var windowManager: WindowManager? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            handler.post { createWebView() }
        }

        private fun createWebView() {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            webView = WebView(applicationContext).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                setBackgroundColor(0x00000000)
                loadUrl("file:///android_asset/home.html")
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            handler.post {
                webView?.layout(0, 0, w, h)
                drawFrame(w, h)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) handler.post { 
                val s = surfaceHolder
                drawFrame(s.surfaceFrame.width(), s.surfaceFrame.height())
            }
            else handler.removeCallbacksAndMessages(null)
        }

        private fun drawFrame(w: Int, h: Int) {
            try {
                val canvas = surfaceHolder.lockHardwareCanvas() 
                    ?: surfaceHolder.lockCanvas() 
                    ?: return
                try {
                    webView?.layout(0, 0, w, h)
                    webView?.draw(canvas)
                } finally {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) { }
            handler.postDelayed({ drawFrame(w, h) }, 33)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            handler.post { webView?.destroy() }
        }
    }
}
