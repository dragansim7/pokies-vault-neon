package com.aistudio.pokiesvault.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

/**
 * Adaptive coroutine concurrency limiter (AIMD-style).
 * Starts at [initialLimit] (6), scales up to [maxLimit] (8) on sustained healthy responses,
 * and scales down toward [minLimit] (3) when rate-limit (429) or timeout bursts occur.
 */
class AdaptiveConcurrencyController(
    private val minLimit: Int = 3,
    initialLimit: Int = 6,
    private val maxLimit: Int = 8
) {
    private val mutex = Mutex()
    private var inFlight: Int = 0
    private var targetLimit: Int = initialLimit.coerceIn(minLimit, maxLimit)
    private var consecutiveHealthy: Int = 0

    private val _currentLimit = MutableStateFlow(targetLimit)
    val currentLimit: StateFlow<Int> = _currentLimit.asStateFlow()

    private val _activeWorkers = MutableStateFlow(0)
    val activeWorkers: StateFlow<Int> = _activeWorkers.asStateFlow()

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
                    _activeWorkers.value = inFlight
                    true
                } else {
                    false
                }
            }
            if (granted) return
            delay(18L)
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
            if (consecutiveHealthy >= 6 && targetLimit < maxLimit) {
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

    suspend fun reset(limit: Int = 6) {
        mutex.withLock {
            targetLimit = limit.coerceIn(minLimit, maxLimit)
            consecutiveHealthy = 0
            _currentLimit.value = targetLimit
        }
    }
}
