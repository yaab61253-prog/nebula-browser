package com.nebulaspectrastudio.browser.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nebulaspectrastudio.browser.ui.theme.*
import java.util.Locale

enum class Screen { BROWSER, TABS, SETTINGS, BOOKMARKS, HISTORY }

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BrowserApp(vm: BrowserViewModel = viewModel(), startUrl: String? = null) {
    val tabs by vm.tabs.collectAsState()
    val currentTabId by vm.currentTabId.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val history by vm.history.collectAsState()
    val settings by vm.settings.collectAsState()

    var screen by remember { mutableStateOf(Screen.BROWSER) }
    var urlText by remember { mutableStateOf(startUrl ?: "https://www.google.com") }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableStateOf(0f) }
    var showMenu by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current
    val currentTab = tabs.find { it.id == currentTabId }



    LaunchedEffect(startUrl) {
        if (!startUrl.isNullOrEmpty()) {
            webViewRef.value?.loadUrl(startUrl)
            urlText = startUrl
        }
    }

    fun navigate(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(input)}"
        }
        webViewRef.value?.loadUrl(url)
        urlText = url
        vm.updateTab(currentTabId, url = url)
    }

    BackHandler(enabled = screen != Screen.BROWSER) { screen = Screen.BROWSER }
    BackHandler(enabled = screen == Screen.BROWSER) {
        if (webViewRef.value?.canGoBack() == true) webViewRef.value?.goBack()
    }

    val bgColor = if (settings.darkMode) BgDark else Color(0xFFF5F5F5)
    val surfaceColor = if (settings.darkMode) Surface else Color.White
    val textColor = if (settings.darkMode) TextPrimary else Color(0xFF111827)
    val hintColor = if (settings.darkMode) TextHint else Color(0xFF9CA3AF)

    Box(Modifier.fillMaxSize().background(bgColor)) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val duration = 300
                if (targetState == Screen.BROWSER) {
                    (slideInHorizontally(tween(duration)) { -it } + fadeIn(tween(duration)))
                        .togetherWith(slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration)))
                } else {
                    (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)))
                        .togetherWith(slideOutHorizontally(tween(duration)) { -it } + fadeOut(tween(duration)))
                }
            }, label = "screen"
        ) { currentScreen ->
        when (currentScreen) {
            Screen.BROWSER -> {
                Column(Modifier.fillMaxSize()) {
                    TopBar(urlText, isLoading, loadProgress,
                        currentTab?.isIncognito == true, settings.darkMode,
                        surfaceColor, textColor, hintColor) { navigate(it) }

                    Box(Modifier.weight(1f)) {
                        AndroidView(factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef.value = this
                                this.settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    setSupportMultipleWindows(true)
                                    allowFileAccess = true
                                    mediaPlaybackRequiresUserGesture = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    databaseEnabled = true
                                    userAgentString = if (settings.desktopMode)
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                                    else
                                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.6099.144 Mobile Safari/537.36"
                                }
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                        isLoading = true; urlText = url
                                        vm.updateTab(currentTabId, url = url)
                                    }
                                    override fun onPageFinished(view: WebView, url: String) {
                                        isLoading = false; urlText = url
                                        vm.updateTab(currentTabId, url = url, title = view.title ?: url,
                                            canBack = view.canGoBack(), canForward = view.canGoForward())
                                        vm.addToHistory(url)
                                    }
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val url = request.url.toString()
                                        if (!url.startsWith("http")) {
                                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                                            return true
                                        }
                                        return false
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                        if (newProgress == 100) isLoading = false
                                    }
                                }
                                setDownloadListener { url, ua, cd, mime, _ ->
                                    val req = DownloadManager.Request(Uri.parse(url))
                                    req.setMimeType(mime)
                                    req.addRequestHeader("User-Agent", ua)
                                    req.setTitle(URLUtil.guessFileName(url, cd, mime))
                                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime))
                                    (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                                }
                                loadUrl(startUrl ?: "https://www.google.com")
                            }
                        }, modifier = Modifier.fillMaxSize())
                    }

                    BottomBar(currentTab?.canGoBack == true, currentTab?.canGoForward == true,
                        tabs.size, currentTab?.isIncognito == true, surfaceColor,
                        { webViewRef.value?.goBack() }, { webViewRef.value?.goForward() },
                        { navigate("https://www.google.com") }, { screen = Screen.TABS }, { showMenu = true })
                }

                if (showMenu) {
                    BrowserMenu(
                        isDesktop = settings.desktopMode, darkMode = settings.darkMode,
                        onDismiss = { showMenu = false },
                        onNewTab = { vm.newTab(false); showMenu = false },
                        onIncognito = { vm.newTab(true); showMenu = false },
                        onBookmarks = { screen = Screen.BOOKMARKS; showMenu = false },
                        onHistory = { screen = Screen.HISTORY; showMenu = false },
                        onDownloads = { showMenu = false; context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) },
                        onAddBookmark = { vm.addBookmark(currentTab?.title ?: urlText, urlText); showMenu = false },
                        onShare = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, urlText) }, "Share"))
                            showMenu = false
                        },
                        onDesktop = { vm.updateSettings(settings.copy(desktopMode = !settings.desktopMode)); webViewRef.value?.reload(); showMenu = false },
                        onSettings = { screen = Screen.SETTINGS; showMenu = false },
                        onReload = { webViewRef.value?.reload(); showMenu = false }
                    )
                }
            }
            Screen.TABS -> TabsScreen(tabs, currentTabId, settings.darkMode, bgColor, surfaceColor, textColor,
                { vm.switchTab(it); screen = Screen.BROWSER }, { vm.closeTab(it) },
                { vm.newTab(false); screen = Screen.BROWSER }, { vm.newTab(true); screen = Screen.BROWSER }, { screen = Screen.BROWSER })
            Screen.SETTINGS -> SettingsScreen(settings, bgColor, surfaceColor, textColor, { screen = Screen.BROWSER }) { vm.updateSettings(it); screen = Screen.BROWSER }
            Screen.BOOKMARKS -> BookmarksScreen(bookmarks, settings.darkMode, bgColor, surfaceColor, textColor,
                { navigate(it); screen = Screen.BROWSER }, { vm.removeBookmark(it) }, { screen = Screen.BROWSER })
            Screen.HISTORY -> HistoryScreen(history, settings.darkMode, bgColor, surfaceColor, textColor,
                { navigate(it); screen = Screen.BROWSER }, { vm.clearHistory() }, { screen = Screen.BROWSER })
        }
        } // end AnimatedContent
    }
}

