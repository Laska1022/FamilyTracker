package com.familytracker.app.map

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

/** Native MapLibre view with lifecycle forwarding; attribution stays enabled. */
@Composable
fun FamilyMap(
    points: List<LocationPoint>,
    names: Map<String, String>,
    route: Boolean,
    focus: LocationPoint?,
    cameraRequest: Int,
    modifier: Modifier = Modifier,
    onSelect: (LocationPoint) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentSelection by rememberUpdatedState(onSelect)
    val currentPoints by rememberUpdatedState(points)
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }
    val errorListener = remember { MapView.OnDidFailLoadingMapListener { failed = true } }
    DisposableEffect(view, lifecycle) {
        view.addOnDidFailLoadingMapListener(errorListener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.removeOnDidFailLoadingMapListener(errorListener)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(view) {
        view.getMapAsync { controller ->
            map = controller
            controller.uiSettings.isAttributionEnabled = true
            controller.setStyle(BuildConfig.MAP_STYLE_URL) { style ->
                style.addSource(GeoJsonSource("members"))
                style.addLayer(
                    CircleLayer("members-fresh", "members")
                        .withFilter(
                            Expression.eq(Expression.get("fresh"), Expression.literal(true))
                        )
                        .withProperties(
                            circleColor("#247B72"),
                            circleRadius(9f),
                            circleStrokeColor("#FFFFFF"),
                            circleStrokeWidth(3f),
                        )
                )
                style.addLayer(
                    CircleLayer("members-stale", "members")
                        .withFilter(
                            Expression.eq(Expression.get("fresh"), Expression.literal(false))
                        )
                        .withProperties(
                            circleColor("#737373"),
                            circleRadius(9f),
                            circleStrokeColor("#FFFFFF"),
                            circleStrokeWidth(3f),
                        )
                )
                style.addLayer(
                    SymbolLayer("member-names", "members")
                        .withProperties(
                            textField(Expression.get("name")),
                            textSize(12f),
                            textOffset(arrayOf(0f, 1.8f)),
                            textColor("#1C3630"),
                            textHaloColor("#FFFFFF"),
                            textHaloWidth(1.5f),
                        )
                )
                style.addSource(GeoJsonSource("route"))
                style.addLayer(
                    LineLayer("route-line", "route")
                        .withProperties(lineColor("#247B72"), lineWidth(4f))
                )
                style.addSource(GeoJsonSource("route-points"))
                style.addLayer(
                    CircleLayer("route-dots", "route-points")
                        .withProperties(
                            circleColor("#247B72"),
                            circleRadius(5f),
                            circleStrokeColor("#FFFFFF"),
                            circleStrokeWidth(1.5f),
                        )
                )
                ready = true
            }
            controller.addOnMapClickListener { coordinate ->
                val feature =
                    controller
                        .queryRenderedFeatures(
                            controller.projection.toScreenLocation(coordinate),
                            "route-dots",
                            "members-fresh",
                            "members-stale",
                        )
                        .firstOrNull()
                val id = feature?.getStringProperty("id")
                val uid = feature?.getStringProperty("uid")
                currentPoints
                    .firstOrNull { if (uid != null) it.uid == uid else it.id == id }
                    ?.let { currentSelection(it) }
                feature != null
            }
        }
    }
    LaunchedEffect(points, names, ready, route, clock) {
        val m = map ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        val lines =
            if (route && points.size > 1)
                listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(
                            points.map { Point.fromLngLat(it.longitude, it.latitude) }
                        )
                    )
                )
            else emptyList()
        style.getSourceAs<GeoJsonSource>("route")?.setGeoJson(FeatureCollection.fromFeatures(lines))
        style
            .getSourceAs<GeoJsonSource>("route-points")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    if (route)
                        points.map { p ->
                            Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).apply {
                                addStringProperty("id", p.id)
                            }
                        }
                    else emptyList()
                )
            )
        style
            .getSourceAs<GeoJsonSource>("members")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    if (!route)
                        points.map { p ->
                            Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).apply {
                                addStringProperty("uid", p.uid)
                                addStringProperty(
                                    "name",
                                    names[p.uid].orEmpty() +
                                        " · " +
                                        context.getString(
                                            if (p.isFresh(clock)) R.string.fresh else R.string.stale
                                        ),
                                )
                                addBooleanProperty("fresh", p.isFresh(clock))
                            }
                        }
                    else emptyList()
                )
            )
        if (points.isNotEmpty() && m.cameraPosition.zoom < 3)
            m.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(points.first().latitude, points.first().longitude),
                    12.0,
                )
            )
    }
    LaunchedEffect(focus, cameraRequest, ready) {
        if (ready && focus != null)
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(focus.latitude, focus.longitude), 15.0)
            )
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 64.dp)) {
            FilledIconButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) }) {
                Icon(Icons.Default.Add, stringResource(R.string.zoom_in))
            }
            FilledIconButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) }) {
                Icon(Icons.Default.Remove, stringResource(R.string.zoom_out))
            }
        }
        if (failed) Surface { Text(stringResource(R.string.map_error)) }
    }
}
