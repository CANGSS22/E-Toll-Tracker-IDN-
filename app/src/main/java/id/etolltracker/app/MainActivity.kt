@file:OptIn(ExperimentalMaterial3Api::class)

package id.etolltracker.app

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { vm.scanning.collect { setReader(it) } }
        setContent { App(vm) }
    }

    override fun onResume() {
        super.onResume()
        val a = NfcAdapter.getDefaultAdapter(this)
        vm.nfc.value = when { a == null -> "Tidak tersedia"; !a.isEnabled -> "Tidak aktif"; else -> "Aktif" }
    }

    override fun onPause() {
        super.onPause(); vm.scanning.value = false; setReader(false)
    }

    private fun setReader(on: Boolean) {
        val a = NfcAdapter.getDefaultAdapter(this) ?: return
        if (on) {
            a.enableReaderMode(this, { tag -> vm.onScan(CardReaders.read(tag)) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        } else a.disableReaderMode(this)
    }
}

@Composable
fun App(vm: MainVm) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF66BB6A), secondary = Color(0xFF64B5F6))
    else lightColorScheme(primary = Color(0xFF1B5E20), secondary = Color(0xFF1565C0))
    MaterialTheme(colorScheme = scheme) { Surface(Modifier.fillMaxSize()) { Root(vm) } }
}