@Composable
fun TopBar(urlText: String, isLoading: Boolean, loadProgress: Float, isIncognito: Boolean,
    darkMode: Boolean, surfaceColor: Color, textColor: Color, hintColor: Color, onUrlSubmit: (String) -> Unit) {
    var text by remember(urlText) { mutableStateOf(urlText) }
    val isSecure = urlText.startsWith("https://")
    Column(Modifier.fillMaxWidth().background(surfaceColor).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp),
                color = if (darkMode) Surface2 else Color(0xFFF0F0F0),
                border = BorderStroke(1.dp, if (darkMode) Border else Color(0xFFD1D5DB))) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isIncognito || !isSecure) (if (isIncognito) Icons.Filled.Lock else Icons.Outlined.Warning) else Icons.Filled.Lock,
                        null, tint = if (isIncognito) Color(0xFF9CA3AF) else if (isSecure) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onUrlSubmit(text) }),
                        decorationBox = { inner -> if (text.isEmpty()) Text("Search or type URL", color = hintColor, fontSize = 13.sp); inner() },
                        modifier = Modifier.weight(1f))
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Purple, strokeWidth = 2.dp)
                }
            }
        }
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(progress = { loadProgress }, modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Purple, trackColor = if (darkMode) Surface2 else Color(0xFFE5E7EB))
        }
    }
}

