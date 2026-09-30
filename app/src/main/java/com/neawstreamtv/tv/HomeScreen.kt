package com.neawstreamtv.tv

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Modelo de datos básico para el canal
data class Canal(
    val nombre: String,
    val url: String,
    val logoUrl: String
)

val ColorOscuroFondo = Color(0xFF111424)
val ColorSuperficieOscura = Color(0xFF1A1E30)
val ColorBlancoAlpha5 = Color(0x0DFFFFFF)
val ColorBlancoAlpha10 = Color(0x1AFFFFFF)
val ColorDorado = Color(0xFFFFBF00)

val DarkColorScheme = darkColorScheme(
    primary = ColorDorado,
    background = ColorOscuroFondo,
    surface = ColorOscuroFondo,
    surfaceVariant = ColorSuperficieOscura,
    onBackground = Color.White,
    onSurface = Color.White
)

fun parsearLista(body: String): List<Canal> {
    val lineas = body.split('\n')
    val listaTemporal = mutableListOf<Canal>()
    var nombre: String? = null
    var logo: String? = null
    val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")

    for (lineaRaw in lineas) {
        val linea = lineaRaw.trim()
        if (linea.startsWith("#EXTINF")) {
            nombre = linea.substringAfterLast(",").trim()
            val match = logoRegex.find(linea)
            logo = match?.groupValues?.get(1) ?: ""
        } else if (linea.isNotEmpty() && !linea.startsWith("#") && nombre != null) {
            listaTemporal.add(Canal(nombre = nombre, url = linea.trim(), logoUrl = logo ?: ""))
            nombre = null
            logo = null
        }
    }
    return listaTemporal
}

object LogManager {
    val logs = mutableStateListOf<String>()
    val logNotifier = mutableStateOf(0)

    fun add(message: String) {
        try {
            val calendar = Calendar.getInstance()
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val horaFormateada = sdf.format(calendar.time)
            logs.add("$horaFormateada - $message")
            if (logs.size > 50) logs.removeAt(0)
            logNotifier.value++
        } catch (e: Exception) {
            logs.add("Log error: ${e.message}")
        }
    }
}