@Composable
fun Root(vm: MainVm) {
    var tab by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Pair<Txn?, String>?>(null) }
    var detail by remember { mutableStateOf<Txn?>(null) }
    var manualBal by remember { mutableStateOf(false) }
    val change by vm.change.collectAsState()
    val msg by vm.msg.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.msg.value = null } }
    val items = listOf("Beranda" to Icons.Filled.Home, "Scan NFC" to Icons.Filled.Nfc, "Riwayat" to Icons.Filled.History,
        "Statistik" to Icons.Filled.BarChart, "Pengaturan" to Icons.Filled.Settings)

    Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        NavigationBar {
            items.forEachIndexed { i, (l, ic) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, l) },
                    label = { Text(l, fontSize = 10.sp, maxLines = 1) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> Home(vm, { tab = it }) { editor = Pair(null, "TOPUP") }
                1 -> Scan(vm) { manualBal = true }
                2 -> History(vm, { detail = it }) { editor = Pair(null, "TOLL") }
                3 -> Stats(vm)
                else -> SettingsScreen(vm)
            }
        }
    }

    editor?.let { (t, preset) ->
        Editor(t, preset, onSave = { vm.save(it); editor = null }, onDismiss = { editor = null })
    }
    detail?.let { t ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text("Detail ${typeLabel(t.type)}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tarif/nominal: ${rupiah(t.amount)}")
                    Text("Gerbang masuk: ${t.gateIn.ifBlank { "Tidak tersedia" }}")
                    Text("Waktu masuk: ${t.timeIn?.let { fmt(it) } ?: "Tidak tersedia"}")
                    Text("Gerbang keluar: ${t.gateOut.ifBlank { "Tidak tersedia" }}")
                    Text("Waktu keluar/transaksi: ${fmt(t.time)}")
                    Text("Lama perjalanan: ${duration(t)}")
                    Text("Saldo sebelum: ${rupiah(t.balanceBefore)}")
                    Text("Saldo sesudah: ${rupiah(t.balanceAfter)}")
                    Text("Kartu: ${t.cardType.ifBlank { "-" }}")
                    Text("Sumber data: ${sourceLabel(t.source)}")
                    Text("Status validasi: " + if (t.source == "TERVERIFIKASI") "Terverifikasi" else "Belum terverifikasi dari sumber resmi")
                    if (t.note.isNotBlank()) Text("Catatan: ${t.note}")
                }
            },
            confirmButton = {
                if (t.source != "TERVERIFIKASI" && t.source != "IMPOR")
                    TextButton(onClick = { editor = Pair(t, t.type); detail = null }) { Text("Edit") }
            },
            dismissButton = {
                Row {
                    if (t.source != "TERVERIFIKASI") TextButton(onClick = { vm.delete(t); detail = null }) { Text("Hapus") }
                    TextButton(onClick = { detail = null }) { Text("Tutup") }
                }
            })
    }
    change?.let { c ->
        AlertDialog(onDismissRequest = { vm.classify(null) }, title = { Text("Saldo berubah") },
            text = {
                Text("Saldo ${if (c.delta > 0) "bertambah" else "berkurang"} ${rupiah(abs(c.delta))} sejak pemindaian terakhir " +
                    "(${rupiah(c.before)} → ${rupiah(c.after)}). Data kartu tidak memastikan jenis transaksinya. " +
                    "Klasifikasikan (akan dicatat sebagai estimasi):")
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { vm.classify("TOLL") }) { Text("Transaksi tol") }
                    TextButton(onClick = { vm.classify("TOPUP") }) { Text("Top-up / isi saldo") }
                    TextButton(onClick = { vm.classify("OTHER") }) { Text("Transaksi lain") }
                }
            },
            dismissButton = { TextButton(onClick = { vm.classify(null) }) { Text("Abaikan") } })
    }
    if (manualBal) {
        var v by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { manualBal = false }, title = { Text("Input saldo manual") },
            text = {
                OutlinedTextField(v, { v = it.filter(Char::isDigit) }, label = { Text("Saldo (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = {
                TextButton(onClick = {
                    v.toLongOrNull()?.let { vm.onScan(CardResult("Input manual", "MANUAL", it, "Dimasukkan pengguna", "")) }
                    manualBal = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { manualBal = false }) { Text("Batal") } })
    }
}

@Composable
fun Stat(label: String, value: String, mod: Modifier = Modifier) {
    Card(mod) { Column(Modifier.padding(12.dp)) { Text(label, fontSize = 12.sp); Text(value, fontWeight = FontWeight.Bold) } }
}

@Composable
fun Home(vm: MainVm, go: (Int) -> Unit, topup: () -> Unit) {
    val s = summarize(vm.txns.collectAsState().value, vm.snaps.collectAsState().value)
    val nfc by vm.nfc.collectAsState()
    val last by vm.last.collectAsState()
    val on = MaterialTheme.colorScheme.onPrimary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("e-Toll Tracker Indonesia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Status NFC: $nfc • Kartu: ${last?.cardType ?: "belum ada pemindaian"}")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Column(Modifier.padding(20.dp)) {
                Text("Saldo terakhir yang terbaca", color = on)
                Text(rupiah(s.actual), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = on)
                Text("Saldo awal: ${rupiah(s.initial)}", color = on)
                s.calc?.let { Text("Saldo hitungan (estimasi): ${rupiah(it)}", color = on) }
                s.diff?.let { if (it != 0L) Text("Selisih ${rupiah(it)}: ada transaksi belum tercatat atau data belum lengkap.", color = on) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Total pemasukan", rupiah(s.income), Modifier.weight(1f))
            Stat("Total pengeluaran", rupiah(s.expense), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Jumlah perjalanan", s.trips.toString(), Modifier.weight(1f))
            Stat("Pengeluaran bulan ini", rupiah(s.month), Modifier.weight(1f))
        }
        Button({ go(1) }, Modifier.fillMaxWidth().height(60.dp)) { Text("Scan Kartu NFC", fontSize = 18.sp) }
        OutlinedButton(topup, Modifier.fillMaxWidth()) { Text("Catat Top-Up") }
        OutlinedButton({ go(2) }, Modifier.fillMaxWidth()) { Text("Riwayat Perjalanan") }
        OutlinedButton({ go(1) }, Modifier.fillMaxWidth()) { Text("Lihat Detail Kartu") }
    }
}

@Composable
fun Scan(vm: MainVm, manual: () -> Unit) {
    val nfc by vm.nfc.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val last by vm.last.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Scan Kartu NFC", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        when (nfc) {
            "Tidak tersedia" -> Text("HP ini tidak mendukung NFC. Anda tetap dapat mencatat secara manual.", color = MaterialTheme.colorScheme.error)
            "Tidak aktif" -> {
                Text("NFC belum diaktifkan.", color = MaterialTheme.colorScheme.error)
                Button({ ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text("Buka Pengaturan NFC") }
            }
            else -> if (!scanning) Button({ vm.scanning.value = true }, Modifier.fillMaxWidth().height(56.dp)) { Text("Mulai Scan") }
        }
        if (scanning) {
            CircularProgressIndicator()
            Text("Tempelkan kartu ke bagian belakang HP dan tahan sebentar…")
            TextButton({ vm.scanning.value = false }) { Text("Batal") }
        }
        last?.let {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Jenis kartu: ${it.cardType}"); Text("ID kartu: ${it.uid}")
                Text("Saldo: ${it.balance?.let { b -> rupiah(b) } ?: "tidak dapat dibaca"}")
                Text("Status: ${it.status}"); Text("Waktu pindai: ${fmt(it.time)}")
                if (it.note.isNotBlank()) Text(it.note, fontSize = 12.sp)
            } }
        }
        OutlinedButton(manual, Modifier.fillMaxWidth()) { Text("Input saldo manual") }
    }
}

