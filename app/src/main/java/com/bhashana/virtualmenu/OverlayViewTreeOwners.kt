package com.bhashana.virtualmenu

import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import android.os.Bundle

/** Minimal owners so Compose can run outside an Activity. */
class OverlayViewTreeOwners : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val vmStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = vmStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate(savedState: Bundle? = null) {
        savedStateController.performRestore(savedState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }
    fun onStart()  { lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    fun onResume() { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        vmStore.clear()
    }
}