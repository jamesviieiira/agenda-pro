package br.com.agendapro

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val ptBR = Locale("pt", "BR")
val dmy: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

val STATUS_LABEL = mapOf(
    "AGENDADO" to "Agendado", "CONFIRMADO" to "Confirmado",
    "CONCLUIDO" to "Concluído", "CANCELADO" to "Cancelado"
)
val PAY_METHODS = listOf("Dinheiro", "Pix", "Débito", "Crédito")

fun money(v: Double): String = NumberFormat.getCurrencyInstance(ptBR).format(v)
fun toMin(t: String): Int { val p = t.split(":"); return p[0].trim().toInt() * 60 + p[1].trim().toInt() }
fun fmtMin(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
fun parseMoney(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun openWhats(ctx: Context, phone: String, text: String) {
    var d = phone.filter { it.isDigit() }
    if (d.isEmpty()) {
        Toast.makeText(ctx, "Cliente sem telefone", Toast.LENGTH_SHORT).show()
        return
    }
    if (d.length <= 11) d = "55$d"
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d?text=" + Uri.encode(text))))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Não foi possível abrir o WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(i, "Compartilhar"))
}

fun easterDate(y: Int): LocalDate {
    val a = y % 19; val b = y / 100; val c = y % 100
    val d = b / 4; val e = b % 4; val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4; val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(y, month, day)
}

fun nationalHolidays(y: Int): List<Holiday> {
    val easter = easterDate(y)
    fun h(d: LocalDate, n: String) = Holiday(d.toString(), n)
    return listOf(
        h(LocalDate.of(y, 1, 1), "Confraternização Universal"),
        h(easter.minusDays(48), "Carnaval (segunda)"),
        h(easter.minusDays(47), "Carnaval (terça)"),
        h(easter.minusDays(2), "Sexta-feira Santa"),
        h(LocalDate.of(y, 4, 21), "Tiradentes"),
        h(LocalDate.of(y, 5, 1), "Dia do Trabalho"),
        h(easter.plusDays(60), "Corpus Christi"),
        h(LocalDate.of(y, 9, 7), "Independência"),
        h(LocalDate.of(y, 10, 12), "Nossa Sra. Aparecida"),
        h(LocalDate.of(y, 11, 2), "Finados"),
        h(LocalDate.of(y, 11, 15), "Proclamação da República"),
        h(LocalDate.of(y, 11, 20), "Consciência Negra"),
        h(LocalDate.of(y, 12, 25), "Natal")
    )
}

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun Picker(label: String, options: List<Pair<Long, String>>, selected: Long?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}

@Composable
fun DateButton(date: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    OutlinedButton(modifier = modifier, onClick = {
        val d = LocalDate.parse(date)
        DatePickerDialog(ctx, { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) },
            d.year, d.monthValue - 1, d.dayOfMonth).show()
    }) { Text(LocalDate.parse(date).format(dmy)) }
}

@Composable
fun TimeButton(time: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    OutlinedButton(modifier = modifier, onClick = {
        val m = toMin(time)
        TimePickerDialog(ctx, { _, h, min -> onChange("%02d:%02d".format(h, min)) }, m / 60, m % 60, true).show()
    }) { Text(time) }
}

@Composable
fun ItemRow(
    title: String, sub: String, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?,
    extra: @Composable () -> Unit = {}
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            extra()
            if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Excluir") }
        }
    }
}

@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
