package id.etolltracker.app

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

val idLoc = Locale("id", "ID")
fun rupiah(v: Long?): String =
    if (v == null) "—" else (if (v < 0) "-" else "") + "Rp" + String.format(idLoc, "%,d", abs(v))
fun fmt(t: Long): String = SimpleDateFormat("dd MMM yyyy HH:mm", idLoc).format(Date(t))
fun fmtIn(t: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", idLoc).format(Date(t))
fun parseIn(s: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd HH:mm", idLoc).apply { isLenient = false }.parse(s.trim())?.time
} catch (e: Exception) { null }

fun typeLabel(t: String) = when (t) { "TOLL" -> "Tol"; "TOPUP" -> "Top-up"; else -> "Lainnya" }
fun sourceLabel(s: String) = when (s) {
    "MANUAL" -> "Input manual"; "ESTIMASI" -> "Estimasi (dikonfirmasi pengguna)"
    "IMPOR" -> "Diimpor"; "DEMO" -> "DATA DEMO"; else -> "Terverifikasi"
}
fun duration(t: Txn): String {
    val m = t.timeIn?.let { (t.time - it) / 60000 } ?: return "Tidak tersedia"
    return if (m < 0) "Tidak tersedia" else "${m / 60} jam ${m % 60} menit"
}

fun inPeriod(t: Long, p: Int): Boolean {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    return when (p) {
        0 -> true
        1 -> t >= c.timeInMillis
        2 -> t >= System.currentTimeMillis() - 7 * 86400000L
        else -> { c.set(Calendar.DAY_OF_MONTH, 1); t >= c.timeInMillis }
    }
}

private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
fun toCsv(list: List<Txn>): String {
    val sb = StringBuilder("Waktu,Jenis,Nominal,Sumber,Kartu,Gerbang Masuk,Waktu Masuk,Gerbang Keluar,Saldo Sebelum,Saldo Sesudah,Catatan\n")
    list.forEach {
        sb.append(listOf(fmtIn(it.time), typeLabel(it.type), it.amount.toString(), sourceLabel(it.source), it.cardType,
            it.gateIn, it.timeIn?.let { x -> fmtIn(x) } ?: "", it.gateOut,
            it.balanceBefore?.toString() ?: "", it.balanceAfter?.toString() ?: "", it.note).joinToString(",") { x -> q(x) }).append('\n')
    }
    return sb.toString()
}

fun toPdf(list: List<Txn>): ByteArray {
    val doc = PdfDocument(); val paint = Paint().apply { textSize = 10f }
    var n = 1
    fun newPage() = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
    var page = newPage(); var y = 40f
    val lines = listOf("e-Toll Tracker Indonesia — Riwayat", "") + list.map {
        "${fmt(it.time)} | ${typeLabel(it.type)} | ${rupiah(it.amount)} | ${it.gateIn}->${it.gateOut} | ${sourceLabel(it.source)}".take(100)
    }
    lines.forEach {
        if (y > 800f) { doc.finishPage(page); n++; page = newPage(); y = 40f }
        page.canvas.drawText(it, 30f, y, paint); y += 14f
    }
    doc.finishPage(page)
    val b = ByteArrayOutputStream(); doc.writeTo(b); doc.close()
    return b.toByteArray()
}
