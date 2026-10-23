package com.familytracker.app.map

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.familytracker.app.BuildConfig
import com.familytracker.app.R
import com.familytracker.app.domain.LocationPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.*
import org.maplibre.android.annotations.*
import org.maplibre.geojson.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import java.text.DateFormat
import java.util.Date

/** Native MapLibre view with lifecycle forwarding; attribution stays enabled. */
@Composable
fun FamilyMap(points: List<LocationPoint>, names: Map<String,String>, route: Boolean, focus: LocationPoint?,
    cameraRequest: Int, modifier: Modifier=Modifier, onSelect: (LocationPoint)->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val currentSelection by rememberUpdatedState(onSelect)
    val currentPoints by rememberUpdatedState(points)
    val view=remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { clock=System.currentTimeMillis();kotlinx.coroutines.delay(30_000) } }
    val markerIcons=remember {
        listOf(Color.rgb(36,123,114),Color.rgb(115,115,115)).map { color ->
            val size=(32*context.resources.displayMetrics.density).toInt()
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color=Color.WHITE;canvas.drawCircle(size/2f,size/2f,size/2.1f,paint)
            paint.color=color;canvas.drawCircle(size/2f,size/2f,size/2.7f,paint)
            IconFactory.getInstance(context).fromBitmap(bitmap)
        }
    }
    val errorListener=remember { MapView.OnDidFailLoadingMapListener { failed=true } }
    DisposableEffect(view,lifecycle) {
        view.addOnDidFailLoadingMapListener(errorListener)
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);view.removeOnDidFailLoadingMapListener(errorListener);view.onPause();view.onStop();view.onDestroy() }
    }
    LaunchedEffect(view) {
        view.getMapAsync { controller ->
            map=controller
            controller.uiSettings.isAttributionEnabled=true
            controller.setStyle(BuildConfig.MAP_STYLE_URL) { style ->
                style.addSource(GeoJsonSource("route"))
                style.addLayer(LineLayer("route-line","route").withProperties(lineColor("#247B72"),lineWidth(4f)))
                style.addSource(GeoJsonSource("route-points"))
                style.addLayer(CircleLayer("route-dots","route-points").withProperties(circleColor("#247B72"),circleRadius(5f),circleStrokeColor("#FFFFFF"),circleStrokeWidth(1.5f)))
                ready=true
            }
            controller.setOnMarkerClickListener { marker ->
                currentPoints.firstOrNull { it.uid==marker.snippet }?.let { currentSelection(it) };true
            }
            controller.addOnMapClickListener { coordinate ->
                val features=controller.queryRenderedFeatures(controller.projection.toScreenLocation(coordinate),"route-dots")
                val id=features.firstOrNull()?.getStringProperty("id")
                currentPoints.firstOrNull { it.id==id }?.let { currentSelection(it) }
                id!=null
            }
        }
    }
    LaunchedEffect(points,names,ready,route,clock) {
        val m=map ?: return@LaunchedEffect
        if(!ready) return@LaunchedEffect
        m.clear()
        val style=m.style ?: return@LaunchedEffect
        val line=if(route && points.size>1) Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude,it.latitude) })) else Feature.fromGeometry(LineString.fromLngLats(emptyList()))
        style.getSourceAs<GeoJsonSource>("route")?.setGeoJson(line)
        style.getSourceAs<GeoJsonSource>("route-points")?.setGeoJson(FeatureCollection.fromFeatures(if(route) points.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.longitude,p.latitude)).apply { addStringProperty("id",p.id) }
        } else emptyList()))
        if(!route) points.forEach { p -> m.addMarker(MarkerOptions().position(LatLng(p.latitude,p.longitude))
            .title(names[p.uid].orEmpty()+" · "+context.getString(if(p.isFresh(clock)) R.string.fresh else R.string.stale)).snippet(p.uid).icon(markerIcons[if(p.isFresh(clock)) 0 else 1])) }
        if(points.isNotEmpty() && m.cameraPosition.zoom<3) m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points.first().latitude,points.first().longitude),12.0))
    }
    LaunchedEffect(focus,cameraRequest,ready) {
        if(ready && focus!=null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus.latitude,focus.longitude),15.0))
    }
    Box(modifier) {
        AndroidView(factory={view},modifier=Modifier.fillMaxSize())
        if(failed) Surface { Text(stringResource(R.string.map_error)) }
    }
}