@Composable
fun History(vm: MainVm, open: (Txn) -> Unit, add: () -> Unit) {
    val all by vm.txns.collectAsState()
    var q by remember { mutableStateOf("") }
    var p by remember { mutableIntStateOf(0) }
    var ty by remember { mutableStateOf("") }
    val list = all.filter { t ->
        inPeriod(t.time, p) && (ty == "" || t.type == ty) &&
            (q.isBlank() || listOf(t.gateIn, t.gateOut, t.cardType, t.note, t.amount.toString()).any { it.contains(q, true) })
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Riwayat Perjalanan Tol", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(q, { q = it }, label = { Text("Cari gerbang, kartu, nominal") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Semua", "Hari ini", "7 hari", "Bulan ini").forEachIndexed { i, l ->
                    FilterChip(p == i, { p = i }, { Text(l, fontSize = 12.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "Semua jenis", "TOLL" to "Tol", "TOPUP" to "Top-up", "OTHER" to "Lainnya").forEach { (k, l) ->
                    FilterChip(ty == k, { ty = k }, { Text(l, fontSize = 12.sp) })
                }
            }
            if (list.isEmpty()) Text("Belum ada catatan. Tekan + untuk menambah, atau muat data demo di Pengaturan.", Modifier.padding(top = 24.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { it.id }) { t ->
                    Card(Modifier.fillMaxWidth().clickable { open(t) }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(typeLabel(t.type), fontWeight = FontWeight.Bold)
                                Text((if (t.type == "TOPUP") "+" else "-") + rupiah(t.amount), fontWeight = FontWeight.Bold)
                            }
                            if (t.type == "TOLL") Text("${t.gateIn.ifBlank { "?" }} → ${t.gateOut.ifBlank { "?" }}", fontSize = 13.sp)
                            Text("${fmt(t.time)} • ${sourceLabel(t.source)}", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        FloatingActionButton(add, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Filled.Add, "Tambah") }
    }
}

@Composable
fun Editor(t: Txn?, preset: String, onSave: (Txn) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(t?.type ?: preset) }
    var amount by remember { mutableStateOf(t?.amount?.toString() ?: "") }
    var gin by remember { mutableStateOf(t?.gateIn ?: "") }
    var gout by remember { mutableStateOf(t?.gateOut ?: "") }
    var note by remember { mutableStateOf(t?.note ?: "") }
    var tin by remember { mutableStateOf(t?.timeIn?.let { fmtIn(it) } ?: "") }
    var tout by remember { mutableStateOf(fmtIn(t?.time ?: System.currentTimeMillis())) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (t == null) "Tambah catatan" else "Edit catatan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("TOLL", "TOPUP", "OTHER").forEach { k -> FilterChip(type == k, { type = k }, { Text(typeLabel(k), fontSize = 12.sp) }) }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Nominal (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (type == "TOLL") {
                    OutlinedTextField(gin, { gin = it }, label = { Text("Gerbang masuk (opsional)") })
                    OutlinedTextField(tin, { tin = it }, label = { Text("Waktu masuk (yyyy-MM-dd HH:mm)") })
                    OutlinedTextField(gout, { gout = it }, label = { Text("Gerbang keluar (opsional)") })
                }
                OutlinedTextField(tout, { tout = it }, label = { Text("Waktu transaksi (yyyy-MM-dd HH:mm)") })
                OutlinedTextField(note, { note = it }, label = { Text("Catatan sumber") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toLongOrNull()
                if (a != null && a > 0) {
                    val time = parseIn(tout) ?: System.currentTimeMillis()
                    val g1 = if (type == "TOLL") gin.trim() else ""
                    val g2 = if (type == "TOLL") gout.trim() else ""
                    val key = "M|$type|$a|${time / 60000}|$g1|$g2"
                    val base = t ?: Txn(type = type, amount = a, time = time, source = "MANUAL", dedupeKey = key)
                    onSave(base.copy(type = type, amount = a, time = time, gateIn = g1, gateOut = g2,
                        timeIn = if (type == "TOLL") parseIn(tin) else null, note = note, dedupeKey = key))
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
fun Bars(data: List<Pair<String, Long>>) {
    val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.secondary
    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        data.forEach { (l, v) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (v > 0) "${v / 1000}rb" else "", fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 1f) * 0.7f).background(color))
                Text(l, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun Stats(vm: MainVm) {
    val txns by vm.txns.collectAsState()
    val exp = txns.filter { it.type != "TOPUP" }
    val dayF = SimpleDateFormat("dd/MM", idLoc)
    val monF = SimpleDateFormat("MMM yy", idLoc)
    val daily = (6 downTo 0).map { off ->
        val k = dayF.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -off) }.time)
        k to exp.filter { dayF.format(Date(it.time)) == k }.sumOf { it.amount }
    }
    val monthly = (5 downTo 0).map { off ->
        val k = monF.format(Calendar.getInstance().apply { add(Calendar.MONTH, -off) }.time)
        k to exp.filter { monF.format(Date(it.time)) == k }.sumOf { it.amount }
    }
    val tolls = txns.filter { it.type == "TOLL" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Statistik", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Stat("Rata-rata biaya tol per perjalanan", if (tolls.isEmpty()) "—" else rupiah(tolls.sumOf { it.amount } / tolls.size), Modifier.fillMaxWidth())
        Text("Pengeluaran 7 hari terakhir"); Bars(daily)
        Text("Pengeluaran 6 bulan terakhir"); Bars(monthly)
    }
}

@Composable
fun SettingsScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val txns by vm.txns.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u ->
        if (u != null) ctx.contentResolver.openOutputStream(u)?.use { it.write(toCsv(txns).toByteArray()) }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        if (u != null) ctx.contentResolver.openOutputStream(u)?.use { it.write(toPdf(txns)) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Button({ csv.launch("riwayat-etoll.csv") }, Modifier.fillMaxWidth()) { Text("Ekspor CSV") }
        Button({ pdf.launch("riwayat-etoll.pdf") }, Modifier.fillMaxWidth()) { Text("Ekspor PDF") }
        OutlinedButton({ vm.demo() }, Modifier.fillMaxWidth()) { Text("Muat data demo (berlabel DEMO)") }
        OutlinedButton({ vm.clearDemo() }, Modifier.fillMaxWidth()) { Text("Hapus data demo saja") }
        Button({ confirm = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("Hapus seluruh data lokal")
        }
        Text("Privasi: semua data hanya tersimpan di HP ini. Aplikasi tidak memakai internet, tidak menyimpan PIN atau kunci kartu, " +
            "dan tidak mengakses lokasi.", fontSize = 12.sp)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Hapus semua data?") },
        text = { Text("Seluruh catatan dan snapshot saldo akan dihapus permanen.") },
        confirmButton = { TextButton({ vm.clearAll(); confirm = false }) { Text("Hapus") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Batal") } })
}
