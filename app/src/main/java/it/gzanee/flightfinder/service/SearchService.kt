package it.gzanee.flightfinder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import it.gzanee.flightfinder.MainActivity
import it.gzanee.flightfinder.R
import it.gzanee.flightfinder.domain.model.JobStatus
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.repository.SearchRepository
import it.gzanee.flightfinder.ui.format.asEuro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Tiene viva la ricerca quando l'app non è in primo piano o lo schermo è spento: un servizio
 * in primo piano (tipo dataSync) con la notifica dell'avanzamento e il pulsante Ferma, più un
 * wake lock parziale perché il processore non si addormenti fra una richiesta e l'altra.
 * A ricerca finita lascia una notifica con l'esito e si ferma.
 */
@AndroidEntryPoint
class SearchService : Service() {

    @Inject lateinit var searchRepository: SearchRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { searchRepository.activeId?.let { searchRepository.cancel(it) } }
            return START_NOT_STICKY
        }
        createChannels()
        ServiceCompat.startForeground(
            this, PROGRESS_ID, progressNotification(null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        acquireWakeLock()
        if (watcher == null) watcher = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        // La ricerca potrebbe essere già finita (o mai partita) quando il servizio si avvia.
        delay(1_000)
        if (searchRepository.activeId == null) {
            finish(null)
            return
        }
        searchRepository.updates.collect { job ->
            if (job.status.isRunning) {
                notify(PROGRESS_ID, progressNotification(job))
            } else {
                // L'ultimo salvataggio arriva mentre il motore sta ancora chiudendo: si aspetta che finisca.
                repeat(50) { if (searchRepository.activeId != null) delay(100) }
                if (searchRepository.activeId == null) finish(job)
            }
        }
    }

    /** Android 15: un servizio dataSync dura al massimo 6 ore al giorno. Si ferma la ricerca (riprendibile). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.launch {
            searchRepository.activeId?.let { searchRepository.cancel(it) }
            finish(null)
        }
    }

    private fun finish(job: SearchJob?) {
        if (job != null) notify(DONE_ID, doneNotification(job))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- Notifiche ----------------

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, getString(R.string.notif_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openApp(searchId: String?): PendingIntent = PendingIntent.getActivity(
        this, searchId?.hashCode() ?: 0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (searchId != null) putExtra(MainActivity.EXTRA_SEARCH_ID, searchId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun progressNotification(job: SearchJob?): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SearchService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_flight)
            .setContentTitle(getString(if (job?.status == JobStatus.CANCELLING) R.string.notif_stopping else R.string.notif_searching))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(job?.id))
            .addAction(0, getString(R.string.stop), stop)
        if (job == null || job.total == 0) {
            builder.setProgress(0, 0, true)
        } else {
            val percent = (100 * job.completed / job.total).coerceIn(0, 100)
            builder.setProgress(100, percent, false)
            builder.setContentText(
                listOfNotNull(
                    "$percent%",
                    resources.getQuantityString(R.plurals.trips_found, job.results.size, job.results.size),
                    job.etaSeconds?.let { remaining(it) },
                ).joinToString(" · "),
            )
        }
        return builder.build()
    }

    private fun doneNotification(job: SearchJob): Notification {
        val trips = resources.getQuantityString(R.plurals.trips, job.results.size, job.results.size)
        val cheapest = job.results.values.minOfOrNull { it.price }
        val title = getString(
            when (job.status) {
                JobStatus.COMPLETE -> R.string.notif_done
                JobStatus.CANCELLED -> R.string.notif_stopped
                JobStatus.BLOCKED -> R.string.notif_blocked
                else -> R.string.notif_interrupted
            },
        )
        return NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_flight)
            .setContentTitle(title)
            .setContentText(listOfNotNull(trips, cheapest?.let { getString(R.string.notif_from_price, it.asEuro()) }).joinToString(" · "))
            .setAutoCancel(true)
            .setContentIntent(openApp(job.id))
            .build()
    }

    private fun remaining(seconds: Int): String {
        val minutes = ceil(seconds / 60.0).toInt()
        return when {
            minutes <= 1 -> getString(R.string.eta_under_minute)
            minutes < 90 -> getString(R.string.eta_left, resources.getQuantityString(R.plurals.minutes, minutes, minutes))
            else -> getString(R.string.eta_left, getString(R.string.hours_approx, "%.1f".format(resources.configuration.locales[0], minutes / 60.0)))
        }
    }

    private fun notify(id: Int, notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    // ---------------- Wake lock ----------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlightFinder:search")
            .apply { setReferenceCounted(false); acquire(MAX_WAKE_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP = "it.gzanee.flightfinder.STOP_SEARCH"
        private const val CHANNEL_PROGRESS = "search_progress"
        private const val CHANNEL_DONE = "search_done"
        private const val PROGRESS_ID = 1
        private const val DONE_ID = 2
        /** Un limite di sicurezza: anche se qualcosa va storto il wake lock non resta acceso per sempre. */
        private const val MAX_WAKE_MS = 6 * 60 * 60 * 1000L

        /** Da chiamare mentre l'app è in primo piano, quando una ricerca parte o riprende. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SearchService::class.java))
        }
    }
}
