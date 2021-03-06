package knf.kuma.custom

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import knf.kuma.App
import knf.kuma.Diagnostic
import knf.kuma.R
import knf.kuma.commons.*
import knf.kuma.directory.DirectoryService
import knf.kuma.retrofit.Repository
import knf.kuma.uagen.UAGenerator
import knf.kuma.uagen.randomUA
import knf.kuma.videoservers.FileActions
import knf.kuma.videoservers.ServersFactory
import knf.tools.bypass.startBypass
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.findOptional
import xdroid.toaster.Toaster


open class GenericActivity : AppCompatActivity() {

    private var tryCount = 0

    override fun onResume() {
        noCrash {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val appIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
                setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name), appIcon, ContextCompat.getColor(this, R.color.colorPrimary)))
            }
        }
        logText("On Resume check")
        noCrash { super.onResume() }
    }

    open fun getSnackbarAnchor(): View? = null

    open fun onBypassUpdated() {

    }

    open fun forceCreation(): Boolean = false

    open fun logText(text: String) {
        Log.e("Bypass", text)
    }

    fun checkBypass() {
        if (BypassUtil.isChecking) {
            logText("Already checking")
            return
        }
        BypassUtil.isChecking = true
        doAsync(exceptionHandler = {
            it.also {
                FirebaseCrashlytics.getInstance().recordException(it)
                logText("Error: ${it.message}")
            }.message?.toastLong()
        }) {
            var flag: Int
            if ((BypassUtil.isNeededFlag().also { flag = it } >= 1 || forceCreation()).also { logText("Is needed or forced: $it") }
                    && !BypassUtil.isLoading.also { logText("Is already loading: $it") }) {
                BypassUtil.isChecking = false
                logText("Flag: $flag")
                if (PrefsUtil.useNewBypass) {
                    bypassLive.postValue(Pair(true, true))
                    BypassUtil.isLoading = true
                    startBypass(4157, BypassUtil.testLink)
                } else {
                    if (flag == 1 || forceCreation()) {
                        logText("Starting creation")
                        BypassUtil.isLoading = true
                        val snack = getSnackbarAnchor()?.showSnackbar("Creando bypass...", Snackbar.LENGTH_INDEFINITE, "Manual") {
                            BypassUtil.isChecking = false
                            BypassUtil.isLoading = false
                            startActivity(Intent(this@GenericActivity, Diagnostic.FullBypass::class.java))
                        }
                        bypassLive.postValue(Pair(true, true))
                        Log.e("CloudflareBypass", "is needed")
                        runWebView(snack)
                    } else {
                        logText("Blocked, code 403")
                    }
                }
            } else {
                BypassUtil.isChecking = false
                logText("Creation not needed, aborting")
                bypassLive.postValue(Pair(false, false))
                Log.e("CloudflareBypass", "Not needed")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun runWebView(snack: Snackbar?) {
        doOnUI(onLog = { logText("Error: $it") }) {
            logText("Searching webview")
            val webView = noCrashLet {
                findOptional<WrapWebView>(R.id.webview).also { if (it != null) logText("Use layout webview") }
                //?: AppWebView(App.context).also { logText("Use Application webview, not visible mode") }
            }
            printWebviewVersion(snack)
            if (webView != null) {
                logText("Clearing cookies")
                BypassUtil.clearCookies(webView)
                logText("Applying settings for webview")
                webView.settings.javaScriptEnabled = true
                /*webView.settings.domStorageEnabled = true
                webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webView.settings.setAppCacheEnabled(false)*/
                webView.webViewClient = object : WebViewClient() {

                    /*override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        shouldOverrideUrlLoading(view, url)
                    }*/

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        shouldOverrideUrlLoading(view, request?.url?.toString())
                        return false
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        logText("Override: $url")
                        logText("Waiting for resolve...")
                        //view?.loadUrl(url)
                        logText("Cookies for animeflv:")
                        logText(noCrashLet { CookieManager.getInstance().getCookie("https://animeflv.net") }
                                ?: "Error!")
                        if (BypassUtil.isLoading && BypassUtil.saveCookies(App.context)) {
                            logText("Cookies saved")
                            //webView.loadUrl("about:blank")
                            doAsync(exceptionHandler = { logText("Error: ${it.message}") }) {
                                logText("Checking connection state")
                                if (BypassUtil.isConnectionBlocked()) {
                                    if (tryCount < 3) {
                                        tryCount++
                                        logText("Connection blocked, retry connection... tries left: ${3 - tryCount}")
                                        BypassUtil.isVerifing = false
                                        runWebView(snack)
                                    } else {
                                        tryCount = 0
                                        logText("Connection was blocked, no tries left")
                                        BypassUtil.isLoading = false
                                        BypassUtil.isVerifing = false
                                        onBypassUpdated()
                                    }
                                } else if (BypassUtil.isNeededFlag() == 0)
                                    doOnUI(onLog = { logText("Error: $it") }) {
                                        webView.loadUrl("about:blank")
                                        tryCount = 0
                                        logText("Connection was successful")
                                        snack?.safeDismiss()
                                        getSnackbarAnchor()?.showSnackbar("Bypass actualizado")
                                        bypassLive.postValue(Pair(first = true, second = false))
                                        Repository().reloadRecents()
                                        onBypassUpdated()
                                        BypassUtil.isLoading = false
                                        PicassoSingle.clear()
                                        if (!PrefsUtil.isDirectoryFinished)
                                            DirectoryService.run(this@GenericActivity)
                                    }
                            }
                        } else if (BypassUtil.isLoading) {
                            logText("cf_clearance not found or empty")
                            url?.let {
                                view?.loadUrl(url)
                            }
                        }
                        return false
                    }
                }
                webView.settings.userAgentString = (if (PrefsUtil.useDefaultUserAgent) {
                    webView.settings.userAgentString
                } else UAGenerator.getRandomUserAgent()).also { PrefsUtil.userAgent = it }
                logText("Open animeflv.net")
                webView.loadUrl(BypassUtil.testLink)
            } else {
                logText("Error finding suitable webview")
                snack?.safeDismiss()
                getSnackbarAnchor()?.showSnackbar("Error al iniciar WebView")
                onBypassUpdated()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4157 && resultCode == RESULT_OK && data != null) {
            if (BypassUtil.saveCookies(this, data.getStringExtra("cookies") ?: "null")) {
                PrefsUtil.useDefaultUserAgent = false
                PrefsUtil.userAgent = data.getStringExtra("user_agent") ?: randomUA()
                bypassLive.postValue(Pair(first = true, second = false))
                Repository().reloadRecents()
                onBypassUpdated()
                BypassUtil.isLoading = false
                PicassoSingle.clear()
                if (!PrefsUtil.isDirectoryFinished)
                    DirectoryService.run(this@GenericActivity)
            }
        } else if (resultCode == RESULT_CANCELED) {
            bypassLive.postValue(Pair(first = false, second = false))
            BypassUtil.isLoading = false
            onBypassUpdated()
        }
    }

    private fun isChromeDetected(): Boolean {
        return try {
            try {
                App.context.packageManager.getPackageInfo("com.android.chrome", 0)
            } catch (e: Exception) {
                App.context.packageManager.getPackageInfo("com.chrome.beta", 0)
            }
            logText("Chrome detected")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun printWebviewVersion(snack: Snackbar?) {
        try {
            val info = App.context.packageManager.getPackageInfo("com.google.android.webview", 0)
            val name = info.versionName
            val version = PackageInfoCompat.getLongVersionCode(info)
            logText("Installed webview: $name($version)")
            if ("70.0.0.0" isVersionGreater name)
                if (!isChromeDetected())
                    if (forceCreation()) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview")))
                        Toaster.toastLong("Se necesita tener actualizado el webview para crear bypass")
                    } else {
                        snack?.dismiss()
                        getSnackbarAnchor()?.showSnackbar("Error en bypass, Webview desactualizado", Snackbar.LENGTH_INDEFINITE, "Actualizar") {
                            BypassUtil.isChecking = false
                            BypassUtil.isLoading = false
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview")))
                        }
                    }
        } catch (e: PackageManager.NameNotFoundException) {
            logText("System Webview not found!")
            if (forceCreation()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview")))
                Toaster.toastLong("Se necesita tener el webview instalado!")
            } else {
                snack?.dismiss()
                getSnackbarAnchor()?.showSnackbar("Error en bypass, Webview no encontrado", Snackbar.LENGTH_INDEFINITE, "Actualizar") {
                    BypassUtil.isChecking = false
                    BypassUtil.isLoading = false
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview")))
                }
            }
        }
    }

    private infix fun String.isVersionGreater(compare: String): Boolean {
        val array1 = split(".")
        val array2 = compare.split(".")
        return noCrashLet(false) {
            for (i in array1.indices)
                if (array1[i].toInt() > array2[i].toInt())
                    return@noCrashLet true
            false
        }
    }

    override fun onPause() {
        super.onPause()
        BypassUtil.isLoading = false
    }

    override fun onDestroy() {
        super.onDestroy()
        ServersFactory.clear()
        FileActions.reset()
        if (forceCreation())
            bypassLive.postValue(Pair(false, false))
    }

    companion object {
        val bypassLive: MutableLiveData<Pair<Boolean, Boolean>> = MutableLiveData()
    }
}