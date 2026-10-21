package br.com.agendapro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

@Composable
fun AgendaScreen(vm: AppVm) {
    val date by vm.selectedDate.collectAsState()
    val appts by vm.dayAppointments.collectAsState()
    val clients by vm.clients.collectAsState()
    val services by vm.services.collectAsState()
    val staff by vm.staff.collectAsState()
    val holidays by vm.holidays.collectAsState()
    val clientPackages by vm.clientPackages.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var actions by remember { mutableStateOf<Appointment?>(null) }
    var showSlots by remember { mutableStateOf(false) }
    var staffFilter by remember { mutableStateOf(0L) }
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val shown = if (staffFilter == 0L) appts else appts.filter { it.staffId == staffFilter }
    val active = shown.filter { it.status != "CANCELADO" }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.selectedDate.value = date.minusDays(1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Anterior") }
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", ptBR)),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { vm.selectedDate.value = date.plusDays(1) }) { Icon(Icons.Default.KeyboardArrowRight, "Próximo") }
                TextButton(onClick = { vm.selectedDate.value = LocalDate.now() }) { Text("Hoje") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).forEach { i ->
                    val d = monday.plusDays(i.toLong())
                    val sel = d == date
                    val fg = if (sel) Color.White else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { vm.selectedDate.value = d }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(d.dayOfWeek.getDisplayName(JTextStyle.SHORT, ptBR).take(3), color = fg, style = MaterialTheme.typography.labelSmall)
                        Text("${d.dayOfMonth}", color = fg, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text(vm.dayNote(date, holidays), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            if (staff.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(0L to "Todos") + staff.map { it.id to it.name }).forEach { (id, name) ->
                        if (staffFilter == id) Button(onClick = { staffFilter = id }) { Text(name) }
                        else OutlinedButton(onClick = { staffFilter = id }) { Text(name) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${active.size} atendimento(s) · ${money(active.sumOf { it.total })}",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { showSlots = true }) { Text("Horários livres") }
            }
            if (shown.isEmpty()) Text("Nenhum agendamento neste dia.", Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(shown, key = { it.id }) { a ->
                    val c = clients.firstOrNull { it.id == a.clientId }
                    val s = services.firstOrNull { it.id == a.serviceId }
                    val st = staff.firstOrNull { it.id == a.staffId }
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { actions = a },
                        colors = CardDefaults.cardColors(
                            containerColor = when (a.status) {
                                "CONFIRMADO" -> Color(0xFFE3F2FD)
                                "CONCLUIDO" -> Color(0xFFE8F5E9)
                                "CANCELADO" -> Color(0xFFEEEEEE)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(64.dp)) {
                                Text(a.time, style = MaterialTheme.typography.titleMedium)
                                Text(fmtMin(toMin(a.time) + a.durationMin), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(c?.name ?: "(cliente removido)", style = MaterialTheme.typography.titleMedium)
                                Text(s?.name ?: "(serviço removido)", style = MaterialTheme.typography.bodyMedium)
                                if (a.productQty > 0) Text(
                                    "+ ${a.productQty}x ${vm.products.value.firstOrNull { it.id == a.productId }?.name ?: "produto"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    (STATUS_LABEL[a.status] ?: a.status) +
                                        (if (a.paid) " · Pago (${a.payMethod})" else "") +
                                        (if (st != null) " · ${st.name}" else ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(if (a.status == "CANCELADO") "—" else money(a.total), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showNew = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Novo agendamento")
        }
    }

    if (showNew) ApptDialog(vm, null, clients, services, onDismiss = { showNew = false })
    editing?.let { ApptDialog(vm, it, clients, services, onDismiss = { editing = null }) }
    if (showSlots) FreeSlotsDialog(vm, services) { showSlots = false }
    actions?.let { a ->
        val c = clients.firstOrNull { it.id == a.clientId }
        val s = services.firstOrNull { it.id == a.serviceId }
        val ctx = LocalContext.current
        val close = { actions = null }
        val pkgs = clientPackages.filter { it.clientId == a.clientId && it.serviceId == a.serviceId && it.total - it.used > 0 }
        AlertDialog(
            onDismissRequest = { close() },
            title = { Text(c?.name ?: "Agendamento") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    @Composable fun Act(label: String, onClick: () -> Unit) =
                        TextButton(onClick = { onClick(); close() }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    Act("Enviar confirmação (WhatsApp)") { openWhats(ctx, c?.phone ?: "", vm.fill("confirm", c, s, a)) }
                    Act("Enviar lembrete (WhatsApp)") { openWhats(ctx, c?.phone ?: "", vm.fill("reminder", c, s, a)) }
                    Act("Marcar como confirmado") { vm.save(a.copy(status = "CONFIRMADO")) }
                    Act("Marcar como concluído") { vm.save(a.copy(status = "CONCLUIDO")) }
                    if (!a.paid) {
                        Text("Receber pagamento:", Modifier.padding(start = 12.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium)
                        PAY_METHODS.chunked(2).forEach { pair ->
                            Row {
                                pair.forEach { m ->
                                    TextButton(onClick = { vm.pay(a, m); close() }) { Text(m) }
                                }
                            }
                        }
                        pkgs.forEach { cp ->
                            Act("Usar sessão: ${cp.packageName} (restam ${cp.total - cp.used})") { vm.usePackage(a, cp) }
                        }
                    } else {
                        Act("Desfazer pagamento") { vm.undoPayment(a) }
                    }
                    Act("Editar / remarcar") { editing = a }
                    if (a.status != "CANCELADO") Act("Cancelar agendamento") { vm.cancel(a) }
                    Act("Excluir") { vm.delete(a) }
                }
            },
            confirmButton = { TextButton(onClick = { close() }) { Text("Fechar") } }
        )
    }
}

@Composable
fun ApptDialog(
    vm: AppVm, initial: Appointment?, clients: List<Client>, services: List<Service>,
    onDismiss: () -> Unit, onSaved: () -> Unit = {}
) {
    val staff by vm.staff.collectAsState()
    val products by vm.products.collectAsState()
    var clientId by remember { mutableStateOf(initial?.clientId?.takeIf { it != 0L }) }
    var serviceId by remember { mutableStateOf(initial?.serviceId?.takeIf { it != 0L }) }
    var staffId by remember { mutableStateOf(initial?.staffId ?: 0L) }
    var productId by remember { mutableStateOf(initial?.productId ?: 0L) }
    var qty by remember { mutableStateOf((initial?.productQty?.takeIf { it > 0 } ?: 1).toString()) }
    var date by remember { mutableStateOf(initial?.date ?: vm.selectedDate.value.toString()) }
    var time by remember { mutableStateOf(initial?.time ?: "09:00") }
    var duration by remember { mutableStateOf((initial?.durationMin ?: 60).toString()) }
    var price by remember { mutableStateOf(initial?.price?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var warned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FormDialog(if (initial == null || initial.id == 0L) "Novo agendamento" else "Editar agendamento", onDismiss, onSave = {
        val cid = clientId
        val sid = serviceId
        val dur = duration.toIntOrNull()
        val pr = parseMoney(price)
        when {
            cid == null -> error = "Escolha o cliente (cadastre em Clientes)"
            sid == null -> error = "Escolha o serviço (cadastre em Catálogo)"
            dur == null || dur <= 0 -> error = "Duração inválida"
            pr == null -> error = "Valor inválido"
            else -> {
                val prod = products.firstOrNull { it.id == productId }
                val q = qty.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val appt = (initial ?: Appointment(clientId = cid, serviceId = sid, date = date, time = time)).copy(
                    clientId = cid, serviceId = sid, date = date, time = time,
                    durationMin = dur, price = pr, notes = notes, staffId = staffId,
                    productId = prod?.id ?: 0L, productQty = if (prod != null) q else 0,
                    productTotal = if (prod != null) prod.price * q else 0.0,
                    productCost = if (prod != null) prod.cost * q else 0.0
                )
                scope.launch {
                    val w = vm.warnings(appt)
                    if (!warned && w.isNotEmpty()) {
                        warned = true
                        error = w.joinToString("; ") + ". Toque em Salvar de novo para confirmar mesmo assim."
                    } else {
                        vm.save(appt).join()
                        onSaved()
                        onDismiss()
                    }
                }
            }
        }
    }) {
        Picker("Cliente", clients.map { it.id to it.name }, clientId) { clientId = it }
        Picker("Serviço", services.map { it.id to it.name }, serviceId) { id ->
            serviceId = id
            services.firstOrNull { it.id == id }?.let { s ->
                duration = s.durationMin.toString()
                if (price.isBlank()) price = s.price.toString()
            }
            warned = false
        }
        if (staff.isNotEmpty()) {
            Picker("Profissional", listOf(0L to "Sem profissional") + staff.map { it.id to it.name }, staffId) { staffId = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateButton(date, { date = it; warned = false }, Modifier.weight(1f))
            TimeButton(time, { time = it; warned = false }, Modifier.weight(1f))
        }
        OutlinedTextField(duration, { duration = it.filter(Char::isDigit); warned = false }, label = { Text("Duração (min)") }, singleLine = true)
        OutlinedTextField(price, { price = it }, label = { Text("Valor do serviço (R$)") }, singleLine = true)
        if (products.isNotEmpty()) {
            Picker("Produto", listOf(0L to "Sem produto") + products.map { it.id to "${it.name} (${money(it.price)})" }, productId) { productId = it }
            if (productId != 0L) OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text("Quantidade") }, singleLine = true)
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("Observações") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun FreeSlotsDialog(vm: AppVm, services: List<Service>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf("7") }
    var dur by remember { mutableStateOf("60") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horários livres") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gere uma lista dos horários vagos para enviar aos clientes (WhatsApp, redes etc.).", style = MaterialTheme.typography.bodySmall)
                if (services.isNotEmpty()) Picker("Usar duração de um serviço", services.map { it.id to it.name }, null) { id ->
                    services.firstOrNull { it.id == id }?.let { dur = it.durationMin.toString() }
                }
                OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Próximos dias") }, singleLine = true)
                OutlinedTextField(dur, { dur = it.filter(Char::isDigit) }, label = { Text("Duração (min)") }, singleLine = true)
                Button(onClick = {
                    val d = days.toIntOrNull()?.coerceIn(1, 30) ?: 7
                    val m = dur.toIntOrNull()?.coerceAtLeast(5) ?: 60
                    scope.launch { text = vm.freeSlotsText(d, m) }
                }) { Text("Gerar") }
                if (text.isNotBlank()) {
                    Text(text)
                    Button(onClick = { shareText(ctx, text) }) { Text("Compartilhar") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
