package com.aistudio.pokiesvault.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adaptive coroutine concurrency limiter (AIMD-style) with live active and peak worker telemetry.
 * Configured per FetchSpeedPreset (default Safe: 1..3 workers, starting at 2).
 * Scales up cautiously (+1 after 10 consecutive healthy domains) and steps down immediately
 * on firewall/rate-limit/timeout congestion.
 */
class AdaptiveConcurrencyController(
    private var minLimit: Int = 1,
    initialLimit: Int = 2,
    private var maxLimit: Int = 3
) {
    private val mutex = Mutex()
    private var inFlight: Int = 0
    private var peakObserved: Int = 0
    private var targetLimit: Int = initialLimit.coerceIn(minLimit, maxLimit)
    private var consecutiveHealthy: Int = 0

    private val _currentLimit = MutableStateFlow(targetLimit)
    val currentLimit: StateFlow<Int> = _currentLimit.asStateFlow()

    private val _activeWorkers = MutableStateFlow(0)
    val activeWorkers: StateFlow<Int> = _activeWorkers.asStateFlow()

    private val _peakWorkers = MutableStateFlow(0)
    val peakWorkers: StateFlow<Int> = _peakWorkers.asStateFlow()

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        return try {
            block()
        } finally {
            release()
        }
    }

    private suspend fun acquire() {
        while (true) {
            val granted = mutex.withLock {
                if (inFlight < targetLimit) {
                    inFlight++
                    if (inFlight > peakObserved) {
                        peakObserved = inFlight
                        _peakWorkers.value = peakObserved
                    }
                    _activeWorkers.value = inFlight
                    true
                } else {
                    false
                }
            }
            if (granted) return
            delay(25L)
        }
    }

    private suspend fun release() {
        mutex.withLock {
            inFlight = (inFlight - 1).coerceAtLeast(0)
            _activeWorkers.value = inFlight
        }
    }

    suspend fun recordHealthy() {
        mutex.withLock {
            consecutiveHealthy++
            if (consecutiveHealthy >= 10 && targetLimit < maxLimit) {
                targetLimit++
                consecutiveHealthy = 0
                _currentLimit.value = targetLimit
            }
        }
    }

    suspend fun recordCongestion() {
        mutex.withLock {
            consecutiveHealthy = 0
            if (targetLimit > minLimit) {
                targetLimit = (targetLimit - 1).coerceAtLeast(minLimit)
                _currentLimit.value = targetLimit
            }
        }
    }

    suspend fun reset(min: Int = 1, initial: Int = 2, max: Int = 3) {
        mutex.withLock {
            minLimit = min.coerceAtLeast(1)
            maxLimit = max.coerceAtLeast(minLimit)
            targetLimit = initial.coerceIn(minLimit, maxLimit)
            consecutiveHealthy = 0
            peakObserved = 0
            _peakWorkers.value = 0
            _currentLimit.value = targetLimit
        }
    }
}