@Composable
fun BottomBar(canGoBack: Boolean, canGoForward: Boolean, tabCount: Int, isIncognito: Boolean,
    surfaceColor: Color, onBack: () -> Unit, onForward: () -> Unit, onHome: () -> Unit, onTabs: () -> Unit, onMenu: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), color = surfaceColor, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.Default.ArrowBack, null, tint = if (canGoBack) Purple else Purple.copy(alpha = 0.3f))
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.Default.ArrowForward, null, tint = if (canGoForward) Purple else Purple.copy(alpha = 0.3f))
            }
            IconButton(onClick = onHome) { Icon(Icons.Filled.Home, null, tint = Purple) }
            Box(contentAlignment = Alignment.Center) {
                IconButton(onClick = onTabs) {
                    Icon(Icons.Outlined.Tab, null, tint = if (isIncognito) Color(0xFF6B7280) else Purple)
                }
                val scale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "badge"
                )
                Surface(modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(17.dp).scale(scale),
                    shape = RoundedCornerShape(5.dp), color = Purple) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(tabCount.toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, null, tint = Purple) }
        }
    }
}

@Composable
fun BrowserMenu(isDesktop: Boolean, darkMode: Boolean, onDismiss: () -> Unit, onNewTab: () -> Unit,
    onIncognito: () -> Unit, onBookmarks: () -> Unit, onHistory: () -> Unit, onDownloads: () -> Unit,
    onAddBookmark: () -> Unit, onShare: () -> Unit, onDesktop: () -> Unit, onSettings: () -> Unit, onReload: () -> Unit) {
    val menuBg = if (darkMode) Color(0xFF1E1B2E) else Color.White
    val textColor = if (darkMode) TextPrimary else Color(0xFF111827)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable { onDismiss() }) {
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = menuBg, shadowElevation = 16.dp) {
            Column(Modifier.padding(vertical = 8.dp).navigationBarsPadding()) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 12.dp)
                    .size(width = 40.dp, height = 4.dp).background(Color(0xFF6B7280), RoundedCornerShape(2.dp)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QA(Icons.Filled.Add, "New Tab", darkMode, onNewTab)
                    QA(Icons.Filled.Lock, "Incognito", darkMode, onIncognito)
                    QA(Icons.Filled.Star, "Bookmark", darkMode, onAddBookmark)
                    QA(Icons.Outlined.Share, "Share", darkMode, onShare)
                }
                Divider(color = if (darkMode) Border else Color(0xFFE5E7EB), modifier = Modifier.padding(vertical = 8.dp))
                MI(Icons.Outlined.BookmarkBorder, "Bookmarks", textColor, onBookmarks)
                MI(Icons.Outlined.History, "History", textColor, onHistory)
                MI(Icons.Outlined.Download, "Downloads", textColor, onDownloads)
                MI(Icons.Filled.Refresh, "Reload Page", textColor, onReload)
                MI(if (isDesktop) Icons.Outlined.PhoneAndroid else Icons.Outlined.DesktopWindows,
                    if (isDesktop) "Mobile Site" else "Desktop Site", textColor, onDesktop)
                MI(Icons.Filled.Settings, "Settings", textColor, onSettings)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun QA(icon: ImageVector, label: String, darkMode: Boolean, onClick: () -> Unit) {
    val textColor = if (darkMode) TextPrimary else Color(0xFF111827)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Surface(shape = CircleShape, color = if (darkMode) Surface2 else Color(0xFFF3F4F6)) {
            Icon(icon, null, tint = Purple, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = textColor)
    }
}

@Composable
fun MI(icon: ImageVector, text: String, textColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 15.sp)
    }
}

