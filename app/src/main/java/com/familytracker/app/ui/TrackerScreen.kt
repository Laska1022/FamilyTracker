@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.familytracker.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytracker.app.R
import com.familytracker.app.domain.*
import com.familytracker.app.map.FamilyMap
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private fun time(value: Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(value))
@Composable fun TrackerScreen(vm: TrackerViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val circle by vm.circle.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val online by vm.connected.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val snackbar=remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var historyMember by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(context.getString(it));vm.message.value=null } }
    LaunchedEffect(profile?.uid) { tab=0;historyMember=null }
    Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={ if(profile!=null) NavigationBar {
        listOf(R.string.map_tab to Icons.Default.Map,R.string.circle_tab to Icons.Default.Groups,R.string.history_tab to Icons.Default.Route,R.string.settings_tab to Icons.Default.Settings).forEachIndexed { index,(label,icon) ->
            NavigationBarItem(selected=tab==index,onClick={tab=index},icon={Icon(icon,contentDescription=null)},label={Text(stringResource(label))})
        }
    } },topBar={if(profile!=null) TopAppBar(title={Column { Text(circle?.name ?: stringResource(R.string.app_name));Text(stringResource(if(online) R.string.online else R.string.offline),style=MaterialTheme.typography.labelSmall) }})}) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if(profile==null) LoginScreen(vm,busy) else when(tab) {
                0 -> MapScreen(vm) { uid -> historyMember=uid;tab=2 }
                1 -> CircleScreen(vm)
                2 -> HistoryScreen(vm,historyMember ?: profile!!.uid) { historyMember=it }
                else -> SettingsScreen(vm)
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
@Composable private fun LoginScreen(vm: TrackerViewModel,busy: Boolean) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(36.dp));Icon(Icons.Default.LocationOn,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.tagline),style=MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.welcome_detail),style=MaterialTheme.typography.bodyLarge)
        if(register) OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.display_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(email,{email=it},label={Text(stringResource(R.string.email))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text(stringResource(R.string.password))},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
        Button(onClick={vm.authenticate(email,password,name,register)},enabled=!busy && email.isNotBlank() && password.length>=6 && (!register || name.trim().length in 1..60),modifier=Modifier.fillMaxWidth()) { Text(stringResource(if(register) R.string.register else R.string.sign_in)) }
        TextButton(onClick={register=!register},enabled=!busy) { Text(stringResource(if(register) R.string.sign_in else R.string.register)) }
        TextButton(onClick={vm.reset(email)},enabled=!busy && email.isNotBlank()) { Text(stringResource(R.string.reset_password)) }
    }
}
@Composable private fun SharingControl(vm: TrackerViewModel) {
    val state by vm.tracking.collectAsStateWithLifecycle()
    val circle by vm.circle.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var explain by remember { mutableStateOf(false) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted=permissions[Manifest.permission.ACCESS_COARSE_LOCATION]==true || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(granted) vm.startSharing() else vm.message.value=R.string.permission_missing
    }
    val active=state==TrackingState.ACTIVE || state==TrackingState.STARTING
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(when(state) { TrackingState.ACTIVE->R.string.sharing_active;TrackingState.STARTING->R.string.sharing_starting;TrackingState.ERROR->R.string.sharing_error;else->R.string.sharing_stopped }),style=MaterialTheme.typography.labelLarge)
        Button(onClick={if(active) vm.stopSharing() else explain=true},enabled=active || circle!=null,modifier=Modifier.fillMaxWidth(),colors=if(active) ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
            Icon(if(active) Icons.Default.StopCircle else Icons.Default.ShareLocation,null);Spacer(Modifier.width(8.dp));Text(stringResource(if(active) R.string.stop_sharing else R.string.start_sharing))
        }
    }
    if(explain) AlertDialog(onDismissRequest={explain=false},title={Text(stringResource(R.string.permission_title))},text={Text(stringResource(R.string.permission_explanation))},confirmButton={TextButton(onClick={
        explain=false
        val required=mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT>=33) required+=Manifest.permission.POST_NOTIFICATIONS
        launcher.launch(required.toTypedArray())
    }) { Text(stringResource(R.string.continue_button)) }},dismissButton={TextButton(onClick={explain=false}) { Text(stringResource(R.string.cancel)) }})
}
@Composable private fun MapScreen(vm: TrackerViewModel,onHistory: (String)->Unit) {
    val points by vm.live.collectAsStateWithLifecycle()
    val circle by vm.circle.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<LocationPoint?>(null) }
    var camera by remember { mutableIntStateOf(0) }
    val selected=points.firstOrNull { it.uid==selectedId }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            FamilyMap(points,circle?.members?.associate { it.uid to it.name }.orEmpty(),false,focus,camera,Modifier.fillMaxSize()) { selectedId=it.uid;focus=it;camera++ }
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick={focus=points.firstOrNull { it.uid==profile?.uid };camera++;if(focus==null) vm.message.value=R.string.location_unavailable}) { Icon(Icons.Default.MyLocation,stringResource(R.string.center_self)) }
                FilledIconButton(onClick={vm.refresh()}) { Icon(Icons.Default.Refresh,stringResource(R.string.refresh)) }
            }
            if(circle==null || points.isEmpty()) Surface(Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max=240.dp),shape=MaterialTheme.shapes.medium,tonalElevation=3.dp) {
                Text(stringResource(if(circle==null) R.string.no_circle else R.string.no_locations),Modifier.padding(12.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                circle?.members?.forEach { member -> AssistChip(onClick={selectedId=member.uid;focus=points.firstOrNull { it.uid==member.uid };camera++},label={Text(member.name)}) }
            }
        }
        Surface(tonalElevation=4.dp) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(selected!=null) {
                MemberDetails(selected,circle?.members?.firstOrNull { it.uid==selected.uid }?.name.orEmpty())
                Row { TextButton(onClick={onHistory(selected.uid)}) { Text(stringResource(R.string.view_history)) };TextButton(onClick={selectedId=null}) { Text(stringResource(R.string.close)) } }
            }
            SharingControl(vm)
        } }
    }
}
@Composable private fun MemberDetails(point: LocationPoint,name: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(point) { while(true) { now=System.currentTimeMillis();kotlinx.coroutines.delay(30_000) } }
    Text(name,style=MaterialTheme.typography.titleMedium)
    Text(stringResource(if(point.isFresh(now)) R.string.fresh else R.string.stale),color=if(point.isFresh(now)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    Text(stringResource(R.string.last_update,time(point.deviceTime)),style=MaterialTheme.typography.bodySmall)
    if(point.uploadedAt>0) Text(stringResource(R.string.uploaded_at,time(point.uploadedAt)),style=MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.accuracy_battery,point.accuracy,point.battery?.let { "$it%" } ?: stringResource(R.string.unknown)),style=MaterialTheme.typography.bodySmall)
}
@Composable private fun CircleScreen(vm: TrackerViewModel) {
    val circle by vm.circle.collectAsStateWithLifecycle()
    val circleId by vm.circleId.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val pending by vm.pendingMembership.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var remove by remember { mutableStateOf<Member?>(null) }
    var leave by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        if(pending>0) item { Text(stringResource(R.string.partial_membership),color=MaterialTheme.colorScheme.error);Button(onClick={vm.retryMembership()}) { Text(stringResource(R.string.retry_membership,pending)) } }
        if(circleId==null) {
            item { Text(stringResource(R.string.no_circle),style=MaterialTheme.typography.headlineSmall) }
            item { OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.circle_name))},modifier=Modifier.fillMaxWidth());Button(onClick={vm.createCircle(name)},enabled=name.trim().length in 1..60) { Text(stringResource(R.string.create_circle)) } }
            item { HorizontalDivider() }
            item { OutlinedTextField(code,{code=it},label={Text(stringResource(R.string.circle_code))},modifier=Modifier.fillMaxWidth());Button(onClick={vm.joinCircle(code)},enabled=code.isNotBlank()) { Text(stringResource(R.string.join_circle)) } }
        } else if(circle==null) item { Text(stringResource(R.string.pending_approval));TextButton(onClick={leave=true}) { Text(stringResource(R.string.leave_circle)) } }
        else {
            item { Text(circle!!.name,style=MaterialTheme.typography.headlineMedium);Text(circleId!!,style=MaterialTheme.typography.bodySmall)
                Button(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,context.getString(R.string.invitation_text,circleId)),context.getString(R.string.share_invitation)))}) { Icon(Icons.Default.PersonAdd,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.share_invitation)) } }
            item { Text(stringResource(R.string.members),style=MaterialTheme.typography.titleLarge) }
            items(circle!!.members,key={it.uid}) { member -> ListItem(headlineContent={Text(member.name)},leadingContent={Icon(Icons.Default.Person,null)},trailingContent={if(profile?.uid==circleId && member.uid!=profile?.uid) TextButton(onClick={remove=member}) { Text(stringResource(R.string.remove)) }}) }
            if(profile?.uid==circleId && circle!!.requests.isNotEmpty()) {
                item { Text(stringResource(R.string.requests),style=MaterialTheme.typography.titleLarge) }
                items(circle!!.requests,key={"request-${it.uid}"}) { member -> Column { Text(member.name);Row { TextButton(onClick={vm.approve(member)}) { Text(stringResource(R.string.approve)) };TextButton(onClick={vm.reject(member)}) { Text(stringResource(R.string.reject)) } } } }
            }
            item { OutlinedButton(onClick={leave=true}) { Text(stringResource(R.string.leave_circle)) } }
        }
    }
    if(remove!=null) ConfirmDialog(R.string.remove_confirm,{remove=null}) { vm.remove(remove!!);remove=null }
    if(leave) ConfirmDialog(R.string.leave_confirm,{leave=false}) { vm.leave();leave=false }
}
@Composable private fun HistoryScreen(vm: TrackerViewModel,target: String,onTarget: (String)->Unit) {
    val circle by vm.circle.collectAsStateWithLifecycle()
    val points by vm.history.collectAsStateWithLifecycle()
    val loading by vm.historyLoading.collectAsStateWithLifecycle()
    val hidden by vm.historyHidden.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date=LocalDate.parse(dateText)
    var selected by remember { mutableStateOf<LocationPoint?>(null) }
    var camera by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(target,dateText,circle?.id) { selected=null;vm.loadHistory(target,date) }
    DisposableEffect(Unit) { onDispose { vm.closeHistory() } }
    if(circle==null) { Text(stringResource(R.string.no_circle),Modifier.padding(24.dp));return }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Box { TextButton(onClick={menu=true}) { Text(circle!!.members.firstOrNull { it.uid==target }?.name.orEmpty());Icon(Icons.Default.ArrowDropDown,null) }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) { circle!!.members.forEach { m -> DropdownMenuItem(text={Text(m.name)},onClick={menu=false;onTarget(m.uid)}) } }
            }
            TextButton(onClick={DatePickerDialog(context,{_,y,m,d -> dateText=LocalDate.of(y,m+1,d).toString()},date.year,date.monthValue-1,date.dayOfMonth).show()}) { Icon(Icons.Default.CalendarMonth,stringResource(R.string.choose_date));Text(dateText) }
        }
        FamilyMap(points,emptyMap(),true,selected,camera,Modifier.fillMaxWidth().weight(1f)) { selected=it;camera++ }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal=16.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.distance,routeDistanceMeters(points)/1000),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.history_note,ZoneId.systemDefault().id),style=MaterialTheme.typography.bodySmall) }
            if(!loading && points.isEmpty()) item { Text(stringResource(if(hidden) R.string.history_hidden else R.string.empty_history)) }
            if(points.isNotEmpty()) item { Text(stringResource(R.string.first_point,time(points.first().deviceTime)));Text(stringResource(R.string.last_point,time(points.last().deviceTime))) }
            if(selected!=null && points.any { it.id==selected!!.id }) item { Text(stringResource(R.string.point_info,time(selected!!.deviceTime),selected!!.accuracy),color=MaterialTheme.colorScheme.primary) }
            items(points,key={it.id}) { p -> ListItem(headlineContent={Text(time(p.deviceTime))},supportingContent={Text(stringResource(R.string.coordinates,p.latitude,p.longitude,p.accuracy))},modifier=Modifier.clickable { selected=p;camera++ }) }
        }
    }
}
@Composable private fun SettingsScreen(vm: TrackerViewModel) {
    val interval by vm.interval.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val online by vm.connected.collectAsStateWithLifecycle()
    val syncError by vm.syncError.collectAsStateWithLifecycle()
    val trackingError by vm.trackingError.collectAsStateWithLifecycle()
    val circle by vm.circle.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var signOut by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SharingControl(vm)
        Text(stringResource(R.string.privacy_note),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.interval),style=MaterialTheme.typography.titleMedium)
        ReportingInterval.entries.forEach { value -> Row(Modifier.fillMaxWidth().clickable { vm.setInterval(value) },verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected=interval==value,onClick={vm.setInterval(value)});Text(stringResource(if(value==ReportingInterval.ONE_MINUTE) R.string.one_minute else R.string.five_minutes))
        } }
        Text(stringResource(R.string.interval_note),style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(stringResource(if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) R.string.permission_granted else R.string.permission_missing))
        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) { Text(stringResource(R.string.open_app_settings)) }
        Text(stringResource(R.string.battery_note),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }}) { Text(stringResource(R.string.battery_settings)) }
        if(trackingError) Text(stringResource(R.string.location_unavailable),color=MaterialTheme.colorScheme.error)
        Text(stringResource(if(online) R.string.online else R.string.offline))
        Text(stringResource(R.string.pending_points,pending))
        if(!online) Text(stringResource(R.string.offline_stop),style=MaterialTheme.typography.bodySmall)
        if(syncError) Text(stringResource(R.string.sync_error),color=MaterialTheme.colorScheme.error)
        TextButton(onClick={delete=true},enabled=circle!=null) { Text(stringResource(R.string.delete_history),color=MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick={signOut=true}) { Icon(Icons.AutoMirrored.Filled.ExitToApp,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.sign_out)) }
    }
    if(signOut) ConfirmDialog(R.string.sign_out_confirm,{signOut=false}) { vm.signOut();signOut=false }
    if(delete) ConfirmDialog(R.string.delete_confirm,{delete=false}) { vm.deleteHistory();delete=false }
}
@Composable private fun ConfirmDialog(text: Int,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,text={Text(stringResource(text))},confirmButton={TextButton(onClick=onConfirm) { Text(stringResource(R.string.confirm)) }},dismissButton={TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }})
}
