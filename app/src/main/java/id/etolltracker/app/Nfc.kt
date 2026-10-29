package id.etolltracker.app

import android.nfc.Tag

data class CardResult(
    val cardType: String, val uid: String, val balance: Long?,
    val status: String, val note: String, val time: Long = System.currentTimeMillis()
)

/**
 * Titik pengembangan: tambahkan adapter untuk tiap penerbit (Mandiri e-Money, BCA Flazz,
 * BRI BRIZZI, BNI TapCash) HANYA jika Anda memiliki SDK resmi/izin penerbit.
 * Adapter mengembalikan null jika bukan kartunya.
 */
interface CardAdapter { fun read(tag: Tag): CardResult? }

/** Hanya melaporkan apa yang benar-benar terbaca: teknologi NFC dan UID. Tidak menebak penerbit/saldo. */
object GenericAdapter : CardAdapter {
    override fun read(tag: Tag): CardResult {
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        val techs = tag.techList.joinToString { it.substringAfterLast('.') }
        return CardResult(
            "Kartu NFC (penerbit tidak dikenali)", uid, null, "Saldo tidak dapat dibaca",
            "Teknologi: $techs. Saldo kartu e-money dilindungi autentikasi penerbit; aplikasi ini " +
                "tidak melewatinya. Gunakan input saldo manual atau integrasi resmi penerbit."
        )
    }
}

object CardReaders {
    val adapters: List<CardAdapter> = listOf(GenericAdapter)
    fun read(tag: Tag): CardResult = adapters.firstNotNullOfOrNull {
        try { it.read(tag) } catch (e: Exception) { null }
    } ?: CardResult("Tidak didukung", "-", null, "Gagal membaca kartu", "Coba tempelkan ulang kartu.")
}
