package br.com.agendapro

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClientsScreen(vm: AppVm) {
    val clients by vm.clients.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Client?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) importing = true
    }
    val shown = clients.filter { it.name.contains(query, true) || it.phone.contains(query) }

    Box(Modifier.fillMaxSize()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query, { query = it }, label = { Text("Buscar cliente") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) importing = true
                    else permission.launch(Manifest.permission.READ_CONTACTS)
                }) { Text("Importar") }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(shown, key = { it.id }) { c ->
                    ItemRow(
                        c.name,
                        listOf(c.phone, if (c.birthday.isNotBlank()) "🎂 ${c.birthday}" else "", c.notes).filter { it.isNotBlank() }.joinToString(" · "),
                        onEdit = { editing = c }, onDelete = { vm.delete(c) }
                    ) {
                        IconButton(onClick = { openWhats(ctx, c.phone, "Olá ${c.name.substringBefore(' ')}!") }) {
                            Icon(Icons.Default.Send, "WhatsApp")
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Novo cliente")
        }
    }
    if (creating) ClientDialog(vm, null) { creating = false }
    editing?.let { ClientDialog(vm, it) { editing = null } }
    if (importing) ImportContactsDialog(vm) { importing = false }
}

@Composable
fun ClientDialog(vm: AppVm, initial: Client?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var birthday by remember { mutableStateOf(initial?.birthday ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    FormDialog(if (initial == null) "Novo cliente" else "Editar cliente", onDismiss, onSave = {
        if (name.isNotBlank()) {
            vm.save((initial ?: Client(name = name)).copy(name = name.trim(), phone = phone.trim(), birthday = birthday.trim(), notes = notes.trim()))
            onDismiss()
        }
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
        OutlinedTextField(phone, { phone = it }, label = { Text("Telefone (com DDD)") }, singleLine = true)
        OutlinedTextField(birthday, { birthday = it }, label = { Text("Aniversário (dd/MM)") }, singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text("Observação / referência") })
    }
}

@Composable
fun ImportContactsDialog(vm: AppVm, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val existing = remember { vm.clients.value.map { it.phone.filter(Char::isDigit) }.toSet() }
    var contacts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }

    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) {
            val out = linkedMapOf<String, Pair<String, String>>()
            val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            ctx.contentResolver.query(phoneUri, cols, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0) ?: continue
                    val p = c.getString(1) ?: continue
                    val key = p.filter(Char::isDigit)
                    if (key.isNotEmpty() && key !in existing && key !in out) out[key] = n to p
                }
            }
            out.values.toList()
        }
        loaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar contatos") },
        text = {
            when {
                !loaded -> Text("Carregando...")
                contacts.isEmpty() -> Text("Nenhum contato novo encontrado.")
                else -> LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(contacts) { i, contact ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (i in selected) selected.remove(i) else selected.add(i) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = i in selected, onCheckedChange = { if (it) selected.add(i) else selected.remove(i) })
                            Column {
                                Text(contact.first)
                                Text(contact.second, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selected.forEach { vm.save(Client(name = contacts[it].first, phone = contacts[it].second)) }
                onDismiss()
            }) { Text("Importar (${selected.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
