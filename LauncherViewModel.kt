package com.example.tvhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvhome.data.AppInfo
import com.example.tvhome.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    /** Incremented every time the Home button is pressed while we are already open. */
    private val _homePresses = MutableStateFlow(0)
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) { _apps.value = repo.load() }
    }

    fun onHomePressed() {
        _homePresses.value += 1
    }

    fun toggleShowHidden() {
        _showHidden.value = !_showHidden.value
    }

    fun togglePin(pkg: String) = edit { repo.togglePin(pkg) }

    fun toggleHidden(pkg: String) = edit { repo.toggleHidden(pkg) }

    fun move(pkg: String, delta: Int, rowPackages: List<String>) = edit {
        repo.move(pkg, delta, rowPackages, _apps.value.map { it.packageName })
    }

    private fun edit(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            block()
            _apps.value = repo.load()
        }
    }
}
