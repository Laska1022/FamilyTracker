package com.familytracker.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.familytracker.app.*
import com.familytracker.app.domain.*
import com.familytracker.app.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

class TrackerViewModel(app: Application): AndroidViewModel(app) {
    private val graph=(app as TrackerApp).graph!!
    val profile=graph.auth.profiles.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val circleId=profile.flatMapLatest { p -> if(p==null) flowOf(null) else graph.settings.circle(p.uid) }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val circle=combine(profile,circleId) { p,id -> p to id }.flatMapLatest { (p,id) ->
        if(p==null || id==null) flowOf(null) else graph.circles.circle(id,p.uid)
    }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    private val refreshTick=MutableStateFlow(0)
    val live=combine(circle,refreshTick) { c,_ -> c }.flatMapLatest { c -> if(c==null) flowOf(emptyList()) else graph.locations.live(c) }
        .stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val interval=profile.flatMapLatest { p -> if(p==null) flowOf(ReportingInterval.ONE_MINUTE) else graph.settings.interval(p.uid) }
        .stateIn(viewModelScope,SharingStarted.Eagerly,ReportingInterval.ONE_MINUTE)
    val connected=graph.locations.connected
    val pending=graph.locations.pending
    val syncError=graph.locations.syncError
    val tracking=TrackingStatus.state
    val trackingError=TrackingStatus.error
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow<Int?>(null)
    val pendingMembership=MutableStateFlow(0)
    val history=MutableStateFlow<List<LocationPoint>>(emptyList())
    val historyLoading=MutableStateFlow(false)
    val historyHidden=MutableStateFlow(false)
    private var historyJob: Job?=null
    init {
        viewModelScope.launch { combine(profile,connected) { p,c -> p to c }.collectLatest { (p,online) ->
            if(p!=null) {
                pendingMembership.value=graph.circles.pendingCount(p.uid)
                if(online) runCatching { graph.circles.retry(p.uid) }.onFailure { message.value=R.string.partial_membership }
                pendingMembership.value=graph.circles.pendingCount(p.uid)
            }
        } }
        // A new process has no active sharing consent; hide prior history and clear stale live data.
        viewModelScope.launch { combine(profile,circleId) { p,id -> p to id }.collectLatest { (p,id) ->
            if(p!=null && id!=null && tracking.value==TrackingState.STOPPED) graph.locations.stop(p.uid,id)
        } }
    }
    private fun action(error: Int=R.string.operation_error, block: suspend ()->Unit) {
        if(busy.value) return
        viewModelScope.launch {
            busy.value=true
            try { withTimeout(30_000) { block() } }
            catch(e: CancellationException) { if(e is TimeoutCancellationException) message.value=error else throw e }
            catch(_: Exception) { message.value=error }
            finally { busy.value=false;profile.value?.let { pendingMembership.value=graph.circles.pendingCount(it.uid) } }
        }
    }
    fun authenticate(email: String,password: String,name: String,register: Boolean) = action(R.string.auth_error) {
        if(register) graph.auth.register(email,password,name) else graph.auth.signIn(email,password)
    }
    fun reset(email: String)=action { graph.auth.reset(email);message.value=R.string.reset_sent }
    fun createCircle(name: String)=action(R.string.partial_membership) {
        val p=profile.value ?: return@action
        val actual=graph.firebaseAuth.currentUser?.displayName.orEmpty().ifBlank { p.email.substringBefore('@') }
        graph.settings.setCircle(p.uid,p.uid)
        graph.circles.create(p.uid,name,actual)
    }
    fun joinCircle(code: String)=action {
        val p=profile.value ?: return@action
        graph.circles.request(code.trim(),p.uid,graph.firebaseAuth.currentUser?.displayName.orEmpty().ifBlank { p.email.substringBefore('@') })
        graph.settings.setCircle(p.uid,code.trim())
    }
    fun approve(member: Member)=action(R.string.partial_membership) {
        val p=profile.value!!;graph.circles.changeMembership(p.uid,circleId.value!!,member.uid,member.name,true)
    }
    fun reject(member: Member)=action { graph.circles.reject(circleId.value!!,member.uid) }
    fun remove(member: Member)=action(R.string.partial_membership) { graph.circles.changeMembership(profile.value!!.uid,circleId.value!!,member.uid,member.name,false) }
    fun retryMembership()=action(R.string.partial_membership) { graph.circles.retry(profile.value!!.uid) }
    fun leave()=action(R.string.partial_membership) {
        val p=profile.value!!;val id=circleId.value ?: return@action
        if(id==p.uid && (circle.value?.members?.size ?: 0)>1) { message.value=R.string.owner_leave;return@action }
        stopNow()
        graph.locations.stop(p.uid,id)
        graph.circles.changeMembership(p.uid,id,p.uid,p.name,false)
        graph.settings.setCircle(p.uid,null)
        history.value=emptyList()
    }
    fun startSharing() {
        if(circle.value==null) return
        try { ContextCompat.startForegroundService(getApplication(),Intent(getApplication(),TrackingService::class.java)
            .setAction(TrackingService.ACTION_START).putExtra("circle",circleId.value)) }
        catch(_: RuntimeException) { message.value=R.string.sharing_error }
    }
    private fun stopNow() { getApplication<Application>().stopService(Intent(getApplication(),TrackingService::class.java)) }
    fun stopSharing() { stopNow();viewModelScope.launch { val p=profile.value;val id=circleId.value;if(p!=null && id!=null) graph.locations.stop(p.uid,id) } }
    fun setInterval(value: ReportingInterval)=action { graph.settings.setInterval(profile.value!!.uid,value) }
    fun refresh() { refreshTick.value++;viewModelScope.launch { graph.locations.sync() } }
    fun signOut()=action {
        val p=profile.value ?: return@action
        stopNow()
        circleId.value?.let { graph.locations.stop(p.uid,it);graph.locations.confirmStopped(p.uid,it) }
        // Online sign-out must acknowledge history revocation. Offline users retain a durable tombstone.
        graph.locations.clearLocal(p.uid)
        historyJob?.cancel();history.value=emptyList()
        graph.auth.signOut()
    }
    fun loadHistory(target: String,date: LocalDate) {
        historyJob?.cancel();history.value=emptyList();historyHidden.value=false
        historyJob=viewModelScope.launch {
            val id=circleId.value ?: return@launch
            val p=profile.value ?: return@launch
            val visibility=if(target==p.uid) flowOf(true) else graph.locations.historyVisible(id,target)
            combine(visibility,circle) { visible,c -> visible && c?.members?.any { it.uid==target }==true }.collectLatest { visible ->
                history.value=emptyList();historyHidden.value=!visible;historyLoading.value=visible
                if(visible) try { history.value=withTimeout(30_000) { graph.locations.history(id,target,date,ZoneId.systemDefault()) } }
                catch(e: CancellationException) { if(e is TimeoutCancellationException) message.value=R.string.operation_error else throw e }
                catch(_: Exception) { message.value=R.string.operation_error }
                finally { historyLoading.value=false }
            }
        }
    }
    fun closeHistory() { historyJob?.cancel();history.value=emptyList() }
    fun deleteHistory()=action {
        val p=profile.value!!;val id=circleId.value!!
        stopNow();graph.locations.stop(p.uid,id)
        graph.locations.deleteHistory(p.uid,id);history.value=emptyList()
    }
}
