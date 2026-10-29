package com.mojrucek.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Tiny generic factory so screens can build their ViewModel from [com.mojrucek.app.di.AppContainer] without a DI framework. */
class GenericViewModelFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
