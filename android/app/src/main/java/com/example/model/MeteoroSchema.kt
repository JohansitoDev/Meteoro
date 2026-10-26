package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Root schema for Meteoro dynamic applications.
 * Can contain an appName, optional version for live reload detection,
 * and a list of root components (screens).
 */
@JsonClass(generateAdapter = true)
data class MeteoroApp(
    @Json(name = "appName") val appName: String? = "Meteoro App",
    @Json(name = "version") val version: String? = "1.0.0",
    @Json(name = "theme") val theme: MeteoroThemeConfig? = null,
    @Json(name = "screens") val screens: List<MeteoroComponent>? = emptyList(),
    @Json(name = "initialRoute") val initialRoute: String? = null,
    @Json(name = "routes") val routes: List<MeteoroRoute>? = emptyList(),
    @Json(name = "navigationBar") val navigationBar: List<MeteoroNavigationItem>? = emptyList(),
    @Json(name = "meta") val meta: Map<String, String>? = emptyMap()
)

@JsonClass(generateAdapter = true)
data class MeteoroNavigationItem(
    @Json(name = "route") val route: String,
    @Json(name = "label") val label: String
)

@JsonClass(generateAdapter = true)
data class MeteoroRoute(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "components") val components: List<MeteoroComponent> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MeteoroThemeConfig(
    @Json(name = "primaryColor") val primaryColor: String? = null,
    @Json(name = "backgroundColor") val backgroundColor: String? = null,
    @Json(name = "textColor") val textColor: String? = null
)

/**
 * Dynamic UI Component description in the Meteoro specification.
 * Supported types:
 * - "text": Renders Text()
 * - "button": Renders Button() with action
 * - "stack": Renders Column or Row (vertical or horizontal)
 * - "input": Renders TextField()
 * - "image": Renders Coil AsyncImage()
 * - "card": Renders elevated card container
 * - "spacer": Renders spacing
 * - "divider": Renders line divider
 */
@JsonClass(generateAdapter = true)
data class MeteoroComponent(
    @Json(name = "id") val id: String? = null,
    @Json(name = "type") val type: String = "text",
    @Json(name = "value") val value: String? = null,
    @Json(name = "props") val props: MeteoroProps? = null
)

@JsonClass(generateAdapter = true)
data class MeteoroProps(
    @Json(name = "size") val size: Float? = null,
    @Json(name = "color") val color: String? = null,
    @Json(name = "backgroundColor") val backgroundColor: String? = null,
    @Json(name = "action") val action: String? = null,
    @Json(name = "direction") val direction: String? = "vertical", // "vertical" | "horizontal"
    @Json(name = "spacing") val spacing: Float? = null,
    @Json(name = "padding") val padding: Float? = null,
    @Json(name = "scrollable") val scrollable: Boolean? = false,
    @Json(name = "placeholder") val placeholder: String? = null,
    @Json(name = "rounded") val rounded: Float? = null,
    @Json(name = "fullWidth") val fullWidth: Boolean? = null,
    @Json(name = "alignment") val alignment: String? = "start", // "start", "center", "end"
    @Json(name = "fontWeight") val fontWeight: String? = null, // "normal", "bold", "medium"
    @Json(name = "children") val children: List<MeteoroComponent>? = null
)
