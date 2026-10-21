package br.com.agendapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

@Composable
fun ReportsPage(vm: AppVm) {
    val ctx = LocalContext.current
    val ym by vm.month.collectAsState()
    val appts by vm.monthAppointments.collectAsState()
    val clients by vm.clients.collectAsState()
    val services by vm.services.collectAsState()
    val staff by vm.staff.collectAsState()
    val products by vm.products.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val sold by vm.clientPackages.collectAsState()

    val valid = appts.filter { it.status != "CANCELADO" }
    val cancelled = appts.count { it.status == "CANCELADO" }
    val done = valid.filter { it.status == "CONCLUIDO" || it.paid }
    val fin = computeFin(ym, appts, expenses, sold, staff, products) { vm.fee(it) }
    val ticket = if (done.isEmpty()) 0.0 else done.sumOf { it.total } / done.size
    val cancelRate = if (appts.isEmpty()) 0 else cancelled * 100 / appts.size

    val lines = mutableListOf<String>()
    fun h(t: String) = lines.add("#$t")
    h("Resumo")
    lines += "Agendamentos: ${appts.size} (concluídos/pagos: ${done.size}, cancelados: $cancelled — $cancelRate%)"
    lines += "Ticket médio: ${money(ticket)}"
    lines += "Receita: ${money(fin.received)} · Lucro líquido: ${money(fin.net)}"
    lines += "Taxas: ${money(fin.fees)} · Comissões: ${money(fin.commissions)} · Custo de produtos: ${money(fin.productCost)}"

    h("Melhores clientes")
    done.groupBy { it.clientId }.entries.sortedByDescending { e -> e.value.sumOf { it.total } }.take(5).forEachIndexed { i, e ->
        lines += "${i + 1}. ${clients.firstOrNull { it.id == e.key }?.name ?: "(removido)"} — ${e.value.size}x · ${money(e.value.sumOf { it.total })}"
    }
    h("Serviços mais realizados")
    done.groupBy { it.serviceId }.entries.sortedByDescending { it.value.size }.take(5).forEachIndexed { i, e ->
        lines += "${i + 1}. ${services.firstOrNull { it.id == e.key }?.name ?: "(removido)"} — ${e.value.size}x · ${money(e.value.sumOf { it.total })}"
    }
    h("Movimento por dia da semana")
    valid.groupBy { LocalDate.parse(it.date).dayOfWeek }.entries.sortedBy { it.key.value }.forEach { e ->
        lines += "${e.key.getDisplayName(JTextStyle.FULL, ptBR)}: ${e.value.size} atendimento(s)"
    }
    h("Horários mais cheios")
    valid.groupBy { it.time.substringBefore(":") }.entries.sortedByDescending { it.value.size }.take(3).forEach { e ->
        lines += "${e.key}h: ${e.value.size} atendimento(s)"
    }
    h("Comissões por colaborador")
    val byStaff = done.filter { it.staffId != 0L }.groupBy { it.staffId }
    if (byStaff.isEmpty()) lines += "Sem atendimentos atribuídos a colaboradores."
    byStaff.forEach { (sid, list) ->
        val st = staff.firstOrNull { it.id == sid }
        val comm = list.sumOf { a ->
            val pc = products.firstOrNull { it.id == a.productId }?.commissionPct ?: 0.0
            a.price * (st?.commissionPct ?: 0.0) / 100.0 + a.productTotal * pc / 100.0
        }
        lines += "${st?.name ?: "(removido)"}: ${list.size} atendimento(s) · produzido ${money(list.sumOf { it.total })} · comissão ${money(comm)}"
    }
    h("Produtos vendidos")
    val withProd = done.filter { it.productQty > 0 }
    if (withProd.isEmpty()) lines += "Nenhuma venda de produto."
    withProd.groupBy { it.productId }.forEach { (pid, list) ->
        val qtd = list.sumOf { it.productQty }
        lines += "${products.firstOrNull { it.id == pid }?.name ?: "(removido)"}: ${qtd}un · vendas ${money(list.sumOf { it.productTotal })} · margem ${money(list.sumOf { it.productTotal - it.productCost })}"
    }

    val title = "Relatório — " + ym.atDay(1).format(DateTimeFormatter.ofPattern("MMMM/yyyy", ptBR))
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.month.value = ym.minusMonths(1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Mês anterior") }
                Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.month.value = ym.plusMonths(1) }) { Icon(Icons.Default.KeyboardArrowRight, "Próximo mês") }
            }
            Button(onClick = {
                shareText(ctx, title + "\n\n" + lines.joinToString("\n") { if (it.startsWith("#")) "\n*${it.drop(1)}*" else it })
            }) { Text("Compartilhar relatório") }
        }
        items(lines) { l ->
            if (l.startsWith("#")) Text(l.drop(1), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            else Text(l)
        }
    }
}
