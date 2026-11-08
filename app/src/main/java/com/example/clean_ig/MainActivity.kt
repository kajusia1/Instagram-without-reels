package com.example.clean_ig

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Instagrom"

        webView = findViewById(R.id.webView)

        // Blokada przewijania w pionie w odtwarzaczach rolek
        var startY = 0f
        var startX = 0f
        var isReelOpen = false

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun setReelActive(active: Boolean) {
                isReelOpen = active
            }
        }, "AndroidBridge")

        webView.setOnTouchListener { _, event ->
            if (isReelOpen) {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                        startX = event.x
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dy = kotlin.math.abs(event.y - startY)
                        val dx = kotlin.math.abs(event.x - startX)
                        if (dy > 10 && dy > dx) {
                            return@setOnTouchListener true
                        }
                    }
                }
            }
            false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && !url.contains("/login") && !url.contains("/accounts/")) {
                    val mode = getSharedPreferences("prefs", MODE_PRIVATE).getInt("app_mode", 0)
                    view?.evaluateJavascript(getScriptForMode(mode), null)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        loadInitialUrl()
    }

    private fun loadInitialUrl() {
        val currentMode = getSharedPreferences("prefs", MODE_PRIVATE).getInt("app_mode", 0)
        when (currentMode) {
            0, 1 -> webView.loadUrl("https://www.instagram.com/")
            2 -> webView.loadUrl("https://www.instagram.com/?variant=following")
        }
    }

    private fun getScriptForMode(mode: Int): String {
        val hideCss = """
            a[href*="/reels/"],
            a[href*="/explore/"],
            svg[aria-label*="Reels" i],
            svg[aria-label*="Rolki" i],
            svg[aria-label*="Eksploruj" i],
            svg[aria-label*="Explore" i],
            div[data-pagelet*="Reel"],
            div[role="progressbar"],
            div[aria-label*="Ładowanie" i],
            div[aria-label*="Loading" i],
            svg[aria-label*="Ładowanie" i],
            svg[aria-label*="Loading" i] {
                display: none !important;
            }
        """.trimIndent().replace("\n", " ")

        val feedLogic = when (mode) {
            0 -> // Dumb feed: hide ads and suggested posts
                """
                if (isHome) {
                    document.querySelectorAll('main article').forEach(article => {
                        let shouldHide = false;
                        const articleText = article.innerText || '';
                        const lowerText = articleText.toLowerCase();

                        if (
                            lowerText.includes('sponsorowane') || 
                            lowerText.includes('sponsored') ||
                            lowerText.includes('promowane')
                        ) {
                            shouldHide = true;
                        }

                        if (article.querySelector('[aria-label*="Sponsored" i], [aria-label*="Sponsorowane" i], [aria-label*="Promowane" i]')) {
                            shouldHide = true;
                        }

                        article.querySelectorAll('button, div[role="button"]').forEach(btn => {
                            const text = (btn.textContent || '').trim();
                            if (text === 'Follow' || text === 'Obserwuj') {
                                shouldHide = true;
                            }
                        });

                        if (shouldHide) {
                            article.style.setProperty('display', 'none', 'important');
                        } else {
                            article.style.removeProperty('display');
                        }
                    });
                }
                """.trimIndent()
            1 -> // No feed: hide all main article on home
                """
                if (isHome) {
                    document.querySelectorAll('main article').forEach(article => {
                        article.style.setProperty('display', 'none', 'important');
                    });
                }
                """.trimIndent()
            2 -> // Followed only: prevent leaving variant=following
                """
                if ((path === '/' || path === '' || path === '/feed/') && !currentFullUrl.includes('variant=following')) {
                    window.location.replace('https://www.instagram.com/?variant=following');
                    return;
                }
                """.trimIndent()
            else -> ""
        }

        return """
            (function() {
                if (window.__cleanScriptInjected) return;
                window.__cleanScriptInjected = true;

                const style = document.createElement('style');
                style.textContent = '$hideCss';
                (document.head || document.documentElement).appendChild(style);

                const clean = () => {
                    const currentFullUrl = window.location.href;
                    const path = window.location.pathname;
                    const isHome = path === '/' || path === '' || path === '/feed/';

                    $feedLogic

                    // Ukryj przyciski dolnego paska i rolki
                    document.querySelectorAll('a[href^="/reels/"], a[href^="/explore/"], div[data-pagelet*="Reel"]').forEach(el => {
                        el.style.setProperty('display', 'none', 'important');
                    });

                    // Precyzyjne wykrywanie aktywnej rolki:
                    // 1. URL zawiera /reel lub /videos
                    // 2. Element wideo znajduje się w modalu / dialogu / poza głównym feedem (nie w main article)
                    const activeVideo = document.querySelector('video');
                    const isFeedVideo = activeVideo ? activeVideo.closest('main article') !== null : false;
                    const isReel = path.includes('/reel') || path.includes('/videos') || 
                                   !!document.querySelector('div[role="dialog"] video, div[style*="scroll-snap"] video') || 
                                   (activeVideo !== null && !isFeedVideo);

                    if (window.AndroidBridge && window.AndroidBridge.setReelActive) {
                        window.AndroidBridge.setReelActive(isReel);
                    }

                    if (isReel) {
                        document.querySelectorAll('div[style*="overflow-y"], div[style*="scroll-snap"], div[data-pagelet*="NoFeed"], div[data-pagelet*="Reel"], video').forEach(el => {
                            let container = el.closest('div[style*="overflow"], div[style*="height"]') || el;
                            container.style.setProperty('overflow', 'hidden', 'important');
                            container.style.setProperty('scroll-snap-type', 'none', 'important');
                        });
                    }
                };

                clean();
                new MutationObserver(clean).observe(document.documentElement, {
                    childList: true,
                    subtree: true
                });
            })();
        """.trimIndent()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Settings")?.setIcon(android.R.drawable.ic_menu_preferences)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            showSettingsDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSettingsDialog() {
        val modes = arrayOf("Dumb feed", "No feed", "Followed only")
        val currentMode = getSharedPreferences("prefs", MODE_PRIVATE).getInt("app_mode", 0)

        AlertDialog.Builder(this)
            .setTitle("Wybierz tryb aplikacji")
            .setSingleChoiceItems(modes, currentMode) { dialog, which ->
                getSharedPreferences("prefs", MODE_PRIVATE).edit().putInt("app_mode", which).apply()
                dialog.dismiss()
                loadInitialUrl()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
}
