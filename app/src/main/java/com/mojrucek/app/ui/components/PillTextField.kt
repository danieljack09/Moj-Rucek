package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape

/**
 * Makes a [PillTextField] a password field with an eye toggle. The two
 * descriptions are what TalkBack reads for the toggle in each state.
 */
data class PasswordToggle(val showDescription: String, val hideDescription: String)

/**
 * Spec item 5: a greenContainer pill, 56dp tall. When empty, the [placeholder]
 * (or the [label] if no placeholder is given) sits centred vertically; once
 * filled, the [label] moves to a small labelSmall line above the value.
 *
 * Error: accentSoft fill at 40%, 1dp error border and [helperText] in the error
 * colour below. Without an error, [helperText] shows in textSecondary.
 * [password] turns on masking and an eye toggle; otherwise [trailingIcon] fills the
 * trailing slot (e.g. a clear button). With singleLine = false the pill becomes a
 * 20dp rounded rectangle that grows with the text. [containerColor] replaces the
 * greenContainer fill, e.g. a background-coloured field inside a greenContainer row.
 * A long placeholder may wrap to two lines rather than be clipped. [masked] hides the
 * text like a password field but without the eye toggle.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    helperText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: PasswordToggle? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    containerColor: Color = MojRucekTheme.colors.greenContainer,
    masked: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val shape = if (singleLine) PillShape else MaterialTheme.shapes.large
    val fill = if (isError) MojRucekTheme.colors.accentSoft.copy(alpha = 0.4f) else containerColor
    val textColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)

    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(fill)
                .then(if (isError) Modifier.border(1.dp, colors.error, shape) else Modifier)
                .semantics {
                    contentDescription = label
                    if (isError) error(helperText ?: label)
                },
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = if ((password != null && !passwordVisible) || (masked && password == null)) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = if (password != null && keyboardOptions == KeyboardOptions.Default)
                KeyboardOptions(keyboardType = KeyboardType.Password) else keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                val hasTrailing = password != null || trailingIcon != null
                Row(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = if (hasTrailing) 4.dp else 20.dp,
                        top = if (singleLine) 0.dp else 10.dp,
                        bottom = if (singleLine) 0.dp else 10.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (value.isNotEmpty()) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    placeholder ?: label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                    when {
                        password != null -> CircleIconButton(
                            icon = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) password.hideDescription else password.showDescription,
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = enabled,
                            containerColor = Color.Transparent
                        )
                        trailingIcon != null -> trailingIcon()
                    }
                }
            }
        )
        if (helperText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                helperText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun PillTextFieldPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var name by remember { mutableStateOf("") }
            PillTextField(name, { name = it }, label = "Име", placeholder = "Пилешко со ориз")
            PillTextField(
                "Спагети болоњезе", {}, label = "Име",
                trailingIcon = {
                    CircleIconButton(Icons.Outlined.Close, contentDescription = "Отстрани", onClick = {}, containerColor = Color.Transparent)
                }
            )
            PillTextField("abc", {}, label = "Е-пошта", isError = true, helperText = "Невалидна е-пошта")
            PillTextField(
                "tajna123", {}, label = "Лозинка",
                password = PasswordToggle(showDescription = "Прикажи лозинка", hideDescription = "Сокриј лозинка")
            )
            PillTextField("", {}, label = "Опис", singleLine = false, minLines = 3)
            PillTextField("Компир", {}, label = "Име", enabled = false)
        }
    }
}
