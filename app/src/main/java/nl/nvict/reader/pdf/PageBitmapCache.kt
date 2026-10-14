package nl.nvict.reader.pdf

import android.graphics.Bitmap
import android.util.LruCache

/** Cache van gerenderde pagina's, maximaal een kwart van het beschikbare geheugen. */
object PageBitmapCache {
    data class Key(val docId: Long, val page: Int, val widthPx: Int, val dark: Boolean)

    private val cache = object : LruCache<Key, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    operator fun get(key: Key): Bitmap? = cache.get(key)

    fun put(key: Key, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Beste beschikbare bitmap van deze pagina (willekeurige breedte), om tijdens het laden alvast iets te tonen. */
    fun anyFor(docId: Long, page: Int, dark: Boolean): Bitmap? =
        cache.snapshot().entries
            .filter { it.key.docId == docId && it.key.page == page && it.key.dark == dark }
            .maxByOrNull { it.key.widthPx }
            ?.value

    fun evictDocument(docId: Long) {
        cache.snapshot().keys.filter { it.docId == docId }.forEach { cache.remove(it) }
    }
}
