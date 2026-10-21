package br.com.agendapro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun MoreScreen(vm: AppVm) {
    var page by rememberSaveable { mutableStateOf("") }
    val back = { page = "" }
    when (page) {
        "wait" -> SubPage("Lista de espera", back) { WaitlistPage(vm) }
        "tomorrow" -> SubPage("Lembretes de amanhã", back) { TomorrowPage(vm) }
        "birthdays" -> SubPage("Aniversariantes", back) { BirthdaysPage(vm) }
        "messages" -> SubPage("Mensagens", back) { MessagesPage(vm) }
        "packages" -> SubPage("Pacotes de sessões", back) { PackagesPage(vm) }
        "loyalty" -> SubPage("Cartão fidelidade", back) { LoyaltyPage(vm) }
        "staff" -> SubPage("Equipe e comissões", back) { StaffPage(vm) }
        "hours" -> SubPage("Expediente e feriados", back) { HoursPage(vm) }
        "settings" -> SubPage("Taxas e lembretes", back) { SettingsPage(vm) }
        "reports" -> SubPage("Relatórios", back) { ReportsPage(vm) }
        "backup" -> SubPage("Backup", back) { BackupPage(vm) }
        else -> {
            val items = listOf(
                Triple("wait", "Lista de espera", "Clientes aguardando vaga; converta em agendamento"),
                Triple("tomorrow", "Lembretes de amanhã", "Envie o lembrete por WhatsApp com um toque"),
                Triple("birthdays", "Aniversariantes", "Parabenize os clientes do mês"),
                Triple("messages", "Mensagens", "Textos de confirmação, lembrete e aniversário"),
                Triple("packages", "Pacotes de sessões", "Venda e controle de sessões"),
                Triple("loyalty", "Cartão fidelidade", "Selos e prêmios por atendimento"),
                Triple("staff", "Equipe e comissões", "Colaboradores e % de comissão"),
                Triple("hours", "Expediente e feriados", "Turnos por dia e dias bloqueados"),
                Triple("settings", "Taxas e lembretes", "Taxas de cartão e notificação diária"),
                Triple("reports", "Relatórios", "Ranking, ticket médio, horários e comissões"),
                Triple("backup", "Backup", "Exportar e restaurar seus dados")
            )
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { (key, title, desc) ->
                    Card(Modifier.fillMaxWidth().clickable { page = key }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WaitlistPage(vm: AppVm) {
    val clients by vm.clients.collectAsState()
    val services by vm.services.collectAsState()
    val wait by vm.waitlist.collectAsState()
    var addWait by remember { mutableStateOf(false) }
    var convert by remember { mutableStateOf<WaitItem?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TextButton(onClick = { addWait = true }) { Text("+ Adicionar à lista de espera") } }
        if (wait.isEmpty()) item { Text("Lista vazia.", Modifier.padding(12.dp)) }
        items(wait, key = { it.id }) { w ->
            val c = clients.firstOrNull { it.id == w.clientId }
            ItemRow(c?.name ?: "(removido)", w.note, onEdit = { convert = w }, onDelete = { vm.delete(w) })
        }
        if (wait.isNotEmpty()) item { Text("Use o lápis para converter em agendamento.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
    }

    if (addWait) {
        var cid by remember { mutableStateOf<Long?>(null) }
        var note by remember { mutableStateOf("") }
        FormDialog("Lista de espera", { addWait = false }, onSave = {
            cid?.let { vm.save(WaitItem(clientId = it, note = note)); addWait = false }
        }) {
            Picker("Cliente", clients.map { it.id to it.name }, cid) { cid = it }
            OutlinedTextField(note, { note = it }, label = { Text("Observação (dia/horário desejado)") })
        }
    }
    convert?.let { w ->
        ApptDialog(
            vm, Appointment(clientId = w.clientId, serviceId = 0, date = LocalDate.now().toString(), time = "09:00"),
            clients, services, onDismiss = { convert = null }, onSaved = { vm.delete(w) }
        )
    }
}

@Composable
fun TomorrowPage(vm: AppVm) {
    val ctx = LocalContext.current
    val list by vm.tomorrow.collectAsState()
    val clients by vm.clients.collectAsState()
    val services by vm.services.collectAsState()
    val active = list.filter { it.status != "CANCELADO" }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Amanhã (${LocalDate.now().plusDays(1).format(dmy)}): ${active.size} atendimento(s)", style = MaterialTheme.typography.titleMedium) }
        items(active, key = { it.id }) { a ->
            val c = clients.firstOrNull { it.id == a.clientId }
            val s = services.firstOrNull { it.id == a.serviceId }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${a.time} · ${c?.name ?: "?"} · ${s?.name ?: "?"}", Modifier.weight(1f))
                    IconButton(onClick = { openWhats(ctx, c?.phone ?: "", vm.fill("reminder", c, s, a)) }) {
                        Icon(Icons.Default.Send, "Enviar lembrete")
                    }
                }
            }
        }
    }
}

@Composable
fun BirthdaysPage(vm: AppVm) {
    val ctx = LocalContext.current
    val clients by vm.clients.collectAsState()
    val thisMonth = LocalDate.now().monthValue
    val birthdays = clients.filter { it.birthday.split("/").getOrNull(1)?.trim()?.toIntOrNull() == thisMonth }
        .sortedBy { it.birthday.substringBefore("/").trim().toIntOrNull() ?: 0 }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (birthdays.isEmpty()) item { Text("Nenhum aniversariante neste mês.") }
        items(birthdays, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.name} · ${c.birthday}", Modifier.weight(1f))
                    IconButton(onClick = { openWhats(ctx, c.phone, vm.fill("birthday", c, null, null)) }) {
                        Icon(Icons.Default.Send, "Parabenizar")
                    }
                }
            }
        }
    }
}

@Composable
fun MessagesPage(vm: AppVm) {
    var confirm by remember { mutableStateOf(vm.tpl("confirm")) }
    var reminder by remember { mutableStateOf(vm.tpl("reminder")) }
    var birthday by remember { mutableStateOf(vm.tpl("birthday")) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Variáveis: {cliente} {servico} {data} {hora}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(confirm, { confirm = it; saved = false }, label = { Text("Confirmação") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(reminder, { reminder = it; saved = false }, label = { Text("Lembrete") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(birthday, { birthday = it; saved = false }, label = { Text("Aniversário") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            vm.setTpl("confirm", confirm); vm.setTpl("reminder", reminder); vm.setTpl("birthday", birthday)
            saved = true
        }) { Text("Salvar") }
        if (saved) Text("Salvo!")
    }
}
