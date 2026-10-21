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
import java.time.LocalDate

// ---------------- Pacotes ----------------
@Composable
fun PackagesPage(vm: AppVm) {
    val defs by vm.packages.collectAsState()
    val sold by vm.clientPackages.collectAsState()
    val clients by vm.clients.collectAsState()
    val services by vm.services.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var newDef by remember { mutableStateOf(false) }
    var editDef by remember { mutableStateOf<PackageDef?>(null) }
    var selling by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, content = { Text("Modelos", Modifier.padding(12.dp)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, content = { Text("Vendidos", Modifier.padding(12.dp)) })
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
                if (tab == 0) {
                    items(defs, key = { "d${it.id}" }) { p ->
                        ItemRow(
                            p.name,
                            "${services.firstOrNull { it.id == p.serviceId }?.name ?: "(serviço removido)"} · ${p.sessions} sessões · ${money(p.price)}",
                            { editDef = p }, { vm.delete(p) }
                        )
                    }
                } else {
                    items(sold, key = { "s${it.id}" }) { cp ->
                        val left = cp.total - cp.used
                        ItemRow(
                            "${cp.packageName} — ${clients.firstOrNull { it.id == cp.clientId }?.name ?: "(removido)"}",
                            "Restam $left de ${cp.total} · ${money(cp.price)} · ${cp.payMethod} · ${LocalDate.parse(cp.date).format(dmy)}",
                            null, { vm.deletePackage(cp) }
                        ) {
                            if (left > 0) TextButton(onClick = { vm.useSessionManually(cp) }) { Text("Usar sessão") }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { if (tab == 0) newDef = true else selling = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, "Novo") }
        }
    }
    if (newDef) PackageDefDialog(vm, null, services) { newDef = false }
    editDef?.let { PackageDefDialog(vm, it, services) { editDef = null } }
    if (selling) SellPackageDialog(vm, defs, clients) { selling = false }
}

@Composable
fun PackageDefDialog(vm: AppVm, initial: PackageDef?, services: List<Service>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var serviceId by remember { mutableStateOf(initial?.serviceId) }
    var sessions by remember { mutableStateOf(initial?.sessions?.toString() ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    FormDialog(if (initial == null) "Novo pacote" else "Editar pacote", onDismiss, onSave = {
        val sid = serviceId
        val n = sessions.toIntOrNull()
        val p = parseMoney(price)
        if (name.isNotBlank() && sid != null && n != null && n > 0 && p != null) {
            vm.save((initial ?: PackageDef(name = name, serviceId = sid, sessions = n, price = p))
                .copy(name = name.trim(), serviceId = sid, sessions = n, price = p))
            onDismiss()
        }
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome do pacote") }, singleLine = true)
        Picker("Serviço", services.map { it.id to it.name }, serviceId) { serviceId = it }
        OutlinedTextField(sessions, { sessions = it.filter(Char::isDigit) }, label = { Text("Nº de sessões") }, singleLine = true)
        OutlinedTextField(price, { price = it }, label = { Text("Preço do pacote (R$)") }, singleLine = true)
    }
}

@Composable
fun SellPackageDialog(vm: AppVm, defs: List<PackageDef>, clients: List<Client>, onDismiss: () -> Unit) {
    var clientId by remember { mutableStateOf<Long?>(null) }
    var defId by remember { mutableStateOf<Long?>(null) }
    var method by remember { mutableStateOf(0L) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    FormDialog("Vender pacote", onDismiss, onSave = {
        val cid = clientId
        val def = defs.firstOrNull { it.id == defId }
        if (cid != null && def != null) {
            vm.save(ClientPackage(
                clientId = cid, packageName = def.name, serviceId = def.serviceId, total = def.sessions,
                price = def.price, date = date, payMethod = PAY_METHODS[method.toInt()]
            ))
            onDismiss()
        }
    }) {
        Picker("Cliente", clients.map { it.id to it.name }, clientId) { clientId = it }
        Picker("Pacote", defs.map { it.id to "${it.name} (${money(it.price)})" }, defId) { defId = it }
        Picker("Forma de pagamento", PAY_METHODS.mapIndexed { i, m -> i.toLong() to m }, method) { method = it }
        DateButton(date, { date = it }, Modifier.fillMaxWidth())
    }
}

// ---------------- Fidelidade ----------------
@Composable
fun LoyaltyPage(vm: AppVm) {
    val clients by vm.clients.collectAsState()
    val counts by vm.concluded.collectAsState()
    val n by vm.loyaltyN.collectAsState()
    val reward by vm.loyaltyReward.collectAsState()
    var nText by remember { mutableStateOf(n.toString()) }
    var rText by remember { mutableStateOf(reward) }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Regra: cada atendimento concluído vale 1 selo.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(nText, { nText = it.filter(Char::isDigit) }, label = { Text("Selos para ganhar o prêmio") }, singleLine = true)
                    OutlinedTextField(rText, { rText = it }, label = { Text("Prêmio") }, singleLine = true)
                    Button(onClick = { nText.toIntOrNull()?.takeIf { it > 0 }?.let { vm.setLoyalty(it, rText) } }) { Text("Salvar regra") }
                }
            }
        }
        items(clients, key = { it.id }) { c ->
            val total = counts.firstOrNull { it.clientId == c.id }?.n ?: 0
            val pending = (total - c.loyaltyRedeemed * n).coerceAtLeast(0)
            val rewards = pending / n
            val current = pending % n
            ItemRow(
                c.name,
                "Selos: $current/$n" + if (rewards > 0) " · 🎁 $rewards prêmio(s): $reward" else "",
                null, null
            ) {
                if (rewards > 0) Button(onClick = { vm.redeemLoyalty(c) }) { Text("Resgatar") }
            }
        }
    }
}

// ---------------- Equipe ----------------
@Composable
fun StaffPage(vm: AppVm) {
    val staff by vm.staff.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Staff?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
            if (staff.isEmpty()) item { Text("Cadastre colaboradores para atribuir atendimentos e calcular comissões.", Modifier.padding(12.dp)) }
            items(staff, key = { it.id }) { s ->
                ItemRow(s.name, "Comissão: ${s.commissionPct}%", { editing = s }, { vm.delete(s) })
            }
        }
        FloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Novo colaborador")
        }
    }
    if (creating) StaffDialog(vm, null) { creating = false }
    editing?.let { StaffDialog(vm, it) { editing = null } }
}

@Composable
fun StaffDialog(vm: AppVm, initial: Staff?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var pct by remember { mutableStateOf(initial?.commissionPct?.toString() ?: "0") }
    FormDialog(if (initial == null) "Novo colaborador" else "Editar colaborador", onDismiss, onSave = {
        val p = parseMoney(pct)
        if (name.isNotBlank() && p != null) {
            vm.save((initial ?: Staff(name = name)).copy(name = name.trim(), commissionPct = p))
            onDismiss()
        }
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
        OutlinedTextField(pct, { pct = it }, label = { Text("Comissão (%)") }, singleLine = true)
    }
}
