package net.primal.android.networking.relays

import io.github.aakira.napier.Napier
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.primal.android.networking.relays.errors.NostrPublishException
import net.primal.android.user.accounts.active.ActiveAccountStore
import net.primal.android.user.db.UsersDatabase
import net.primal.android.user.domain.Relay
import net.primal.android.user.domain.RelayKind
import net.primal.android.user.domain.mapToRelayDO
import net.primal.core.networking.sockets.NostrSocketClientFactory
import net.primal.core.utils.coroutines.DispatcherProvider
import net.primal.domain.global.CachingImportRepository
import net.primal.domain.nostr.NostrEvent

@Singleton
class RelaysSocketManager @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val nostrSocketClientFactory: NostrSocketClientFactory,
    private val cachingImportRepository: CachingImportRepository,
    private val activeAccountStore: ActiveAccountStore,
    private val usersDatabase: UsersDatabase,
) {
    private val scope = CoroutineScope(dispatchers.io())
    private val relayPoolsMutex = Mutex()

    private var relaysObserverJob: Job? = null

    private fun buildRelayPool() =
        RelayPool(
            dispatchers = dispatchers,
            nostrSocketClientFactory = nostrSocketClientFactory,
            cachingImportRepository = cachingImportRepository,
        )

    private val userRelaysPool: RelayPool = buildRelayPool()
    private val nwcRelaysPool: RelayPool = buildRelayPool()
    private val fallbackRelaysPool: RelayPool = buildRelayPool()

    val userRelayPoolStatus = userRelaysPool.relayPoolStatus

    init {
        initFallbackRelaysPool()
        observeActiveUserId()
    }

    private fun initFallbackRelaysPool() = fallbackRelaysPool.changeRelays(FALLBACK_RELAYS)

    private fun observeActiveUserId() =
        scope.launch {
            activeAccountStore.activeUserId.collect { userId ->
                when {
                    userId.isEmpty() -> {
                        relaysObserverJob?.cancel()
                        relaysObserverJob = null
                        clearRelayPools()
                    }

                    else -> {
                        relaysObserverJob?.cancel()
                        relaysObserverJob = observeRelays(userId)
                    }
                }
            }
        }

    private fun observeRelays(userId: String): Job =
        scope.launch {
            try {
                usersDatabase.relays().observeRelays(userId = userId).collect { relays ->
                    val userRelays = relays.filter { it.kind == RelayKind.UserRelay }.map { it.mapToRelayDO() }
                    val nwcRelays = relays.filter { it.kind == RelayKind.NwcRelay }.map { it.mapToRelayDO() }
                    updateRelayPools(regularRelays = userRelays, walletRelays = nwcRelays)
                }
            } catch (error: CancellationException) {
                Napier.w(throwable = error) { "Relay observation cancelled" }
            }
        }

    private suspend fun updateRelayPools(regularRelays: List<Relay>?, walletRelays: List<Relay>?) {
        relayPoolsMutex.withLock {
            val userRelaysChanged = userRelaysPool.relays != regularRelays
            if (userRelaysChanged && !regularRelays.isNullOrEmpty()) {
                userRelaysPool.changeRelays(relays = regularRelays)
            }

            val nwcRelaysChanged = nwcRelaysPool.relays != walletRelays
            if (nwcRelaysChanged && !walletRelays.isNullOrEmpty()) {
                nwcRelaysPool.changeRelays(relays = walletRelays)
            }
        }
    }

    private suspend fun clearRelayPools() =
        relayPoolsMutex.withLock {
            userRelaysPool.closePool()
            nwcRelaysPool.closePool()
        }

    // b2r fork (Sprint 1.3): event publishing to Nostr relays is removed.
    // The original Nostr design pushes signed events to "dumb" relays that store
    // and rebroadcast everything; b2r does not use that delivery mechanism — posts
    // are kept in the local append/LWW log and propagated directly peer-to-peer.
    // These methods keep their signatures (and @Throws contract) so every caller
    // still compiles, but they open no relay socket and send nothing. The actual
    // P2P hand-off is wired in Step 2 via the replication service.
    @Suppress("UNUSED_PARAMETER")
    @Throws(NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: NostrEvent) {
        Napier.d { "b2r: relay publish disabled; event ${nostrEvent.id} not sent to any relay." }
    }

    @Suppress("UNUSED_PARAMETER")
    @Throws(NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: NostrEvent, relays: List<Relay>) {
        Napier.d { "b2r: relay publish disabled; event ${nostrEvent.id} not sent to ${relays.size} relay(s)." }
    }

    @Suppress("UNUSED_PARAMETER")
    @Throws(NostrPublishException::class)
    suspend fun publishNwcEvent(nostrEvent: NostrEvent) {
        Napier.d { "b2r: relay publish disabled; NWC event ${nostrEvent.id} not sent to any relay." }
    }

    // b2r fork (Sprint 1.3): no outbound relay connections are established.
    fun tryConnectingToAllUserRelays() {
        Napier.d { "b2r: relay connections disabled; skipping connect to user relays." }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun tryConnectingToUserRelay(url: String) {
        Napier.d { "b2r: relay connections disabled; skipping connect to $url." }
    }
}
