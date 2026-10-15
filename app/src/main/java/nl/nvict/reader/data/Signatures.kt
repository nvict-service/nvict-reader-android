package nl.nvict.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Soort handtekening; [widthFraction] is de standaardbreedte op de pagina (deel van de paginabreedte). */
enum class SignatureKind(val fileName: String, val widthFraction: Float) {
    SIGNATURE("handtekening.png", 0.35f),
    INITIALS("paraaf.png", 0.14f),
}

/**
 * Eén vaste handtekening en één vaste paraaf, als PNG met transparante achtergrond in de privé-opslag
 * van de app. Ze blijven bewaard tot de gebruiker ze vervangt of verwijdert.
 */
class Signatures private constructor(context: Context) {

    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }
    private val _state = MutableStateFlow<Map<SignatureKind, Bitmap>>(emptyMap())
    val state: StateFlow<Map<SignatureKind, Bitmap>> = _state.asStateFlow()

    private fun file(kind: SignatureKind) = File(dir, kind.fileName)

    suspend fun load() = withContext(Dispatchers.IO) {
        migrateOld()
        _state.value = SignatureKind.entries.mapNotNull { kind ->
            file(kind).takeIf { it.exists() }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?.let { kind to it }
        }.toMap()
    }

    suspend fun save(kind: SignatureKind, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        FileOutputStream(file(kind)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _state.value = _state.value + (kind to bitmap)
    }

    suspend fun delete(kind: SignatureKind) = withContext(Dispatchers.IO) {
        file(kind).delete()
        _state.value = _state.value - kind
    }

    /** Versie 1.0.10 bewaarde meerdere losse handtekeningen; de nieuwste wordt de vaste handtekening. */
    private fun migrateOld() {
        val old = dir.listFiles { f -> f.name.startsWith("handtekening_") && f.extension == "png" } ?: return
        if (old.isEmpty()) return
        val target = file(SignatureKind.SIGNATURE)
        if (!target.exists()) old.maxByOrNull { it.lastModified() }?.renameTo(target)
        old.filter { it.exists() }.forEach { it.delete() }
    }

    companion object {
        @Volatile private var instance: Signatures? = null
        fun get(context: Context): Signatures =
            instance ?: synchronized(this) { instance ?: Signatures(context.applicationContext).also { instance = it } }
    }
}
