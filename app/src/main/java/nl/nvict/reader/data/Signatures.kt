package nl.nvict.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Een opgeslagen handtekening (PNG met transparante achtergrond) in de privé-opslag van de app. */
data class SavedSignature(val file: File, val bitmap: Bitmap)

class Signatures(context: Context) {

    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }

    suspend fun list(): List<SavedSignature> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f -> BitmapFactory.decodeFile(f.absolutePath)?.let { SavedSignature(f, it) } }
            ?: emptyList()
    }

    suspend fun save(bitmap: Bitmap): SavedSignature = withContext(Dispatchers.IO) {
        val file = File(dir, "handtekening_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Maximaal 6 bewaren
        dir.listFiles { f -> f.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX)
            ?.forEach { it.delete() }
        SavedSignature(file, bitmap)
    }

    suspend fun delete(sig: SavedSignature) = withContext(Dispatchers.IO) {
        sig.file.delete()
    }

    companion object {
        private const val MAX = 6
    }
}