suspend fun descargarM3u(url: String): String = suspendCancellableCoroutine { continuation ->
    val client = OkHttpClient()
    val request = Request.Builder().url(url.trim()).build()
    val call = client.newCall(request)

    continuation.invokeOnCancellation { call.cancel() }

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) {
                    continuation.resumeWithException(IOException("Error de servidor: ${response.code}"))
                } else {
                    val body = response.body?.string() ?: ""
                    continuation.resume(body)
                }
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayChannel: (List<Canal>, Int) -> Unit,
    onOpenXtreamCode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("NeawPrefs", Context.MODE_PRIVATE) }

    var urlInput by remember { mutableStateOf("") }
    var buscarTexto by remember { mutableStateOf("") }
    val canalesCompletos = remember { mutableStateListOf<Canal>() }
    val canalesMostrar = remember { mutableStateListOf<Canal>() }
    
    val scrollState = rememberLazyGridState()
    val itemsPorPagina = 50

    var isLoading by remember { mutableStateOf(false) }
    var mostrarLogs by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var mostrarDisclaimer by remember { mutableStateOf(false) }

    var lastClickedIndex by rememberSaveable { mutableStateOf(0) }

    var tiempoActual by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val calendar = Calendar.getInstance()
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            tiempoActual = sdf.format(calendar.time)
            delay(1000L)
        }
    }

    fun cargarMas() {
        if (canalesMostrar.size < canalesCompletos.size && !isLoading) {
            val inicio = canalesMostrar.size
            val fin = if (inicio + itemsPorPagina > canalesCompletos.size) canalesCompletos.size else inicio + itemsPorPagina
            canalesMostrar.addAll(canalesCompletos.subList(inicio, fin))
        }
    }

    LaunchedEffect(Unit) {
        val disclaimerVisto = prefs.getBoolean("disclaimer_visto", false)
        if (!disclaimerVisto) {
            mostrarDisclaimer = true
        }

        val ultimaUrl = prefs.getString("user_url", "") ?: ""
        urlInput = ultimaUrl

        if (ultimaUrl.isNotEmpty()) {
            isLoading = true
            LogManager.add("Descargando: $ultimaUrl")
            scope.launch {
                try {
                    val m3uBody = descargarM3u(ultimaUrl)
                    val lista = parsearLista(m3uBody)
                    canalesCompletos.clear()
                    canalesCompletos.addAll(lista)
                    
                    val cantidadNecesaria = maxOf(itemsPorPagina, ((lastClickedIndex / itemsPorPagina) + 1) * itemsPorPagina)
                    val limiteInicial = minOf(cantidadNecesaria, canalesCompletos.size)

                    canalesMostrar.clear()
                    canalesMostrar.addAll(canalesCompletos.take(limiteInicial))

                } catch (e: Exception) {
                    LogManager.add("Error: ${e.toString()}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && buscarTexto.isEmpty() && lastIndex >= canalesMostrar.size - 15) {
                    cargarMas()
                }
            }
    }

    val canalesAListar = if (buscarTexto.isEmpty()) {
        canalesMostrar
    } else {
        canalesCompletos.filter { it.nombre.contains(buscarTexto, ignoreCase = true) }
    }

    MaterialTheme(colorScheme = DarkColorScheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(ColorOscuroFondo),
            containerColor = ColorOscuroFondo,
            topBar = {
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Neaw Stream",
                                color = Color.White,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                            Text(
                                text = tiempoActual,
                                color = ColorDorado,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { buscando = !buscando }) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = if (buscando) ColorDorado else Color.White)
                        }
                        IconButton(onClick = { mostrarLogs = !mostrarLogs }) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = if (mostrarLogs) ColorDorado else Color.White)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Pegar enlace M3U aquí...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = ColorBlancoAlpha10,
                                unfocusedContainerColor = ColorBlancoAlpha5,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { 
                                if (urlInput.isNotEmpty()) {
                                    isLoading = true
                                    LogManager.add("Descargando: $urlInput")
                                    scope.launch {
                                        try {
                                            prefs.edit().putString("user_url", urlInput).apply()
                                            val m3uBody = descargarM3u(urlInput)
                                            val lista = parsearLista(m3uBody)
                                            canalesCompletos.clear()
                                            canalesCompletos.addAll(lista)
                                            canalesMostrar.clear()
                                            canalesMostrar.addAll(canalesCompletos.take(itemsPorPagina))
                                        } catch (e: Exception) {
                                            LogManager.add("Error: ${e.toString()}")
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDorado)
                        ) {
                            Text("CARGAR", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onOpenXtreamCode() },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDorado)
                        ) {
                            Text("Xtream Code", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (buscando) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = buscarTexto,
                                onValueChange = { buscarTexto = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Buscar canal...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = ColorBlancoAlpha10,
                                    unfocusedContainerColor = ColorBlancoAlpha5,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(10),
                        state = scrollState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = canalesAListar.size,
                            key = { index -> canalesAListar[index].url + index }
                        ) { i ->
                            val canal = canalesAListar[i]
                            val isSelected = (i == lastClickedIndex)
                            val focusRequester = remember { FocusRequester() }

                            LaunchedEffect(isSelected) {
                                if (isSelected && canalesMostrar.size > i && buscarTexto.isEmpty()) {
                                    try {
                                        scrollState.scrollToItem(i)
                                        focusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                            }

                            CanalItem(
                                canal = canal,
                                accentColor = ColorDorado,
                                focusRequester = focusRequester,
                                onClick = {
                                    lastClickedIndex = i
                                    val indexReal = canalesCompletos.indexOf(canal)
                                    if (indexReal != -1) {
                                        onPlayChannel(canalesCompletos, indexReal)
                                    }
                                }
                            )
                        }
                    }
                }

                if (mostrarLogs) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 20.dp, end = 20.dp)
                            .size(width = 250.dp, height = 200.dp)
                            .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp))
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            items(LogManager.logs) { log ->
                                Text(text = log, color = Color.Green, fontSize = 9.sp)
                            }
                        }
                    }
                }

                if (mostrarDisclaimer) {
                    AlertDialog(
                        onDismissRequest = {
                            prefs.edit().putBoolean("disclaimer_visto", true).apply()
                            mostrarDisclaimer = false
                        },
                        containerColor = ColorSuperficieOscura,
                        title = {
                            Text(
                                text = "Aviso Legal / Disclaimer",
                                color = ColorDorado,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "\"NeawStreamV-player\" es un reproductor multimedia de propósito general. El software no contiene, proporciona, ni preinstala ningún tipo de lista de canales, contenido multimedia, o enlaces a fuentes externas.\n\nEl usuario es el único responsable de la legalidad, propiedad y uso de los contenidos que decida cargar o reproducir mediante la aplicación. El autor no apoya ni fomenta el uso de material protegido por derechos de autor sin la debida licencia.\n\n¡Gracias por usar NeawStreamV-player!",
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    prefs.edit().putBoolean("disclaimer_visto", true).apply()
                                    mostrarDisclaimer = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDorado)
                            ) {
                                Text("Aceptar y Continuar", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CanalItem(
    canal: Canal,
    accentColor: Color,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .focusRequester(focusRequester)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) accentColor else ColorBlancoAlpha10,
                shape = RoundedCornerShape(12.dp)
            )
            .graphicsLayer {
                val scale = if (isFocused) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            } 
            .focusable(interactionSource = interactionSource) 
            .clickable(
                interactionSource = interactionSource,
                indication = null 
            ) {
                onClick()
            }
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (canal.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = canal.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .graphicsLayer(alpha = if (isFocused) 0.35f else 0.18f), 
                        contentScale = ContentScale.Fit
                    )
                }

                Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = canal.nombre,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis, 
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}