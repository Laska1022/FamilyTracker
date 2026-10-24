package com.familytracker.app.map

import com.familytracker.app.domain.LocationPoint
import org.maplibre.geojson.Feature

/** Member and history layers intentionally have different property schemas. */
internal fun selectedPoint(feature: Feature?, points: List<LocationPoint>): LocationPoint? {
    fun stringProperty(name: String): String? =
        feature
            ?.properties()
            ?.get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
    val uid = stringProperty("uid")
    val id = stringProperty("id")
    return when {
        uid != null -> points.firstOrNull { it.uid == uid }
        id != null -> points.firstOrNull { it.id == id }
        else -> null
    }
}
