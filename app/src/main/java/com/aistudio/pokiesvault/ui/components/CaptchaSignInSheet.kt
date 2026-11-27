package com.aistudio.pokiesvault.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private const val CAPTCHA_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

private const val SESSION_HOOK_JS = """
(function() {
  if (window.__pvHookInstalled) return;
  window.__pvHookInstalled = true;
  window.__pvCaptured = null;
  function tryExtract(txt) {
    try {
      var obj = typeof txt === 'string' ? JSON.parse(txt) : txt;
      if (obj && obj.status === 'SUCCESS' && obj.data && obj.data.id && obj.data.token) {
        window.__pvCaptured = {
          id: String(obj.data.id),
          token: String(obj.data.token),
          host: String(window.location.hostname || ''),
          merchantId: String(window.MERCHANTID || '')
        };
      }
    } catch (e) {}
  }
  try {
    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(m, u) {
      this.__pvUrl = u || '';
      return origOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
      this.addEventListener('load', function() {
        try {
          if (this.responseText && this.responseText.indexOf('"token"') !== -1) {
            tryExtract(this.responseText);
          }
        } catch (e) {}
      });
      return origSend.apply(this, arguments);
    };
  } catch (e) {}
  try {
    if (window.fetch) {
      var origFetch = window.fetch;
      window.fetch = function() {
        return origFetch.apply(this, arguments).then(function(res) {
          try {
            var clone = res.clone();
            clone.text().then(function(t) {
              if (t && t.indexOf('"token"') !== -1) tryExtract(t);
            }).catch(function(){});
          } catch (e) {}
          return res;
        });
      };
    }
  } catch (e) {}
})();
"""

private const val SESSION_POLL_JS = """
(function() {
  try {
    if (window.__pvCaptured && window.__pvCaptured.id && window.__pvCaptured.token) {
      return JSON.stringify(window.__pvCaptured);
    }
    var keys = ['USER', 'user', 'auth', 'session'];
    for (var i = 0; i < localStorage.length; i++) {
      var k = localStorage.key(i);
      if (k && keys.indexOf(k) === -1) keys.push(k);
    }
    for (var j = 0; j < keys.length; j++) {
      var raw = localStorage.getItem(keys[j]);
      if (!raw || raw.charAt(0) !== '{') continue;
      try {
        var parsed = JSON.parse(raw);
        var id = parsed.id || parsed.accessId || (parsed.data && parsed.data.id);
        var token = parsed.token || parsed.accessToken || (parsed.data && parsed.data.token);
        if (id && token && String(token).length >= 8) {
          return JSON.stringify({
            id: String(id),
            token: String(token),
            host: String(window.location.hostname || ''),
            merchantId: String(window.MERCHANTID || '')
          });
        }
      } catch (e) {}
    }
  } catch (e) {}
  return '';
})();
"""

/**
 * Full-screen in-app WebView dialog that lets the user solve a casino's AliyunCaptcha once.
 * Automatically detects the authenticated (accessId, accessToken) from localStorage or
 * /users/login responses and saves it to Pokies Vault's Keystore-encrypted session store.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaSignInDialog(
    domain: String,
    savedPhone: String,
    savedPassword: String,
    onDismiss: () -> Unit,
    onSessionCaptured: (accessId: String, accessToken: String, host: String, merchantId: String) -> Unit
) {
    val palette = LocalVaultPalette.current
    var loadProgress by remember { mutableIntStateOf(0) }
    var statusNote by remember {
        mutableStateOf("Sign in once below and complete the captcha. Your encrypted session token will be captured automatically.")
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val capturedOnce = remember(domain) { AtomicBoolean(false) }

    fun handleRawCaptureResult(rawJsReturn: String?) {
        if (capturedOnce.get()) return
        if (rawJsReturn.isNullOrBlank() || rawJsReturn == "null" || rawJsReturn == "\"\"") return
        try {
            // evaluateJavascript returns a JSON-encoded string literal, so unwrap outer quotes if present
            val unwrapped = if (rawJsReturn.startsWith("\"") && rawJsReturn.endsWith("\"")) {
                JSONObject("{\"v\":$rawJsReturn}").optString("v", "")
            } else {
                rawJsReturn
            }
            if (unwrapped.isBlank() || !unwrapped.startsWith("{")) return
            val obj = JSONObject(unwrapped)
            val id = obj.optString("id", "").trim()
            val token = obj.optString("token", "").trim()
            val host = obj.optString("host", "").trim()
            val merchantId = obj.optString("merchantId", "").trim()
            if (id.isNotEmpty() && token.length >= 8 && capturedOnce.compareAndSet(false, true)) {
                statusNote = "Session captured! Syncing your balances and bonuses…"
                onSessionCaptured(id, token, host, merchantId)
            }
        } catch (_: Exception) {
            // Ignore transient JSON parse glitches while page is loading
        }
    }

    // Poll localStorage & XHR/fetch capture hook every 750ms while dialog is open
    LaunchedEffect(domain, webViewRef) {
        while (!capturedOnce.get()) {
            delay(750L)
            val wv = webViewRef ?: continue
            wv.evaluateJavascript(SESSION_POLL_JS) { result ->
                handleRawCaptureResult(result)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("captcha_signin_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = palette.accentC,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "One-Time Captcha Sign-In",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = domain,
                                        color = palette.accentA,
                                        style = MonoTextStyles.metricSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (savedPhone.isNotBlank() && savedPassword.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val jsPhone = JSONObject.quote(savedPhone.trim())
                                        val jsPass = JSONObject.quote(savedPassword)
                                        val fillScript = """
                                            (function() {
                                              var phoneVal = $jsPhone;
                                              var passVal = $jsPass;
                                              function setNativeValue(el, val) {
                                                if (!el) return;
                                                var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                                                if (setter) setter.call(el, val); else el.value = val;
                                                el.dispatchEvent(new Event('input', { bubbles: true }));
                                                el.dispatchEvent(new Event('change', { bubbles: true }));
                                              }
                                              var inputs = Array.prototype.slice.call(document.querySelectorAll('input'));
                                              var passEl = inputs.find(function(i) { return i.type === 'password'; });
                                              var phoneEl = inputs.find(function(i) {
                                                var n = ((i.name || '') + ' ' + (i.placeholder || '') + ' ' + (i.id || '')).toLowerCase();
                                                return i.type === 'tel' || n.indexOf('mobile') !== -1 || n.indexOf('phone') !== -1 || (i.type === 'text' && i !== passEl);
                                              });
                                              setNativeValue(phoneEl, phoneVal);
                                              setNativeValue(passEl, passVal);
                                            })();
                                        """.trimIndent()
                                        webViewRef?.evaluateJavascript(fillScript, null)
                                        statusNote = "Filled saved login — complete the captcha and tap Login."
                                    },
                                    border = BorderStroke(1.dp, palette.accentA),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accentA),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("fill_saved_login_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fill Login", fontWeight = FontWeight.Bold)
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("close_captcha_signin_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Captcha Sign-In",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = statusNote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                        )

                        if (loadProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { loadProgress / 100f },
                                color = palette.accentA,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                            )
                        }
                    }
                }

                // Embedded Casino WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("captcha_signin_webview"),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.userAgentString = CAPTCHA_USER_AGENT

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        view?.evaluateJavascript(SESSION_HOOK_JS, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(SESSION_HOOK_JS, null)
                                        view?.evaluateJavascript(SESSION_POLL_JS) { res ->
                                            handleRawCaptureResult(res)
                                        }
                                    }
                                }
                                webViewRef = this
                                loadUrl("https://$domain/#login")
                            }
                        }
                    )
                }
            }
        }
    }
}