@Composable
fun TabsScreen(tabs: List<TabState>, currentTabId: Int, darkMode: Boolean, bgColor: Color, surfaceColor: Color, textColor: Color,
    onSelectTab: (Int) -> Unit, onCloseTab: (Int) -> Unit, onNewTab: () -> Unit, onNewIncognito: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Purple) }
            Text("Tabs (${tabs.size})", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNewIncognito) { Icon(Icons.Filled.Lock, null, tint = Color(0xFF6B7280)) }
            IconButton(onClick = onNewTab) { Icon(Icons.Filled.Add, null, tint = Purple) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs) { tab ->
                Surface(modifier = Modifier.fillMaxWidth().clickable { onSelectTab(tab.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (tab.id == currentTabId) (if (darkMode) Surface2 else Color(0xFFEDE9FE)) else surfaceColor,
                    border = if (tab.id == currentTabId) BorderStroke(1.dp, Purple) else BorderStroke(1.dp, if (darkMode) Border else Color(0xFFE5E7EB))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (tab.isIncognito) Icons.Filled.Lock else Icons.Outlined.Tab, null,
                            tint = if (tab.isIncognito) Color(0xFF6B7280) else Purple, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tab.title, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tab.url, color = if (darkMode) TextHint else Color(0xFF9CA3AF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onCloseTab(tab.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, null, tint = if (darkMode) TextHint else Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(settings: BrowserSettings, bgColor: Color, surfaceColor: Color, textColor: Color, onBack: () -> Unit, onSave: (BrowserSettings) -> Unit) {
    var cur by remember { mutableStateOf(settings) }
    val hintColor = if (cur.darkMode) TextHint else Color(0xFF9CA3AF)
    Column(Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSave(cur) }) { Icon(Icons.Default.ArrowBack, null, tint = Purple) }
            Text("Settings", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                Text("General", color = hintColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                TS("Desktop Mode", Icons.Outlined.DesktopWindows, cur.desktopMode, textColor, surfaceColor) { cur = cur.copy(desktopMode = it) }
                TS("Save History", Icons.Outlined.History, cur.saveHistory, textColor, surfaceColor) { cur = cur.copy(saveHistory = it) }
                TS("Block Ads", Icons.Outlined.Block, cur.blockAds, textColor, surfaceColor) { cur = cur.copy(blockAds = it) }
                TS("Dark Mode", Icons.Outlined.DarkMode, cur.darkMode, textColor, surfaceColor) { cur = cur.copy(darkMode = it) }

                Spacer(Modifier.height(24.dp))
                Button(onClick = { onSave(cur) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(12.dp)) {
                    Text("Save", color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TS(label: String, icon: ImageVector, value: Boolean, textColor: Color, surfaceColor: Color, onToggle: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(10.dp), color = surfaceColor) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Purple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = textColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(value, onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Purple))
        }
    }
}

@Composable
fun RS(label: String, selected: Boolean, textColor: Color, surfaceColor: Color, borderColor: Color, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onClick() },
        shape = RoundedCornerShape(10.dp), color = surfaceColor,
        border = BorderStroke(1.dp, if (selected) Purple else borderColor)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = textColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, null, tint = Purple, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun BookmarksScreen(bookmarks: List<Bookmark>, darkMode: Boolean, bgColor: Color, surfaceColor: Color, textColor: Color,
    onNavigate: (String) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Purple) }
            Text("Bookmarks", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (bookmarks.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No bookmarks yet", color = if (darkMode) TextHint else Color(0xFF9CA3AF))
        } else LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bookmarks) { b ->
                Surface(modifier = Modifier.fillMaxWidth().clickable { onNavigate(b.url) }, shape = RoundedCornerShape(10.dp), color = surfaceColor) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, color = textColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.url, color = if (darkMode) TextHint else Color(0xFF9CA3AF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onDelete(b.url) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = if (darkMode) TextHint else Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(history: List<String>, darkMode: Boolean, bgColor: Color, surfaceColor: Color, textColor: Color,
    onNavigate: (String) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Purple) }
            Text("History", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear", color = Color(0xFFEF4444)) }
        }
        if (history.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No history", color = if (darkMode) TextHint else Color(0xFF9CA3AF))
        } else LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(history) { url ->
                Surface(modifier = Modifier.fillMaxWidth().clickable { onNavigate(url) }, shape = RoundedCornerShape(10.dp), color = surfaceColor) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = Purple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(url, color = textColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
