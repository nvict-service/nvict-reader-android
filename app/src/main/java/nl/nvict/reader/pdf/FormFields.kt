package nl.nvict.reader.pdf

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class FieldKind { TEXT, CHECKBOX, RADIO, CHOICE }

/**
 * Eén zichtbaar formulierelement (widget) op een pagina. Een veld kan meerdere widgets hebben
 * (bijvoorbeeld een groep keuzerondjes); die delen dan [fieldName].
 */
data class FormWidget(
    val fieldName: String,
    val kind: FieldKind,
    val page: Int,
    val rect: NRect,
    val readOnly: Boolean,
    /** Waarde die dit vinkje/rondje "aan" zet. */
    val optionValue: String? = null,
    val multiline: Boolean = false,
    val maxLen: Int = -1,
    val password: Boolean = false,
    /** Lettergrootte uit het formulier; 0 = automatisch passend. */
    val fontSizePt: Float = 0f,
    /** 0 = links, 1 = midden, 2 = rechts. */
    val align: Int = 0,
    /** Keuzelijst: (exportwaarde, weergavetekst). */
    val options: List<Pair<String, String>> = emptyList(),
)

data class FormInfo(
    val widgets: List<FormWidget>,
    /** Waarden zoals ze in het bestand staan; "Off" = vinkje/rondje uit. */
    val initialValues: Map<String, String>,
    /** Dynamisch XFA-formulier: alleen in Adobe Acrobat in te vullen. */
    val dynamicXfa: Boolean,
) {
    val isEmpty: Boolean get() = widgets.isEmpty()
}

const val OFF = "Off"

/** Leest de AcroForm-velden met PdfBox; posities worden via Pdfium naar schermcoördinaten omgerekend. */
object FormReader {

    private val fontSizeRegex = Regex("""([0-9]*\.?[0-9]+)\s+Tf""")

    suspend fun read(context: Context, doc: OpenDocument): FormInfo? {
        val raw = ArrayList<Pair<FormWidget, RectF>>()
        val initial = HashMap<String, String>()
        var dynamicXfa = false

        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(doc.uri) ?: return@withContext
            val pdf = input.use {
                PDDocument.load(it, doc.password ?: "", MemoryUsageSetting.setupMixed(32L * 1024 * 1024))
            }
            try {
                val form = pdf.documentCatalog.acroForm ?: return@withContext
                dynamicXfa = runCatching { form.xfaIsDynamic() }.getOrDefault(false)

                // Voor widgets zonder /P: pagina opzoeken via de annotatielijsten
                val pageLookup: Map<COSBase, Int> by lazy {
                    HashMap<COSBase, Int>().also { m ->
                        pdf.pages.forEachIndexed { i, p ->
                            runCatching { p.annotations }.getOrDefault(emptyList()).forEach { a -> m[a.cosObject] = i }
                        }
                    }
                }
                fun pageOf(w: PDAnnotationWidget): Int? =
                    w.page?.let { pdf.pages.indexOf(it) }?.takeIf { it >= 0 } ?: pageLookup[w.cosObject]

                for (field in form.fieldTree) {
                    if (field !is PDTerminalField) continue
                    val kind = when (field) {
                        is PDTextField -> FieldKind.TEXT
                        is PDCheckBox -> FieldKind.CHECKBOX
                        is PDRadioButton -> FieldKind.RADIO
                        is PDChoice -> FieldKind.CHOICE
                        else -> null // drukknoppen en digitale-handtekeningvelden
                    } ?: continue
                    val name = field.fullyQualifiedName ?: continue
                    val readOnly = field.isReadOnly

                    var value: String = when (field) {
                        is PDTextField -> field.value ?: ""
                        is PDChoice -> field.value.firstOrNull() ?: ""
                        else -> OFF
                    }

                    val exportValues = (field as? PDButton)?.exportValues.orEmpty()
                    field.widgets.forEachIndexed { i, w ->
                        if (w.isHidden || w.isNoView) return@forEachIndexed
                        val r = w.rectangle ?: return@forEachIndexed
                        val page = pageOf(w) ?: return@forEachIndexed
                        val pdfRect = RectF(r.lowerLeftX, r.upperRightY, r.upperRightX, r.lowerLeftY)

                        val widget = when (field) {
                            is PDTextField -> FormWidget(
                                fieldName = name, kind = kind, page = page, rect = NRect(0f, 0f, 0f, 0f), readOnly = readOnly,
                                multiline = field.isMultiline,
                                maxLen = field.maxLen,
                                password = field.isPassword,
                                fontSizePt = fontSize(field.defaultAppearance),
                                align = field.q,
                            )
                            is PDButton -> {
                                val on = exportValues.getOrNull(i) ?: onValueForWidget(w)
                                if (on.isEmpty()) return@forEachIndexed
                                // Huidige stand volgt uit de weergave van de widgets
                                if (w.appearanceState != null && w.appearanceState != COSName.Off) value = on
                                FormWidget(name, kind, page, NRect(0f, 0f, 0f, 0f), readOnly, optionValue = on)
                            }
                            is PDChoice -> {
                                val export = field.optionsExportValues
                                val display = field.optionsDisplayValues.takeIf { it.size == export.size } ?: export
                                FormWidget(
                                    fieldName = name, kind = kind, page = page, rect = NRect(0f, 0f, 0f, 0f), readOnly = readOnly,
                                    fontSizePt = fontSize(field.defaultAppearance),
                                    options = export.zip(display),
                                )
                            }
                            else -> return@forEachIndexed
                        }
                        raw += widget to pdfRect
                    }
                    initial[name] = value
                }
            } finally {
                pdf.close()
            }
        }

        if (raw.isEmpty()) return if (dynamicXfa) FormInfo(emptyList(), emptyMap(), true) else null

        // Posities per pagina omrekenen naar genormaliseerde schermcoördinaten
        val widgets = raw.groupBy { it.first.page }.flatMap { (page, list) ->
            val rects = doc.pdfRectsToNormalized(page, list.map { it.second })
            list.mapIndexed { i, (w, _) -> w.copy(rect = rects[i]) }
        }
        return FormInfo(widgets, initial, dynamicXfa)
    }

    private fun fontSize(da: String?): Float =
        da?.let { fontSizeRegex.find(it)?.groupValues?.get(1)?.toFloatOrNull() } ?: 0f

    private fun onValueForWidget(w: PDAnnotationWidget): String {
        val normal = w.appearance?.normalAppearance ?: return ""
        if (!normal.isSubDictionary) return ""
        return normal.subDictionary.keys.firstOrNull { it != COSName.Off }?.name ?: ""
    }
}
