package com.neawstreamtv.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.neawstreamtv.tv.ui.theme.NeawStreamTVTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NeawStreamTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    // Estados para controlar la navegación en vivo
                    var activeScreen by remember { mutableStateOf("home") }
                    var selectedList by remember { mutableStateOf<List<Canal>>(emptyList()) }
                    var selectedIndex by remember { mutableStateOf(0) }

                    when (activeScreen) {
                        "home" -> HomeScreen(
                            onPlayChannel = { lista, indice ->
                                selectedList = lista
                                selectedIndex = indice
                                activeScreen = "player"
                            },
                            onOpenXtreamCode = {
                                activeScreen = "xtream" // O la lógica que uses para abrir esa sección
                            }
                        )
                        "player" -> PlayerScreen(
                            listaCanales = selectedList,
                            indiceInicial = selectedIndex,
                            onBack = {
                                activeScreen = "home"
                            }
                        )
                        // C. Pantalla de Xtream Codes (si aplica en tu flujo)
                        "xtream" -> {
                            activeScreen = "home"
                        }
                    }
                }
            }
        }
    }
}