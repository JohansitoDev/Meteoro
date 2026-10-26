package com.example.network

import android.content.Context
import android.net.Uri
import com.example.model.MeteoroApp
import com.example.model.MeteoroComponent
import com.example.model.MeteoroProps
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MeteoroRepository(private val context: Context? = null) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val appAdapter = moshi.adapter(MeteoroApp::class.java)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Normalizes a scanned or entered string into a fetchable URL or demo key.
     */
    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("project://", ignoreCase = true) -> trimmed
            trimmed.startsWith("demo:", ignoreCase = true) -> trimmed
            trimmed.startsWith("meteoro://app/", ignoreCase = true) -> {
                val path = trimmed.removePrefix("meteoro://app/").removePrefix("/")
                "https://api.meteoro.dev/app/$path"
            }
            trimmed.startsWith("meteoro://", ignoreCase = true) -> {
                val id = trimmed.removePrefix("meteoro://").removeSuffix("/")
                if (id.contains("/code")) {
                    "https://api.meteoro.dev/$id"
                } else {
                    "https://api.meteoro.dev/app/$id/code"
                }
            }
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".dev") || trimmed.contains(".com") || trimmed.contains("/") -> "https://$trimmed"
            else -> "https://api.meteoro.dev/app/$trimmed/code"
        }
    }

    /**
     * Extracts an app ID or readable name from a URL.
     */
    fun extractAppId(url: String): String {
        if (url.startsWith("demo:")) {
            return url.removePrefix("demo:").replaceFirstChar { it.uppercase() }
        }
        val clean = url.removePrefix("https://").removePrefix("http://")
        val segments = clean.split("/").filter { it.isNotEmpty() && it != "code" && it != "app" }
        return segments.lastOrNull() ?: "Meteoro App"
    }

    /**
     * Fetches the dynamic Meteoro schema from the specified endpoint.
     */
    suspend fun fetchAppSchema(endpointUrl: String): Result<Pair<MeteoroApp, String>> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(endpointUrl)

        // Built-in demo presets for immediate interactive testing and offline use
        if (normalized.startsWith("demo:")) {
            val demoType = normalized.removePrefix("demo:")
            val demoApp = getBuiltInDemo(demoType)
            val jsonString = appAdapter.toJson(demoApp)
            return@withContext Result.success(Pair(demoApp, jsonString))
        }

        if (normalized.equals("project://bundle", ignoreCase = true)) {
            val appContext = context
                ?: return@withContext Result.failure(IOException("No hay contexto para leer la app empaquetada."))
            return@withContext try {
                val body = appContext.assets.open("meteoro-app.json").bufferedReader().use { it.readText() }
                val parsed = appAdapter.fromJson(body)
                    ?: throw IOException("Error al parsear el JSON empaquetado de Meteoro")
                Result.success(Pair(parsed, body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        try {
            val request = Request.Builder()
                .url(normalized)
                .header("Accept", "application/json")
                .header("User-Agent", "MeteoroGo-Android/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP Error ${response.code}: ${response.message}")
                )
            }

            val body = response.body?.string() ?: throw IOException("Cuerpo de respuesta vacío")
            val parsed = appAdapter.fromJson(body) ?: throw IOException("Error al parsear el JSON de Meteoro")
            Result.success(Pair(resolveProjectAssets(parsed, normalized), body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveProjectAssets(app: MeteoroApp, endpoint: String): MeteoroApp {
        val assetBase = Uri.parse(endpoint)
            .buildUpon()
            .encodedPath("/assets/")
            .encodedQuery(null)
            .fragment(null)
            .build()
            .toString()

        fun resolve(component: MeteoroComponent): MeteoroComponent {
            val value = if (
                component.type == "image" &&
                component.value?.startsWith("assets/") == true
            ) {
                assetBase + component.value.removePrefix("assets/")
            } else {
                component.value
            }
            return component.copy(
                value = value,
                props = component.props?.copy(
                    children = component.props.children?.map(::resolve)
                )
            )
        }

        return app.copy(
            screens = app.screens?.map(::resolve),
            routes = app.routes?.map { route ->
                route.copy(components = route.components.map(::resolve))
            }
        )
    }

    /**
     * Generates rich built-in interactive demo schemas so users can immediately
     * experience native rendering and live reload without setting up a remote server.
     */
    fun getBuiltInDemo(key: String, versionCounter: Int = 1): MeteoroApp {
        return when (key.lowercase().trim()) {
            "coffee", "cafe" -> createCoffeeDemo(versionCounter)
            "dashboard" -> createDashboardDemo(versionCounter)
            else -> createCounterDemo(versionCounter)
        }
    }

    private fun createCoffeeDemo(versionCounter: Int): MeteoroApp {
        return MeteoroApp(
            appName = "Café Meteoro",
            version = "1.0.$versionCounter",
            screens = listOf(
                MeteoroComponent(
                    id = "header_img",
                    type = "image",
                    value = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=800&q=80",
                    props = MeteoroProps(rounded = 16f, size = 180f)
                ),
                MeteoroComponent(
                    id = "title",
                    type = "text",
                    value = "☕ Café Artesanal de Origen",
                    props = MeteoroProps(size = 24f, color = "#FF6B35", fontWeight = "bold", padding = 8f)
                ),
                MeteoroComponent(
                    id = "desc",
                    type = "text",
                    value = "Tostado fresco todas las mañanas. Renderizado dinámicamente en tiempo real con Meteoro Go.",
                    props = MeteoroProps(size = 14f, color = "#94A3B8", padding = 4f)
                ),
                MeteoroComponent(
                    id = "price_stack",
                    type = "stack",
                    props = MeteoroProps(
                        direction = "horizontal",
                        spacing = 12f,
                        alignment = "center",
                        padding = 8f,
                        children = listOf(
                            MeteoroComponent(
                                type = "text",
                                value = "$4.50 USD",
                                props = MeteoroProps(size = 20f, color = "#00D2D3", fontWeight = "bold")
                            ),
                            MeteoroComponent(
                                type = "text",
                                value = "• 250g Grano Entero",
                                props = MeteoroProps(size = 14f, color = "#64748B")
                            )
                        )
                    )
                ),
                MeteoroComponent(
                    id = "user_notes",
                    type = "input",
                    value = "",
                    props = MeteoroProps(
                        placeholder = "¿Instrucciones especiales para tu café?",
                        padding = 8f,
                        rounded = 12f
                    )
                ),
                MeteoroComponent(
                    id = "actions_stack",
                    type = "stack",
                    props = MeteoroProps(
                        direction = "horizontal",
                        spacing = 8f,
                        padding = 8f,
                        children = listOf(
                            MeteoroComponent(
                                type = "button",
                                value = "🛒 Añadir al Carrito",
                                props = MeteoroProps(
                                    action = "toast:¡Añadido 1 Café Artesanal al carrito!",
                                    backgroundColor = "#FF6B35",
                                    color = "#FFFFFF",
                                    rounded = 12f
                                )
                            ),
                            MeteoroComponent(
                                type = "button",
                                value = "❤️ Favorito",
                                props = MeteoroProps(
                                    action = "alert:Guardado en tus favoritos de Meteoro",
                                    backgroundColor = "#1E3A63",
                                    color = "#00D2D3",
                                    rounded = 12f
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun createDashboardDemo(versionCounter: Int): MeteoroApp {
        return MeteoroApp(
            appName = "Dev Metrics Dashboard",
            version = "2.1.$versionCounter",
            screens = listOf(
                MeteoroComponent(
                    type = "text",
                    value = "🚀 Meteoro Live Telemetry",
                    props = MeteoroProps(size = 22f, color = "#FF6B35", fontWeight = "bold", padding = 8f)
                ),
                MeteoroComponent(
                    type = "stack",
                    props = MeteoroProps(
                        direction = "horizontal",
                        spacing = 10f,
                        padding = 8f,
                        children = listOf(
                            MeteoroComponent(
                                type = "card",
                                props = MeteoroProps(
                                    backgroundColor = "#162B4D",
                                    padding = 12f,
                                    rounded = 12f,
                                    children = listOf(
                                        MeteoroComponent(type = "text", value = "99.98%", props = MeteoroProps(size = 20f, color = "#10B981", fontWeight = "bold")),
                                        MeteoroComponent(type = "text", value = "Uptime Server", props = MeteoroProps(size = 12f, color = "#94A3B8"))
                                    )
                                )
                            ),
                            MeteoroComponent(
                                type = "card",
                                props = MeteoroProps(
                                    backgroundColor = "#162B4D",
                                    padding = 12f,
                                    rounded = 12f,
                                    children = listOf(
                                        MeteoroComponent(type = "text", value = "1.2 ms", props = MeteoroProps(size = 20f, color = "#00D2D3", fontWeight = "bold")),
                                        MeteoroComponent(type = "text", value = "Latency UI", props = MeteoroProps(size = 12f, color = "#94A3B8"))
                                    )
                                )
                            )
                        )
                    )
                ),
                MeteoroComponent(
                    type = "text",
                    value = "Estado de Sincronización en Vivo:",
                    props = MeteoroProps(size = 16f, color = "#F1F5F9", padding = 8f)
                ),
                MeteoroComponent(
                    type = "input",
                    value = "",
                    props = MeteoroProps(placeholder = "Escribe un comando o query de prueba...", padding = 8f)
                ),
                MeteoroComponent(
                    type = "button",
                    value = "⚡ Ejecutar Ping al Servidor",
                    props = MeteoroProps(
                        action = "toast:Ping ejecutado: Respuesta en 4ms (OK 200)",
                        backgroundColor = "#0081C9",
                        color = "#FFFFFF",
                        rounded = 12f
                    )
                )
            )
        )
    }

    private fun createCounterDemo(versionCounter: Int): MeteoroApp {
        return MeteoroApp(
            appName = "Contador Interactivo",
            version = "1.0.$versionCounter",
            screens = listOf(
                MeteoroComponent(
                    type = "text",
                    value = "🎯 Contador Reactivo Nativo",
                    props = MeteoroProps(size = 22f, color = "#FF6B35", fontWeight = "bold", padding = 8f)
                ),
                MeteoroComponent(
                    type = "text",
                    value = "Valor reactivo en vivo: {counter}",
                    props = MeteoroProps(size = 18f, color = "#00D2D3", fontWeight = "bold", padding = 8f)
                ),
                MeteoroComponent(
                    type = "stack",
                    props = MeteoroProps(
                        direction = "horizontal",
                        spacing = 16f,
                        alignment = "center",
                        padding = 16f,
                        children = listOf(
                            MeteoroComponent(
                                type = "button",
                                value = "➖ Disminuir",
                                props = MeteoroProps(
                                    action = "counter:dec",
                                    backgroundColor = "#1E3A63",
                                    color = "#F1F5F9",
                                    rounded = 12f
                                )
                            ),
                            MeteoroComponent(
                                type = "button",
                                value = "➕ Incrementar",
                                props = MeteoroProps(
                                    action = "counter:inc",
                                    backgroundColor = "#FF6B35",
                                    color = "#FFFFFF",
                                    rounded = 12f
                                )
                            )
                        )
                    )
                ),
                MeteoroComponent(
                    type = "button",
                    value = "📢 Mostrar Alerta Nativa",
                    props = MeteoroProps(
                        action = "alert:¡Hola desde Meteoro Go! Este componente se está ejecutando 100% de manera nativa en Jetpack Compose.",
                        backgroundColor = "#0081C9",
                        color = "#FFFFFF",
                        rounded = 12f
                    )
                )
            )
        )
    }
}
