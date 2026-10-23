package com.familytracker.app.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.familytracker.app.*
import com.familytracker.app.domain.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.UUID

object TrackingStatus {
    val state=MutableStateFlow(TrackingState.STOPPED)
    val error=MutableStateFlow(false)
}
class TrackingService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val graph get()=(application as TrackerApp).graph!!
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var job: Job?=null
    private var identity: Pair<String,String>?=null
    private var callback: LocationCallback?=null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==ACTION_STOP) { stopTracking();return START_NOT_STICKY }
        if(job?.isActive==true) return START_NOT_STICKY
        val uid=graph.firebaseAuth.currentUser?.uid
        val circle=intent?.getStringExtra("circle")
        val permission=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!mayStartTracking(uid!=null,!circle.isNullOrBlank(),permission,intent?.action==ACTION_START)) { stopSelf();return START_NOT_STICKY }
        identity=uid!! to circle!!
        TrackingStatus.state.value=TrackingState.STARTING
        try { foreground() } catch(_: RuntimeException) { fail();return START_NOT_STICKY }
        job=scope.launch {
            try {
                withTimeout(15_000) {
                    check(graph.circles.approved(circle,uid).first { it })
                    graph.locations.begin(uid,circle)
                }
                launch {
                    graph.circles.approved(circle,uid).collect { approved -> if(!approved) stopTracking() }
                }
                launch {
                    graph.auth.profiles.collect { profile -> if(profile?.uid!=uid) stopTracking() }
                }
                TrackingStatus.state.value=TrackingState.ACTIVE
                TrackingStatus.error.value=false
                graph.settings.interval(uid).collectLatest { interval ->
                    locations(interval).collect { location ->
                        val battery=(getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
                        val point=LocationPoint(UUID.randomUUID().toString(),uid,circle,location.latitude,location.longitude,
                            location.accuracy.toDouble(),if(location.hasSpeed()) location.speed.toDouble() else null,battery,location.time,intervalMs=interval.millis)
                        if(point.isValid()) graph.locations.record(point)
                    }
                }
            } catch(e: CancellationException) { if(e is TimeoutCancellationException) fail() else throw e }
            catch(_: Exception) { fail() }
        }
        return START_NOT_STICKY
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun locations(interval: ReportingInterval) = kotlinx.coroutines.flow.callbackFlow {
        var lastElapsed=Long.MIN_VALUE
        val listener=object: LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location=result.lastLocation ?: return
                val elapsed=location.elapsedRealtimeNanos/1_000_000
                if(SystemClock.elapsedRealtime()-elapsed>30_000) return
                if(lastElapsed!=Long.MIN_VALUE && elapsed-lastElapsed<interval.millis) return
                lastElapsed=elapsed
                trySend(location)
            }
            override fun onLocationAvailability(value: LocationAvailability) { TrackingStatus.error.value=!value.isLocationAvailable }
        }
        callback=listener
        val request=LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY,interval.millis)
            .setMinUpdateIntervalMillis(interval.millis).setMaxUpdateDelayMillis(interval.millis)
            .setWaitForAccurateLocation(false).build()
        fused.requestLocationUpdates(request,listener,Looper.getMainLooper()).addOnFailureListener { close(it) }
        awaitClose { fused.removeLocationUpdates(listener);if(callback===listener) callback=null }
    }
    private fun foreground() {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,getString(R.string.tracking_channel),NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,TrackingService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_location)
            .setContentTitle(getString(R.string.sharing_active)).setContentText(getString(R.string.notification_detail))
            .setContentIntent(open).setOngoing(true).addAction(0,getString(R.string.stop_sharing),stop).build()
        ServiceCompat.startForeground(this,NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }
    private fun fail() { TrackingStatus.error.value=true;stopTracking();TrackingStatus.state.value=TrackingState.ERROR }
    private fun stopTracking() {
        callback?.let { fused.removeLocationUpdates(it) };callback=null
        job?.cancel();job=null
        val old=identity;identity=null
        if(old!=null) graph.scope.launch { graph.locations.stop(old.first,old.second) }
        TrackingStatus.state.value=TrackingState.STOPPED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { stopTracking();scope.cancel();super.onDestroy() }
    companion object {
        const val ACTION_START="com.familytracker.START"
        const val ACTION_STOP="com.familytracker.STOP"
        private const val CHANNEL="location_sharing"
        private const val NOTIFICATION_ID=1
    }
}
