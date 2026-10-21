package br.com.agendapro

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle

// ---------------- Expediente e feriados ----------------
@Composable
fun HoursPage(vm: AppVm) {
    val holidays by vm.holidays.collectAsState()
    var addHoliday by remember { mutableStateOf(false) }
    val year = LocalDate.now().year

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(
                "Turnos separados por vírgula, ex.: 09:00-12:00,14:00-18:00. Deixe vazio para dia fechado.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items((1..7).toList()) { d ->
            var text by remember(d) { mutableStateOf(vm.hours(d)) }
            OutlinedTextField(
                text, { text = it; vm.setHours(d, it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(DayOfWeek.of(d).getDisplayName(JTextStyle.FULL, ptBR)) }
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Feriados e dias bloqueados", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { addHoliday = true }) { Text("+ Dia") }
                TextButton(onClick = { vm.addHolidays(nationalHolidays(year)) }) { Text("Nacionais $year") }
                TextButton(onClick = { vm.addHolidays(nationalHolidays(year + 1)) }) { Text("Nacionais ${year + 1}") }
            }
        }
        items(holidays, key = { it.date }) { h ->
            ItemRow(h.name, LocalDate.parse(h.date).format(dmy), null, { vm.delete(h) })
        }
    }

    if (addHoliday) {
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var name by remember { mutableStateOf("") }
        FormDialog("Bloquear dia", { addHoliday = false }, onSave = {
            if (name.isNotBlank()) { vm.save(Holiday(date, name.trim())); addHoliday = false }
        }) {
            DateButton(date, { date = it }, Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Motivo") }, singleLine = true)
        }
    }
}

// ---------------- Taxas e lembretes ----------------
@Composable
fun SettingsPage(vm: AppVm) {
    val ctx = LocalContext.current
    var deb by remember { mutableStateOf(vm.ps("fee_deb", "0")) }
    var cred by remember { mutableStateOf(vm.ps("fee_cred", "0")) }
    var remOn by remember { mutableStateOf(vm.ps("rem_on", "0") == "1") }
    var remTime by remember { mutableStateOf(vm.ps("rem_time", "18:00")) }
    var saved by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Taxas de cartão (%)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(deb, { deb = it; saved = false }, label = { Text("Débito") }, singleLine = true)
        OutlinedTextField(cred, { cred = it; saved = false }, label = { Text("Crédito") }, singleLine = true)
        Button(onClick = {
            vm.setPs("fee_deb", deb); vm.setPs("fee_cred", cred); saved = true
        }) { Text("Salvar taxas") }
        if (saved) Text("Salvo!")

        Spacer(Modifier.height(12.dp))
        Text("Lembrete diário", style = MaterialTheme.typography.titleMedium)
        Text("Receba uma notificação com os atendimentos do dia seguinte.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = remOn, onCheckedChange = { on ->
                remOn = on
                vm.setPs("rem_on", if (on) "1" else "0")
                if (on) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    ReminderScheduler.schedule(ctx, remTime)
                } else ReminderScheduler.cancel(ctx)
            })
            Text("  Ativar notificação")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Horário:")
            TimeButton(remTime, { t ->
                remTime = t
                vm.setPs("rem_time", t)
                if (remOn) ReminderScheduler.schedule(ctx, t)
            })
        }
    }
}

// ---------------- Backup ----------------
@Composable
fun BackupPage(vm: AppVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = vm.exportJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }.isSuccess
            }
            Toast.makeText(ctx, if (ok) "Backup salvo!" else "Falha ao salvar", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "O backup é um arquivo único com clientes, agenda, financeiro e configurações. " +
                "Ao salvar, escolha uma pasta do Google Drive, Dropbox etc. para ter cópia na nuvem.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = { export.launch("agenda-backup-${LocalDate.now()}.json") }) { Text("Exportar backup") }
        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restaurar backup") }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restaurar backup?") },
            text = { Text("Isso substitui TODOS os dados atuais pelos do arquivo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            runCatching { ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
                        }
                        val ok = text != null && vm.importJson(text)
                        Toast.makeText(ctx, if (ok) "Dados restaurados!" else "Arquivo inválido", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancelar") } }
        )
    }
}
