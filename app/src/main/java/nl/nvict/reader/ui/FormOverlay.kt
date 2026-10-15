package nl.nvict.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import nl.nvict.reader.pdf.FieldKind
import nl.nvict.reader.pdf.FormWidget
import nl.nvict.reader.pdf.OFF
import kotlin.math.roundToInt

/** Lichtblauwe markering van invulvelden, zoals in Acrobat en Fill & Sign. */
private val FieldTint = Color(0x332F80ED)
/** Achtergrond zodra de app het veld zelf tekent (gewijzigd of aan het typen): dekt de oude weergave af. */
private val FieldEditing = Color(0xFFE8F0FE)
private val FieldText = Color(0xFF111111)

/**
 * Tikbare invulvelden boven de gerenderde pagina. Zolang een veld niet gewijzigd is, toont de pagina zelf
 * de waarde (met een lichte markering); na wijzigen tekent de app de nieuwe waarde tot het document is opgeslagen.
 */
@Composable
internal fun FormOverlay(
    state: DocState,
    vm: ReaderViewModel,
    pageIndex: Int,
    widthPx: Int,
    heightPx: Int,
    pageWidthPt: Float,
) {
    val form = state.form ?: return
    val widgets = remember(form, pageIndex) { form.widgets.filter { it.page == pageIndex && !it.readOnly } }
    if (widgets.isEmpty()) return
    val density = LocalDensity.current

    widgets.forEach { w ->
        key(w.fieldName, w.rect, w.optionValue) {
            val left = (w.rect.left * widthPx).roundToInt()
            val top = (w.rect.top * heightPx).roundToInt()
            val wDp: Dp = with(density) { (w.rect.width * widthPx).toDp() }
            val hDp: Dp = with(density) { (w.rect.height * heightPx).toDp() }
            val value = state.formValues[w.fieldName] ?: ""
            val changed = value != form.initialValues[w.fieldName]

            Box(
                modifier = Modifier
                    .offset { IntOffset(left, top) }
                    .size(wDp, hDp),
            ) {
                when (w.kind) {
                    FieldKind.TEXT -> TextFieldBox(state, vm, w, value, changed, widthPx, heightPx, pageWidthPt)
                    FieldKind.CHECKBOX, FieldKind.RADIO -> ToggleBox(state, vm, w, value, changed)
                    FieldKind.CHOICE -> ChoiceBox(state, vm, w, value, changed, widthPx, heightPx, pageWidthPt)
                }
            }
        }
    }
}

/** Lettergrootte in pixels: uit het formulier, of passend bij de veldhoogte. */
private fun fontPx(w: FormWidget, widthPx: Int, heightPx: Int, pageWidthPt: Float): Float {
    val scale = widthPx / pageWidthPt
    return when {
        w.fontSizePt > 0f -> w.fontSizePt * scale
        w.multiline -> 10f * scale
        else -> (w.rect.height * heightPx * 0.62f).coerceAtMost(14f * scale)
    }
}

@Composable
private fun BoxScope.TextFieldBox(
    state: DocState,
    vm: ReaderViewModel,
    w: FormWidget,
    value: String,
    changed: Boolean,
    widthPx: Int,
    heightPx: Int,
    pageWidthPt: Float,
) {
    var focused by remember { mutableStateOf(false) }
    var typedSinceFocus by remember { mutableStateOf(false) }
    val showOwn = focused || changed
    val sizeSp = with(LocalDensity.current) { fontPx(w, widthPx, heightPx, pageWidthPt).toSp() }

    BasicTextField(
        value = value,
        onValueChange = { input ->
            var v = if (w.multiline) input else input.replace("\n", "")
            if (w.maxLen > 0) v = v.take(w.maxLen)
            vm.setFieldValue(state, w.fieldName, v, newUndoStep = !typedSinceFocus)
            typedSinceFocus = true
        },
        singleLine = !w.multiline,
        textStyle = TextStyle(
            fontSize = sizeSp,
            color = if (showOwn) FieldText else Color.Transparent,
            textAlign = when (w.align) {
                1 -> TextAlign.Center
                2 -> TextAlign.End
                else -> TextAlign.Start
            },
        ),
        visualTransformation = if (w.password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (w.password) KeyboardType.Password else KeyboardType.Text,
            imeAction = if (w.multiline) ImeAction.Default else ImeAction.Done,
        ),
        cursorBrush = SolidColor(NVictColors.Accent),
        modifier = Modifier
            .matchParentSize()
            .background(if (showOwn) FieldEditing else FieldTint)
            .then(if (focused) Modifier.border(1.5.dp, NVictColors.Accent) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) typedSinceFocus = false
            }
            .padding(horizontal = 2.dp),
        decorationBox = { inner ->
            Box(
                contentAlignment = if (w.multiline) Alignment.TopStart else Alignment.CenterStart,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxWidth()) { inner() }
            }
        },
    )
}

@Composable
private fun BoxScope.ToggleBox(state: DocState, vm: ReaderViewModel, w: FormWidget, value: String, changed: Boolean) {
    val option = w.optionValue ?: return
    val on = value == option
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .matchParentSize()
            .background(if (changed) FieldEditing else FieldTint)
            .clickable {
                val new = if (w.kind == FieldKind.CHECKBOX && on) OFF else option
                vm.setFieldValue(state, w.fieldName, new)
            },
    ) {
        // Na wijzigen tekent de app de stand zelf (de oude weergave is afgedekt)
        if (changed && on) {
            if (w.kind == FieldKind.CHECKBOX) {
                Icon(Icons.Default.Check, null, tint = FieldText, modifier = Modifier.fillMaxSize().padding(1.dp))
            } else {
                Box(Modifier.fillMaxSize(0.5f).background(FieldText, CircleShape))
            }
        }
    }
}

@Composable
private fun BoxScope.ChoiceBox(
    state: DocState,
    vm: ReaderViewModel,
    w: FormWidget,
    value: String,
    changed: Boolean,
    widthPx: Int,
    heightPx: Int,
    pageWidthPt: Float,
) {
    var open by remember { mutableStateOf(false) }
    val sizeSp = with(LocalDensity.current) { fontPx(w, widthPx, heightPx, pageWidthPt).toSp() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .matchParentSize()
            .background(if (changed) FieldEditing else FieldTint)
            .clickable { open = true },
    ) {
        if (changed) {
            val display = w.options.firstOrNull { it.first == value }?.second ?: value
            Text(
                display,
                fontSize = sizeSp,
                color = FieldText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 18.dp),
            )
        }
        Icon(
            Icons.Default.ArrowDropDown,
            null,
            tint = NVictColors.Accent,
            modifier = Modifier.align(Alignment.CenterEnd).size(18.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            w.options.forEach { (export, display) ->
                DropdownMenuItem(
                    text = { Text(display.ifBlank { " " }) },
                    leadingIcon = if (export == value) ({ Icon(Icons.Default.Check, null) }) else null,
                    onClick = {
                        open = false
                        vm.setFieldValue(state, w.fieldName, export)
                    },
                )
            }
        }
    }
}
