package br.com.agendapro

import java.time.LocalDate
import java.time.YearMonth

data class Fin(
    val received: Double,
    val pending: Double,
    val fees: Double,
    val commissions: Double,
    val productCost: Double,
    val expenses: Double,
    val packageSales: Double
) {
    val net: Double get() = received - fees - commissions - productCost - expenses
}

fun expensesOf(ym: YearMonth, all: List<Expense>): List<Expense> = all.filter {
    val d = LocalDate.parse(it.date)
    if (it.recurringMonthly) d <= ym.atEndOfMonth() else YearMonth.from(d) == ym
}

fun computeFin(
    ym: YearMonth,
    appts: List<Appointment>,
    expensesAll: List<Expense>,
    sold: List<ClientPackage>,
    staff: List<Staff>,
    products: List<Product>,
    feeOf: (String) -> Double
): Fin {
    val valid = appts.filter { it.status != "CANCELADO" }
    val soldMonth = sold.filter { YearMonth.from(LocalDate.parse(it.date)) == ym }
    fun cashIn(a: Appointment): Double = if (a.payMethod == "Pacote") a.productTotal else a.total
    val paid = valid.filter { it.paid }
    val packageSales: Double = soldMonth.sumOf { it.price }
    val received: Double = paid.sumOf { cashIn(it) } + packageSales
    val pending: Double = valid.filter { !it.paid }.sumOf { it.total }
    val fees: Double = paid.sumOf { cashIn(it) * feeOf(it.payMethod) / 100.0 } +
        soldMonth.sumOf { it.price * feeOf(it.payMethod) / 100.0 }
    val done = valid.filter { it.paid || it.status == "CONCLUIDO" }
    val commissions: Double = done.sumOf { a ->
        val st = staff.firstOrNull { it.id == a.staffId }
        if (st == null) 0.0 else {
            val pc = products.firstOrNull { it.id == a.productId }?.commissionPct ?: 0.0
            a.price * st.commissionPct / 100.0 + a.productTotal * pc / 100.0
        }
    }
    val productCost: Double = done.sumOf { it.productCost }
    val exp: Double = expensesOf(ym, expensesAll).sumOf { it.amount }
    return Fin(received, pending, fees, commissions, productCost, exp, packageSales)
}
