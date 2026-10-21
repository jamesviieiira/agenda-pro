package br.com.agendapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FinanceScreen(vm: AppVm) {
    val ym by vm.month.collectAsState()
    val appts by vm.monthAppointments.collectAsState()
    val allExpenses by vm.expenses.collectAsState()
    val services by vm.services.collectAsState()
    val staff by vm.staff.collectAsState()
    val products by vm.products.collectAsState()
    val sold by vm.clientPackages.collectAsState()
    val goal by vm.goal.collectAsState()
    var newExpense by remember { mutableStateOf(false) }
    var editExpense by remember { mutableStateOf<Expense?>(null) }
    var goalDialog by remember { mutableStateOf(false) }

    val expenses = expensesOf(ym, allExpenses)
    val fin = computeFin(ym, appts, allExpenses, sold, staff, products) { vm.fee(it) }
    val paid = appts.filter { it.status != "CANCELADO" && it.paid }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.month.value = ym.minusMonths(1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Mês anterior") }
                    Text(
                        ym.atDay(1).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)),
                        Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { vm.month.value = ym.plusMonths(1) }) { Icon(Icons.Default.KeyboardArrowRight, "Próximo mês") }
                }
            }
            item { SummaryCard("Receita recebida", money(fin.received)) }
            item { SummaryCard("  • venda de pacotes", money(fin.packageSales)) }
            item { SummaryCard("A receber", money(fin.pending)) }
            item { SummaryCard("Taxas de cartão", money(fin.fees)) }
            item { SummaryCard("Comissões", money(fin.commissions)) }
            item { SummaryCard("Custo de produtos", money(fin.productCost)) }
            item { SummaryCard("Despesas", money(fin.expenses)) }
            item { SummaryCard("Lucro líquido", money(fin.net)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Meta do mês", style = MaterialTheme.typography.titleMedium)
                        if (goal > 0) {
                            LinearProgressIndicator(progress = { (fin.received / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("${money(fin.received)} de ${money(goal)} (${(fin.received / goal * 100).toInt()}%)")
                        } else Text("Nenhuma meta definida")
                        TextButton(onClick = { goalDialog = true }) { Text("Definir meta") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Por serviço", style = MaterialTheme.typography.titleMedium)
                        val byService = paid.groupBy { it.serviceId }
                        if (byService.isEmpty()) Text("Sem recebimentos no mês")
                        byService.forEach { (sid, list) ->
                            Text("${services.firstOrNull { it.id == sid }?.name ?: "(removido)"}: ${list.size}x · ${money(list.sumOf { it.total })}")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Por forma de pagamento", style = MaterialTheme.typography.titleMedium)
                        paid.groupBy { it.payMethod }.forEach { (m, list) ->
                            Text("$m: ${money(list.sumOf { it.total })}")
                        }
                    }
                }
            }
            item { Text("Despesas do mês", style = MaterialTheme.typography.titleMedium) }
            items(expenses, key = { it.id }) { e ->
                ItemRow(
                    e.description,
                    "${money(e.amount)} · ${LocalDate.parse(e.date).format(dmy)}" + if (e.recurringMonthly) " · fixa mensal" else "",
                    { editExpense = e }, { vm.delete(e) }
                )
            }
        }
        FloatingActionButton(onClick = { newExpense = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Nova despesa")
        }
    }
    if (newExpense) ExpenseDialog(vm, null) { newExpense = false }
    editExpense?.let { ExpenseDialog(vm, it) { editExpense = null } }
    if (goalDialog) {
        var text by remember { mutableStateOf(if (goal > 0) goal.toString() else "") }
        FormDialog("Meta mensal (R$)", { goalDialog = false }, onSave = {
            parseMoney(text)?.let { vm.setGoal(it); goalDialog = false }
        }) { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Valor") }) }
    }
}

@Composable
private fun SummaryCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ExpenseDialog(vm: AppVm, initial: Expense?, onDismiss: () -> Unit) {
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var recurring by remember { mutableStateOf(initial?.recurringMonthly ?: false) }
    FormDialog(if (initial == null) "Nova despesa" else "Editar despesa", onDismiss, onSave = {
        val v = parseMoney(amount)
        if (desc.isNotBlank() && v != null) {
            vm.save((initial ?: Expense(description = desc, amount = v, date = date)).copy(
                description = desc.trim(), amount = v, date = date, recurringMonthly = recurring))
            onDismiss()
        }
    }) {
        OutlinedTextField(desc, { desc = it }, label = { Text("Descrição") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Valor (R$)") }, singleLine = true)
        DateButton(date, { date = it }, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(recurring, { recurring = it })
            Text("  Despesa fixa mensal")
        }
    }
}
