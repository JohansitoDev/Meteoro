package com.example.ui.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MeteoroComponent
import com.example.model.MeteoroProps
import com.example.ui.theme.MeteoroBorder
import com.example.ui.theme.MeteoroCard
import com.example.ui.theme.MeteoroOrange
import com.example.ui.theme.MeteoroSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Safely parses hex colors like "#FF6B35", "#0A1931", or "#AARRGGBB"
 */
fun parseHexColor(hexString: String?, fallback: Color): Color {
    if (hexString.isNullOrBlank()) return fallback
    return try {
        var cleanHex = hexString.trim().removePrefix("#")
        when (cleanHex.length) {
            3 -> { // #RGB -> #RRGGBB
                cleanHex = cleanHex.map { "$it$it" }.joinToString("")
                Color(android.graphics.Color.parseColor("#FF$cleanHex"))
            }
            6 -> Color(android.graphics.Color.parseColor("#FF$cleanHex"))
            8 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Root composable that renders a list of Meteoro dynamic components.
 */
@Composable
fun MeteoroScreenRenderer(
    components: List<MeteoroComponent>,
    inputValues: Map<String, String>,
    interactiveCounter: Int,
    onAction: (String) -> Unit,
    onInputChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dynamic_screen_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        components.forEachIndexed { index, component ->
            MeteoroComponentRenderer(
                component = component,
                index = index,
                inputValues = inputValues,
                interactiveCounter = interactiveCounter,
                onAction = onAction,
                onInputChange = onInputChange
            )
        }
    }
}

/**
 * Dispatches dynamic rendering based on the Meteoro component type.
 */
@Composable
fun MeteoroComponentRenderer(
    component: MeteoroComponent,
    index: Int,
    inputValues: Map<String, String>,
    interactiveCounter: Int,
    onAction: (String) -> Unit,
    onInputChange: (String, String) -> Unit
) {
    val props = component.props ?: MeteoroProps()
    val id = component.id ?: "comp_${component.type}_$index"

    when (component.type.lowercase().trim()) {
        "text" -> {
            var displayText = component.value.orEmpty()
            if (displayText.contains("{counter}")) {
                displayText = displayText.replace("{counter}", interactiveCounter.toString())
            }

            val textColor = parseHexColor(props.color, TextPrimary)
            val fontSize = (props.size ?: 16f).sp
            val fontWeight = when (props.fontWeight?.lowercase()) {
                "bold" -> FontWeight.Bold
                "medium" -> FontWeight.SemiBold
                "light" -> FontWeight.Light
                else -> FontWeight.Normal
            }
            val textAlign = when (props.alignment?.lowercase()) {
                "center" -> TextAlign.Center
                "end", "right" -> TextAlign.End
                else -> TextAlign.Start
            }

            Text(
                text = displayText,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        props.backgroundColor?.let { parseHexColor(it, Color.Transparent) }
                            ?: Color.Transparent
                    )
                    .padding((props.padding ?: 0f).dp)
                    .testTag("text_$id")
            )
        }

        "button" -> {
            val bg = parseHexColor(props.backgroundColor, MeteoroOrange)
            val textColor = parseHexColor(props.color, Color.White)
            val shape = RoundedCornerShape((props.rounded ?: 12f).dp)

            Button(
                onClick = {
                    val actionStr = props.action ?: "toast:Botón pulsado"
                    onAction(actionStr)
                },
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = bg,
                    contentColor = textColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding((props.padding ?: 4f).dp)
                    .heightIn(min = 48.dp)
                    .testTag("button_$id")
            ) {
                Text(
                    text = component.value ?: "Acción",
                    color = textColor,
                    fontSize = (props.size ?: 15f).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        "input" -> {
            val currentValue = inputValues[id] ?: component.value.orEmpty()
            val shape = RoundedCornerShape((props.rounded ?: 12f).dp)

            OutlinedTextField(
                value = currentValue,
                onValueChange = { onInputChange(id, it) },
                placeholder = {
                    Text(
                        text = props.placeholder ?: "Escriba aquí...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                shape = shape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeteoroOrange,
                    unfocusedBorderColor = MeteoroBorder,
                    focusedTextColor = parseHexColor(props.color, TextPrimary),
                    unfocusedTextColor = parseHexColor(props.color, TextPrimary),
                    focusedContainerColor = parseHexColor(props.backgroundColor, MeteoroSurface),
                    unfocusedContainerColor = parseHexColor(props.backgroundColor, MeteoroSurface)
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding((props.padding ?: 4f).dp)
                    .testTag("input_$id")
            )
        }

        "stack" -> {
            val isHorizontal = props.direction.equals("horizontal", ignoreCase = true)
            val spacing = (props.spacing ?: 8f).dp
            val padding = (props.padding ?: 0f).dp
            val bg = props.backgroundColor?.let { parseHexColor(it, Color.Transparent) }
            val roundedShape = RoundedCornerShape((props.rounded ?: 0f).dp)

            var stackModifier = Modifier.fillMaxWidth()
            if (bg != null && bg != Color.Transparent) {
                stackModifier = stackModifier
                    .background(bg, roundedShape)
                    .clip(roundedShape)
            }
            if (padding.value > 0) {
                stackModifier = stackModifier.padding(padding)
            }

            val children = props.children.orEmpty()

            if (isHorizontal) {
                if (props.scrollable == true) {
                    stackModifier = stackModifier.horizontalScroll(rememberScrollState())
                }
                val horizontalArrangement = when (props.alignment?.lowercase()) {
                    "center" -> Arrangement.Center
                    "end", "right" -> Arrangement.End
                    "space_between" -> Arrangement.SpaceBetween
                    "space_around" -> Arrangement.SpaceAround
                    else -> Arrangement.spacedBy(spacing)
                }

                Row(
                    modifier = stackModifier.testTag("hstack_$id"),
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    children.forEachIndexed { childIdx, child ->
                        val itemModifier = if (props.scrollable == true) {
                            Modifier.widthIn(min = 160.dp, max = 240.dp)
                        } else {
                            Modifier.weight(1f, fill = false)
                        }
                        Box(modifier = itemModifier) {
                            MeteoroComponentRenderer(
                                component = child,
                                index = childIdx,
                                inputValues = inputValues,
                                interactiveCounter = interactiveCounter,
                                onAction = onAction,
                                onInputChange = onInputChange
                            )
                        }
                    }
                }
            } else {
                if (props.scrollable == true) {
                    stackModifier = stackModifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                }
                val verticalAlignment = when (props.alignment?.lowercase()) {
                    "center" -> Alignment.CenterHorizontally
                    "end", "right" -> Alignment.End
                    else -> Alignment.Start
                }

                Column(
                    modifier = stackModifier.testTag("vstack_$id"),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    horizontalAlignment = verticalAlignment
                ) {
                    children.forEachIndexed { childIdx, child ->
                        MeteoroComponentRenderer(
                            component = child,
                            index = childIdx,
                            inputValues = inputValues,
                            interactiveCounter = interactiveCounter,
                            onAction = onAction,
                            onInputChange = onInputChange
                        )
                    }
                }
            }
        }

        "image" -> {
            val context = LocalContext.current
            val imageUrl = component.value.orEmpty()
            val roundedShape = RoundedCornerShape((props.rounded ?: 12f).dp)
            val heightDp = (props.size ?: 180f).dp
            val localBitmap by produceState<Bitmap?>(null, imageUrl) {
                if (imageUrl.startsWith("assets/")) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            context.assets.open(imageUrl).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                                ?: run {
                                    Log.e("MeteoroRenderer", "No se pudo decodificar la imagen $imageUrl")
                                    null
                                }
                        } catch (error: IOException) {
                            Log.e("MeteoroRenderer", "No se pudo cargar la imagen $imageUrl", error)
                            null
                        }
                    }
                } else {
                    value = null
                }
            }
            val localImage = localBitmap?.asImageBitmap()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .padding((props.padding ?: 4f).dp)
                    .clip(roundedShape)
                    .background(MeteoroCard)
                    .testTag("image_$id"),
                contentAlignment = Alignment.Center
            ) {
                if (localImage != null) {
                    Image(
                        bitmap = localImage,
                        contentDescription = "Imagen del proyecto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(heightDp)
                    )
                } else if (imageUrl.isNotBlank() && !imageUrl.startsWith("assets/")) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Dynamic Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(heightDp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Image Placeholder",
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        "card" -> {
            val bg = parseHexColor(props.backgroundColor, MeteoroCard)
            val roundedShape = RoundedCornerShape((props.rounded ?: 16f).dp)
            val padding = (props.padding ?: 12f).dp

            Card(
                shape = roundedShape,
                colors = CardDefaults.cardColors(containerColor = bg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MeteoroBorder, roundedShape)
                    .testTag("card_$id")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy((props.spacing ?: 8f).dp)
                ) {
                    if (!component.value.isNullOrBlank()) {
                        Text(
                            text = component.value,
                            color = parseHexColor(props.color, TextPrimary),
                            fontSize = (props.size ?: 17f).sp,
                            fontWeight = when (props.fontWeight?.lowercase()) {
                                "normal" -> FontWeight.Normal
                                "medium" -> FontWeight.Medium
                                else -> FontWeight.SemiBold
                            }
                        )
                    }
                    props.children.orEmpty().forEachIndexed { childIdx, child ->
                        MeteoroComponentRenderer(
                            component = child,
                            index = childIdx,
                            inputValues = inputValues,
                            interactiveCounter = interactiveCounter,
                            onAction = onAction,
                            onInputChange = onInputChange
                        )
                    }
                }
            }
        }

        "spacer" -> {
            Spacer(modifier = Modifier.height((props.size ?: 16f).dp))
        }

        "divider" -> {
            HorizontalDivider(
                color = parseHexColor(props.color, MeteoroBorder),
                modifier = Modifier.padding(vertical = (props.padding ?: 8f).dp)
            )
        }

        else -> {
            // Fallback for custom or unrecognized tags
            Text(
                text = "${component.type}: ${component.value}",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
