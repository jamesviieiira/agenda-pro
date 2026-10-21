package br.com.agendapro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences("agenda", MODE_PRIVATE)
        if (p.getString("rem_on", "0") == "1") ReminderScheduler.schedule(this, p.getString("rem_time", "18:00") ?: "18:00")
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF6A3DE8))) {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppVm = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        "Agenda" to Icons.Default.Event,
        "Clientes" to Icons.Default.People,
        "Financeiro" to Icons.Default.AttachMoney,
        "Catálogo" to Icons.Default.Inventory2,
        "Mais" to Icons.Default.MoreHoriz
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> AgendaScreen(vm)
                1 -> ClientsScreen(vm)
                2 -> FinanceScreen(vm)
                3 -> CatalogScreen(vm)
                else -> MoreScreen(vm)
            }
        }
    }
}
