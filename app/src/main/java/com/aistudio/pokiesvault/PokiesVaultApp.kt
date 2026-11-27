package com.aistudio.pokiesvault

import android.app.Application
import com.aistudio.pokiesvault.data.coordinator.VaultRunCoordinator
import com.aistudio.pokiesvault.data.repository.VaultRepository

class PokiesVaultApp : Application() {
    val repository: VaultRepository by lazy { VaultRepository(this) }
    val coordinator: VaultRunCoordinator by lazy { VaultRunCoordinator(this, repository) }
}
