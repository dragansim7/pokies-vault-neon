package com.aistudio.pokiesvault.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aistudio.pokiesvault.MainActivity
import com.aistudio.pokiesvault.PokiesVaultApp
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.coordinator.BackgroundRunNotificationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Play-compliant [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC] foreground service that keeps
 * active Account Fetcher and Bonus Finder runs alive when Background Runs is switched ON,
 * holds a scoped CPU partial wake lock strictly while a scan is active, and shows live scan
 * progress with a one-tap Stop action in the notification shade.
 */
class VaultBackgroundRunService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var lastNotificationPushMs = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    private val coordinator
        get() = (application as PokiesVaultApp).coordinator

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            coordinator.stopAllRuns()
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        // Read synchronous coordinator state so we never race against async combine() StateFlow updates
        val current = coordinator.buildCurrentNotificationState()
        promoteForeground(buildNotification(current))

        if (!current.isRunning || !current.allowBackground) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        acquireWakeLockIfNeeded()
        startObservingCoordinator()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!coordinator.isAllowBackgroundRunsEnabled()) {
            coordinator.stopAllRunsForAppClose()
            stopSelfCleanly()
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startObservingCoordinator() {
        if (observerJob?.isActive == true) return
        val nm = getSystemService(NotificationManager::class.java)
        observerJob = serviceScope.launch {
            coordinator.notificationState.collect { state ->
                // Double-check synchronous coordinator state so a stale initial combine emission never kills the service
                val syncActive = coordinator.isAnyRunActive()
                val syncAllow = coordinator.isAllowBackgroundRunsEnabled()
                if (!syncActive || !syncAllow) {
                    stopSelfCleanly()
                    return@collect
                }
                if (!state.isRunning || !state.allowBackground) {
                    return@collect
                }
                val now = System.currentTimeMillis()
                if (now - lastNotificationPushMs >= 500L || state.progressDone >= state.progressTotal) {
                    lastNotificationPushMs = now
                    nm?.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    private fun acquireWakeLockIfNeeded() {
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            val existing = wakeLock
            if (existing != null && existing.isHeld) return
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            wakeLock = lock
        } catch (_: Exception) {
            // Best effort — foreground service still runs even if wake lock acquisition fails
        }
    }

    private fun releaseWakeLock() {
        try {
            val lock = wakeLock
            wakeLock = null
            if (lock != null && lock.isHeld) {
                lock.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun promoteForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfCleanly() {
        releaseWakeLock()
        observerJob?.cancel()
        observerJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.bg_service_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.bg_service_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(state: BackgroundRunNotificationState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VaultBackgroundRunService::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopPending = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(state.title)
            .setContentText(state.subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.bg_service_stop_action),
                stopPending
            )

        if (state.progressTotal > 0) {
            builder.setProgress(state.progressTotal, state.progressDone.coerceAtMost(state.progressTotal), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "pokies_vault_bg_runs"
        const val NOTIFICATION_ID = 4102
        const val ACTION_START_OR_SYNC = "com.aistudio.pokiesvault.action.START_OR_SYNC_BG"
        const val ACTION_STOP_ALL = "com.aistudio.pokiesvault.action.STOP_ALL_BG"
        private const val WAKE_LOCK_TAG = "PokiesVault:BackgroundScanWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 2L * 60L * 60L * 1000L // 2h safety cap
    }
}
