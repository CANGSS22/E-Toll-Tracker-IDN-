package id.etolltracker.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

data class Change(val delta: Long, val before: Long, val after: Long, val cardType: String)
data class Summary(
    val initial: Long?, val actual: Long?, val income: Long, val expense: Long,
    val calc: Long?, val diff: Long?, val trips: Int, val month: Long
)

fun summarize(txns: List<Txn>, snaps: List<Snapshot>): Summary {
    val first = snaps.firstOrNull { it.balance != null }
    val actual = snaps.lastOrNull { it.balance != null }?.balance
    val rel = if (first == null) emptyList() else txns.filter { it.time >= first.time }
    val calc = first?.balance?.let {
        it + rel.filter { t -> t.type == "TOPUP" }.sumOf { t -> t.amount } -
            rel.filter { t -> t.type != "TOPUP" }.sumOf { t -> t.amount }
    }
    val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    return Summary(
        first?.balance, actual,
        txns.filter { it.type == "TOPUP" }.sumOf { it.amount },
        txns.filter { it.type != "TOPUP" }.sumOf { it.amount },
        calc, if (actual != null && calc != null) actual - calc else null,
        txns.count { it.type == "TOLL" },
        txns.filter { it.type != "TOPUP" && it.time >= c.timeInMillis }.sumOf { it.amount }
    )
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val txns = dao.txns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val snaps = dao.snaps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val nfc = MutableStateFlow("Memeriksa…")
    val scanning = MutableStateFlow(false)
    val last = MutableStateFlow<CardResult?>(null)
    val change = MutableStateFlow<Change?>(null)
    val msg = MutableStateFlow<String?>(null)

    fun onScan(r: CardResult) {
        viewModelScope.launch {
            val prev = snaps.value.lastOrNull { it.balance != null && it.cardUid == r.uid }?.balance
            dao.insertSnap(Snapshot(time = r.time, cardUid = r.uid, cardType = r.cardType, balance = r.balance, status = r.status))
            last.value = r; scanning.value = false
            val b = r.balance
            if (b != null && prev != null && b != prev) change.value = Change(b - prev, prev, b, r.cardType)
        }
    }

    /** Perubahan saldo bukan bukti transaksi tol: dicatat sebagai ESTIMASI hanya setelah dikonfirmasi pengguna. */
    fun classify(type: String?) {
        val c = change.value ?: return
        change.value = null
        if (type == null) return
        val now = System.currentTimeMillis()
        save(Txn(type = type, amount = abs(c.delta), time = now, source = "ESTIMASI", cardType = c.cardType,
            balanceBefore = c.before, balanceAfter = c.after,
            note = "Dari perubahan saldo, dikonfirmasi pengguna",
            dedupeKey = "E|${c.before}|${c.after}|${now / 60000}"))
    }

    fun save(t: Txn) {
        viewModelScope.launch {
            try {
                if (t.id == 0L) { if (dao.insert(t) == -1L) msg.value = "Catatan duplikat diabaikan" } else dao.update(t)
            } catch (e: Exception) { msg.value = "Gagal menyimpan (kemungkinan duplikat)" }
        }
    }
    fun delete(t: Txn) { viewModelScope.launch { dao.delete(t) } }
    fun clearAll() { viewModelScope.launch { dao.clearTxn(); dao.clearSnap(); last.value = null } }
    fun clearDemo() { viewModelScope.launch { dao.clearDemo() } }
    fun demo() {
        val now = System.currentTimeMillis(); val d = 86400000L
        listOf(11000L, 22500L, 8500L, 17000L, 13500L, 22500L).forEachIndexed { i, a ->
            val t = now - (i + 1) * d
            save(Txn(type = "TOLL", amount = a, time = t, timeIn = t - 45 * 60000, source = "DEMO",
                gateIn = "Gerbang Demo ${'A' + i}", gateOut = "Gerbang Demo ${'B' + i}", note = "DATA DEMO", dedupeKey = "D|$i"))
        }
        save(Txn(type = "TOPUP", amount = 200000, time = now - 7 * d, source = "DEMO", note = "DATA DEMO", dedupeKey = "D|topup"))
    }
}
