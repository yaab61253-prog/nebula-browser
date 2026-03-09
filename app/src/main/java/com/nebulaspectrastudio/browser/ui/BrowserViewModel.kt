package com.nebulaspectrastudio.browser.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TabState(
    val id: Int,
    val url: String = "https://www.google.com",
    val title: String = "New Tab",
    val isIncognito: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

data class Bookmark(val title: String, val url: String)

class BrowserViewModel : ViewModel() {
    private val _tabs = MutableStateFlow(listOf(TabState(id = 0)))
    val tabs: StateFlow<List<TabState>> = _tabs

    private val _currentTabId = MutableStateFlow(0)
    val currentTabId: StateFlow<Int> = _currentTabId

    private val _bookmarks = MutableStateFlow(listOf<Bookmark>())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    private val _history = MutableStateFlow(listOf<String>())
    val history: StateFlow<List<String>> = _history

    private val _settings = MutableStateFlow(BrowserSettings())
    val settings: StateFlow<BrowserSettings> = _settings

    private var nextId = 1

    val currentTab get() = _tabs.value.find { it.id == _currentTabId.value }

    fun newTab(incognito: Boolean = false) {
        val tab = TabState(id = nextId++, isIncognito = incognito)
        _tabs.value = _tabs.value + tab
        _currentTabId.value = tab.id
    }

    fun closeTab(id: Int) {
        val list = _tabs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (list.size <= 1) { list[0] = list[0].copy(url = "https://www.google.com"); _tabs.value = list; return }
        list.removeAt(idx)
        _tabs.value = list
        _currentTabId.value = list.getOrNull(idx.coerceAtMost(list.size - 1))?.id ?: list.last().id
    }

    fun switchTab(id: Int) { _currentTabId.value = id }

    fun updateTab(id: Int, url: String? = null, title: String? = null, canBack: Boolean? = null, canForward: Boolean? = null) {
        _tabs.value = _tabs.value.map {
            if (it.id == id) it.copy(
                url = url ?: it.url,
                title = title ?: it.title,
                canGoBack = canBack ?: it.canGoBack,
                canGoForward = canForward ?: it.canGoForward
            ) else it
        }
    }

    fun addToHistory(url: String) {
        if (currentTab?.isIncognito == true) return
        if (!_settings.value.saveHistory) return
        _history.value = (listOf(url) + _history.value).take(100)
    }

    fun clearHistory() { _history.value = emptyList() }

    fun addBookmark(title: String, url: String) {
        _bookmarks.value = _bookmarks.value + Bookmark(title, url)
    }

    fun removeBookmark(url: String) {
        _bookmarks.value = _bookmarks.value.filter { it.url != url }
    }

    fun updateSettings(settings: BrowserSettings) { _settings.value = settings }

}

data class BrowserSettings(
    val desktopMode: Boolean = false,
    val blockAds: Boolean = false,
    val saveHistory: Boolean = true,
    val darkMode: Boolean = true,
)
