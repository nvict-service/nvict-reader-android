package nl.nvict.reader

import android.app.Application
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.api.AlreadyClosedBehavior
import io.legere.pdfiumandroid.api.Config

class ReaderApp : Application() {

    /** Eén Pdfium-instantie voor de hele app. */
    val pdfium: PdfiumCore by lazy {
        PdfiumCore(this, Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
    }

    companion object {
        lateinit var instance: ReaderApp
            private set
    }
}
