package net.primal.core.config

import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.primal.core.config.api.ApiConfigResponse
import net.primal.core.config.api.WellKnownApi
import net.primal.core.config.store.AppConfigDataStore
import net.primal.core.utils.Result
import net.primal.core.utils.coroutines.DispatcherProvider
import net.primal.core.utils.runCatching
import net.primal.core.utils.updater.Updater
import net.primal.domain.common.exception.NetworkException

class AppConfigHandler internal constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val appConfigStore: AppConfigDataStore,
    private val wellKnownApi: WellKnownApi,
) : Updater() {

    private val fetchMutex = Mutex()

    override suspend fun doUpdate(): Result<Unit> {
        fetchMutex.withLock {
            return withContext(dispatcherProvider.io()) {
                val response = fetchAppConfigOrNull()
                    ?: return@withContext Result.failure<Unit>(NetworkException("Could not fetch app config."))

                appConfigStore.updateConfig {
                    copy(
                        cacheUrl = response.cacheServers.firstOrNull() ?: this.cacheUrl,
                        uploadUrl = response.uploadServers.firstOrNull() ?: this.uploadUrl,
                        walletUrl = response.walletServers.firstOrNull() ?: this.walletUrl,
                    )
                }

                Result.success(Unit)
            }
        }
    }

    private suspend fun fetchAppConfigOrNull(): ApiConfigResponse? {
        // b2r fork (Sprint 1.2): the dynamic endpoint discovery that fetched
        // server lists from https://primal.net/.well-known/primal-endpoints.json
        // is disabled. b2r is serverless, so there is no remote config to pull.
        // We keep the method (and the wellKnownApi wiring) so signatures and the
        // Updater contract stay intact, but it never performs a network call.
        Napier.d("b2r: remote app-config fetch is disabled; using local defaults.")
        return null
    }

    suspend fun overrideCacheUrl(url: String) = appConfigStore.overrideCacheUrl(url = url)

    suspend fun restoreDefaultCacheUrl() {
        val response = fetchAppConfigOrNull()
        val wellKnownCacheUrl = response?.cacheServers?.firstOrNull()
        appConfigStore.revertCacheUrlOverrideFlag()
        appConfigStore.updateConfig {
            copy(cacheUrl = wellKnownCacheUrl ?: DEFAULT_APP_CONFIG.cacheUrl)
        }
    }
}
