package com.jvk.dwpcreator.di

import android.app.Application
import com.jvk.dwpcreator.midi.MidiInputManager
import com.jvk.dwpcreator.sampler.android.SamplePlayer
import com.jvk.dwpcreator.sampler.android.SamplerFxPreferences
import com.jvk.dwpcreator.session.project.ContentResolverFileStreams

/**
 * Raíz de composición: el único sitio que conoce las implementaciones
 * concretas de Android y las enchufa a los contratos que usa la sesión.
 */
class AppContainer(private val application: Application) {

    /**
     * Dependencias nuevas para un ViewModel. Cada ViewModel recibe su propio
     * reproductor, porque su ciclo de vida (abrir y liberar el stream de audio)
     * va ligado al del ViewModel.
     */
    fun newServices(): AppServices {
        val context = application.applicationContext
        return AppServices(
            playback = SamplePlayer(context),
            fxStore = SamplerFxPreferences(context),
            fileStreams = ContentResolverFileStreams(context.contentResolver),
            midiInputFactory = { MidiInputManager(context) }
        )
    }
}
