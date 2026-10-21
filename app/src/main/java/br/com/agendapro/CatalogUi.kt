package br.com.agendapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CatalogScreen(vm: AppVm) {
    var tab by remember { mutableIntStateOf(0) }
    val services by vm.services.collectAsState()
    val products by vm.products.collectAsState()
    var editS by remember { mutableStateOf<Service?>(null) }
    var newS by remember { mutableStateOf(false) }
    var editP by remember { mutableStateOf<Product?>(null) }
    var newP by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, content = { Text("Serviços", Modifier.padding(12.dp)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, content = { Text("Produtos", Modifier.padding(12.dp)) })
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
                if (tab == 0) {
                    items(services, key = { it.id }) { s ->
                        ItemRow(s.name, "${money(s.price)} · ${s.durationMin} min", { editS = s }, { vm.delete(s) })
                    }
                } else {
                    items(products, key = { it.id }) { p ->
                        ItemRow(
                            p.name,
                            "Venda ${money(p.price)} · Custo ${money(p.cost)} · Comissão ${p.commissionPct}%",
                            { editP = p }, { vm.delete(p) }
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = { if (tab == 0) newS = true else newP = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, "Novo") }
        }
    }
    if (newS) ServiceDialog(vm, null) { newS = false }
    editS?.let { ServiceDialog(vm, it) { editS = null } }
    if (newP) ProductDialog(vm, null) { newP = false }
    editP?.let { ProductDialog(vm, it) { editP = null } }
}

@Composable
fun ServiceDialog(vm: AppVm, initial: Service?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var dur by remember { mutableStateOf((initial?.durationMin ?: 60).toString()) }
    FormDialog(if (initial == null) "Novo serviço" else "Editar serviço", onDismiss, onSave = {
        val p = parseMoney(price)
        val d = dur.toIntOrNull()
        if (name.isNotBlank() && p != null && d != null && d > 0) {
            vm.save((initial ?: Service(name = name, price = p)).copy(name = name.trim(), price = p, durationMin = d))
            onDismiss()
        }
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
        OutlinedTextField(price, { price = it }, label = { Text("Preço (R$)") }, singleLine = true)
        OutlinedTextField(dur, { dur = it.filter(Char::isDigit) }, label = { Text("Duração (min)") }, singleLine = true)
    }
}

@Composable
fun ProductDialog(vm: AppVm, initial: Product?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.toString() ?: "0") }
    var comm by remember { mutableStateOf(initial?.commissionPct?.toString() ?: "0") }
    FormDialog(if (initial == null) "Novo produto" else "Editar produto", onDismiss, onSave = {
        val p = parseMoney(price)
        val c = parseMoney(cost)
        val cm = parseMoney(comm)
        if (name.isNotBlank() && p != null && c != null && cm != null) {
            vm.save((initial ?: Product(name = name, price = p)).copy(name = name.trim(), price = p, cost = c, commissionPct = cm))
            onDismiss()
        }
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
        OutlinedTextField(price, { price = it }, label = { Text("Preço de venda (R$)") }, singleLine = true)
        OutlinedTextField(cost, { cost = it }, label = { Text("Preço de custo (R$)") }, singleLine = true)
        OutlinedTextField(comm, { comm = it }, label = { Text("Comissão (%)") }, singleLine = true)
    }
}
