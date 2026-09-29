package com.qwaicode.persiansubtitles.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.qwaicode.persiansubtitles.domain.text.BidiText
import com.qwaicode.persiansubtitles.domain.text.TextFieldSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A text style whose paragraph direction is decided by the text itself: a Persian
 * line is laid out right-to-left, an English line left-to-right, and the final
 * period, question mark or bracket of each stays on the correct side. Used for
 * everything the user typed or the AI produced.
 */
fun TextStyle.autoDirection(): TextStyle =
    copy(textDirection = TextDirection.Content, textAlign = TextAlign.Start)

/** For values that are never Persian — API keys, file names, model ids, URLs. */
fun TextStyle.ltrDirection(): TextStyle =
    copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Start)

/**
 * The app's text input.
 *
 * ### Why this owns its text instead of just displaying `value`
 *
 * Typing used to scramble the line: `q` then `w` came out as `wq`, in Persian just
 * as much as in English. The reason is not direction — it is that every field here
 * is bound to a value that is persisted asynchronously (DataStore), so while the
 * user types, the value arriving from outside is one keystroke behind. Adopting it
 * dropped the last character and put the caret back at position zero.
 *
 * The field therefore keeps the [TextFieldValue] — text *and* caret — as its own
 * state and asks [TextFieldSync] which incoming values to ignore: its own echoes
 * are dropped however late they arrive, a genuine outside change (restore defaults,
 * a new project) is adopted. The caret is never moved by an echo.
 *
 * ### Direction and keyboard
 *
 * The direction follows the first strong character of the content, the same rule
 * the Unicode BiDi algorithm uses: type Persian and the field is right-to-left,
 * type English and caret, alignment and label flip to left-to-right. An empty field
 * stays Persian, which is the app's language. And the field asks to be scrolled
 * into view when it takes focus, after the keyboard insets have settled, so what is
 * being typed is never behind the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BidiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    /** Keeps the field left-to-right no matter what is typed (keys, file names). */
    forceLtr: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val requester = remember { BringIntoViewRequester() }

    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var pending by remember { mutableStateOf(emptyList<String>()) }

    val decision = TextFieldSync.onExternalValue(
        external = value,
        current = field.text,
        pending = pending,
    )
    if (decision.pending !== pending) pending = decision.pending
    if (decision.adopt) {
        field = TextFieldValue(value, TextRange(value.length))
    }

    val rtl = !forceLtr && BidiText.firstStrongIsRtl(field.text, default = true)
    val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        OutlinedTextField(
            value = field,
            onValueChange = { updated ->
                val textChanged = updated.text != field.text
                field = updated
                if (textChanged) {
                    pending = TextFieldSync.remember(pending, updated.text)
                    onValueChange(updated.text)
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .bringIntoViewRequester(requester)
                .onFocusEvent { state ->
                    if (state.isFocused) {
                        scope.launch {
                            // Wait for the keyboard to actually be up, otherwise the
                            // scroll target is computed against the old viewport.
                            delay(250)
                            runCatching { requester.bringIntoView() }
                        }
                    }
                },
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            textStyle = if (forceLtr) {
                LocalTextStyle.current.ltrDirection()
            } else {
                LocalTextStyle.current.autoDirection()
            },
            shape = MaterialTheme.shapes.small,
        )
    }
}
