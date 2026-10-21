package br.com.agendapro

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

data class Backup(
    val version: Int = 2,
    val clients: List<Client> = emptyList(),
    val services: List<Service> = emptyList(),
    val products: List<Product> = emptyList(),
    val staff: List<Staff> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val packageDefs: List<PackageDef> = emptyList(),
    val clientPackages: List<ClientPackage> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val waitlist: List<WaitItem> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val prefs: Map<String, String> = emptyMap()
)

class AppVm(app: Application) : AndroidViewModel(app) {
    val db = AppDb.get(app)
    private val prefs = app.getSharedPreferences("agenda", Context.MODE_PRIVATE)

    fun ps(k: String, d: String): String = prefs.getString(k, d) ?: d
    fun setPs(k: String, v: String) { prefs.edit().putString(k, v).apply() }

    private fun <T> Flow<T>.hot(init: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val clients = db.clients().all().hot(emptyList())
    val services = db.services().all().hot(emptyList())
    val products = db.products().all().hot(emptyList())
    val staff = db.staff().all().hot(emptyList())
    val expenses = db.expenses().all().hot(emptyList())
    val waitlist = db.waitlist().all().hot(emptyList())
    val holidays = db.holidays().all().hot(emptyList())
    val packages = db.packageDefs().all().hot(emptyList())
    val clientPackages = db.clientPackages().all().hot(emptyList())
    val concluded = db.appointments().concludedCounts().hot(emptyList())
    val tomorrow = db.appointments().byDate(LocalDate.now().plusDays(1).toString()).hot(emptyList())

    val selectedDate = MutableStateFlow(LocalDate.now())
    val month = MutableStateFlow(YearMonth.now())
    val goal = MutableStateFlow(ps("goal", "0").toDoubleOrNull() ?: 0.0)
    val loyaltyN = MutableStateFlow(ps("loy_n", "10").toIntOrNull() ?: 10)
    val loyaltyReward = MutableStateFlow(ps("loy_reward", "1 atendimento grátis"))

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayAppointments = selectedDate
        .flatMapLatest { db.appointments().byDate(it.toString()) }.hot(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthAppointments = month
        .flatMapLatest { db.appointments().between(it.atDay(1).toString(), it.atEndOfMonth().toString()) }
        .hot(emptyList())

    // ---------- escrita simples ----------
    fun save(c: Client) = viewModelScope.launch { db.clients().upsert(c) }
    fun delete(c: Client) = viewModelScope.launch { db.clients().delete(c) }
    fun save(s: Service) = viewModelScope.launch { db.services().upsert(s) }
    fun delete(s: Service) = viewModelScope.launch { db.services().delete(s) }
    fun save(p: Product) = viewModelScope.launch { db.products().upsert(p) }
    fun delete(p: Product) = viewModelScope.launch { db.products().delete(p) }
    fun save(s: Staff) = viewModelScope.launch { db.staff().upsert(s) }
    fun delete(s: Staff) = viewModelScope.launch { db.staff().delete(s) }
    fun save(a: Appointment) = viewModelScope.launch { db.appointments().upsert(a) }
    fun save(p: PackageDef) = viewModelScope.launch { db.packageDefs().upsert(p) }
    fun delete(p: PackageDef) = viewModelScope.launch { db.packageDefs().delete(p) }
    fun save(p: ClientPackage) = viewModelScope.launch { db.clientPackages().upsert(p) }
    fun save(e: Expense) = viewModelScope.launch { db.expenses().upsert(e) }
    fun delete(e: Expense) = viewModelScope.launch { db.expenses().delete(e) }
    fun save(w: WaitItem) = viewModelScope.launch { db.waitlist().upsert(w) }
    fun delete(w: WaitItem) = viewModelScope.launch { db.waitlist().delete(w) }
    fun save(h: Holiday) = viewModelScope.launch { db.holidays().upsert(h) }
    fun delete(h: Holiday) = viewModelScope.launch { db.holidays().delete(h) }
    fun addHolidays(l: List<Holiday>) = viewModelScope.launch { db.holidays().upsertAll(l) }

    // ---------- agendamentos / pacotes ----------
    private suspend fun restorePackage(a: Appointment) {
        if (a.packageId != 0L) {
            db.clientPackages().get(a.packageId)?.let {
                db.clientPackages().upsert(it.copy(used = (it.used - 1).coerceAtLeast(0)))
            }
        }
    }

    fun delete(a: Appointment) = viewModelScope.launch {
        restorePackage(a)
        db.appointments().delete(a)
    }

    fun cancel(a: Appointment) = viewModelScope.launch {
        restorePackage(a)
        val keepPay = a.payMethod != "Pacote"
        db.appointments().upsert(
            a.copy(status = "CANCELADO", packageId = 0, paid = a.paid && keepPay, payMethod = if (keepPay) a.payMethod else "")
        )
    }

    fun pay(a: Appointment, method: String) = viewModelScope.launch {
        db.appointments().upsert(a.copy(paid = true, payMethod = method, status = if (a.status == "CANCELADO") a.status else "CONCLUIDO"))
    }

    fun undoPayment(a: Appointment) = viewModelScope.launch {
        restorePackage(a)
        db.appointments().upsert(a.copy(paid = false, payMethod = "", packageId = 0))
    }

    fun usePackage(a: Appointment, cp: ClientPackage) = viewModelScope.launch {
        db.clientPackages().upsert(cp.copy(used = cp.used + 1))
        db.appointments().upsert(a.copy(paid = true, payMethod = "Pacote", packageId = cp.id, status = "CONCLUIDO"))
    }

    fun useSessionManually(cp: ClientPackage) = viewModelScope.launch {
        if (cp.used < cp.total) db.clientPackages().upsert(cp.copy(used = cp.used + 1))
    }

    fun deletePackage(cp: ClientPackage) = viewModelScope.launch { db.clientPackages().delete(cp) }

    fun redeemLoyalty(c: Client) = save(c.copy(loyaltyRedeemed = c.loyaltyRedeemed + 1))

    // ---------- validações de agenda ----------
    suspend fun hasConflict(a: Appointment): Boolean {
        val s = toMin(a.time)
        val e = s + a.durationMin
        return db.appointments().activeByDate(a.date).any {
            it.id != a.id && toMin(it.time) < e && s < toMin(it.time) + it.durationMin
        }
    }

    fun parseShifts(s: String): List<Pair<Int, Int>> = s.split(",").mapNotNull { p ->
        val t = p.trim().split("-")
        if (t.size == 2) runCatching { toMin(t[0]) to toMin(t[1]) }.getOrNull() else null
    }

    private val defHours = mapOf(
        1 to "09:00-18:00", 2 to "09:00-18:00", 3 to "09:00-18:00", 4 to "09:00-18:00",
        5 to "09:00-18:00", 6 to "09:00-13:00", 7 to ""
    )
    fun hours(dow: Int): String = ps("wh$dow", defHours[dow] ?: "")
    fun setHours(dow: Int, v: String) = setPs("wh$dow", v)

    fun outsideHours(date: String, time: String, dur: Int): String? {
        holidays.value.firstOrNull { it.date == date }?.let { return "Feriado: ${it.name}" }
        val shifts = parseShifts(hours(LocalDate.parse(date).dayOfWeek.value))
        if (shifts.isEmpty()) return "Dia sem expediente"
        val s = toMin(time)
        val e = s + dur
        return if (shifts.any { s >= it.first && e <= it.second }) null else "Fora do expediente"
    }

    suspend fun warnings(a: Appointment): List<String> {
        val w = mutableListOf<String>()
        if (hasConflict(a)) w += "Conflito com outro agendamento"
        outsideHours(a.date, a.time, a.durationMin)?.let { w += it }
        return w
    }

    fun dayNote(d: LocalDate, hs: List<Holiday>): String {
        hs.firstOrNull { it.date == d.toString() }?.let { return "Feriado: ${it.name}" }
        val h = hours(d.dayOfWeek.value)
        return if (parseShifts(h).isEmpty()) "Sem expediente" else "Expediente: $h"
    }

    /** Texto com horários livres para divulgar aos clientes (substitui o agendamento online). */
    suspend fun freeSlotsText(days: Int, dur: Int): String {
        val now = LocalDateTime.now()
        val sb = StringBuilder("Horários disponíveis:\n")
        var any = false
        for (i in 0 until days) {
            val d = LocalDate.now().plusDays(i.toLong())
            if (holidays.value.any { it.date == d.toString() }) continue
            val shifts = parseShifts(hours(d.dayOfWeek.value))
            if (shifts.isEmpty()) continue
            val busy = db.appointments().activeByDate(d.toString())
            val slots = mutableListOf<String>()
            for ((start, end) in shifts) {
                var t = start
                while (t + dur <= end) {
                    val overlaps = busy.any { toMin(it.time) < t + dur && t < toMin(it.time) + it.durationMin }
                    val past = d == now.toLocalDate() && t <= now.hour * 60 + now.minute
                    if (!overlaps && !past) slots += fmtMin(t)
                    t += 30
                }
            }
            if (slots.isNotEmpty()) {
                any = true
                sb.append("\n*${d.dayOfWeek.getDisplayName(JTextStyle.SHORT, ptBR)} ${d.format(DateTimeFormatter.ofPattern("dd/MM"))}*: ${slots.joinToString(", ")}")
            }
        }
        if (!any) sb.append("\nNenhum horário livre no período.")
        sb.append("\n\nResponda com o dia e o horário desejado!")
        return sb.toString()
    }

    // ---------- metas / fidelidade / taxas ----------
    fun setGoal(v: Double) { setPs("goal", v.toString()); goal.value = v }
    fun setLoyalty(n: Int, reward: String) {
        setPs("loy_n", n.toString()); setPs("loy_reward", reward)
        loyaltyN.value = n; loyaltyReward.value = reward
    }
    fun fee(method: String): Double = when (method) {
        "Débito" -> ps("fee_deb", "0")
        "Crédito" -> ps("fee_cred", "0")
        else -> "0"
    }.replace(',', '.').toDoubleOrNull() ?: 0.0

    // ---------- mensagens ----------
    private val defaults = mapOf(
        "confirm" to "Olá {cliente}! Seu horário de {servico} está agendado para {data} às {hora}. Pode confirmar?",
        "reminder" to "Olá {cliente}! Passando para lembrar do seu horário de {servico} em {data} às {hora}. Até lá!",
        "birthday" to "Parabéns, {cliente}! 🎉 Desejamos um dia muito especial!"
    )
    fun tpl(key: String): String = ps(key, defaults[key] ?: "")
    fun setTpl(key: String, v: String) = setPs(key, v)

    fun fill(key: String, c: Client?, s: Service?, a: Appointment?): String =
        tpl(key)
            .replace("{cliente}", c?.name?.substringBefore(' ') ?: "")
            .replace("{servico}", s?.name ?: "")
            .replace("{data}", a?.let { LocalDate.parse(it.date).format(dmy) } ?: "")
            .replace("{hora}", a?.time ?: "")

    // ---------- backup ----------
    suspend fun exportJson(): String = Gson().toJson(
        Backup(
            clients = db.clients().list(), services = db.services().list(), products = db.products().list(),
            staff = db.staff().list(), appointments = db.appointments().list(),
            packageDefs = db.packageDefs().list(), clientPackages = db.clientPackages().list(),
            expenses = db.expenses().list(), waitlist = db.waitlist().list(), holidays = db.holidays().list(),
            prefs = prefs.all.mapValues { it.value.toString() }
        )
    )

    suspend fun importJson(json: String): Boolean {
        val b = try { Gson().fromJson(json, Backup::class.java) } catch (e: Exception) { null } ?: return false
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            db.clients().upsertAll(b.clients); db.services().upsertAll(b.services)
            db.products().upsertAll(b.products); db.staff().upsertAll(b.staff)
            db.appointments().upsertAll(b.appointments); db.packageDefs().upsertAll(b.packageDefs)
            db.clientPackages().upsertAll(b.clientPackages); db.expenses().upsertAll(b.expenses)
            db.waitlist().upsertAll(b.waitlist); db.holidays().upsertAll(b.holidays)
        }
        val e = prefs.edit().clear()
        b.prefs.forEach { (k, v) -> e.putString(k, v) }
        e.apply()
        goal.value = ps("goal", "0").toDoubleOrNull() ?: 0.0
        loyaltyN.value = ps("loy_n", "10").toIntOrNull() ?: 10
        loyaltyReward.value = ps("loy_reward", "1 atendimento grátis")
        return true
    }
}
