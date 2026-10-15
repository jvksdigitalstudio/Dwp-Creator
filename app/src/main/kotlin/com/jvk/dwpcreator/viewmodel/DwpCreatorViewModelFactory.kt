package com.jvk.dwpcreator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jvk.dwpcreator.di.AppContainer

/** Crea el [DwpCreatorViewModel] con sus dependencias reales. */
class DwpCreatorViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DwpCreatorViewModel::class.java)) {
            "ViewModel no soportado: ${modelClass.name}"
        }
        return DwpCreatorViewModel(container.newServices()) as T
    }
}
