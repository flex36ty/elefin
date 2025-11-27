package com.flex.elefin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Input color scheme matching Jellyfin AndroidTV
 * Based on jellyfin-androidtv/app/src/main/res/values/colors.xml
 */
object InputColors {
    // Border/stroke color - always visible
    val stroke = Color(0xB3747474)  // 70% opacity gray
    
    // Background colors
    val normalBackground = Color.Transparent
    val disabledBackground = Color(0x33747474)  // 20% opacity gray
    val highlightBackground = Color(0xFFDDDDDD)  // Light gray when focused
    
    // Text colors
    val normalText = Color(0xFFDDDDDD)  // Light text when unfocused
    val disabledText = Color(0xFF686868)  // Dim text when disabled
    val highlightText = Color(0xFF444444)  // Dark text when focused
    
    // Input rounding
    val rounding = 3.dp
    val strokeWidth = 2.dp
}

/**
 * TV-friendly TextField using BasicTextField with Jellyfin AndroidTV styling.
 * 
 * This implementation makes the BasicTextField DIRECTLY focusable (like native EditText)
 * without a wrapper Box. This allows:
 * - D-pad navigation to directly select the text field
 * - Keyboard to show automatically when focused
 * - IME actions (Next/Done) to work properly
 * 
 * Uses MutableInteractionSource with collectIsFocusedAsState() for focus detection
 * and decorationBox for text field decoration - matching the official Jellyfin AndroidTV approach.
 * 
 * @see <a href="https://github.com/jellyfin/jellyfin-androidtv">Jellyfin AndroidTV</a>
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    // Use MutableInteractionSource for focus detection - Jellyfin AndroidTV approach
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    
    // Notify parent of focus changes
    LaunchedEffect(focused) {
        onFocusChanged?.invoke(focused)
    }
    
    // Determine colors based on focus and enabled state
    val backgroundColor = when {
        !enabled -> InputColors.disabledBackground
        focused -> InputColors.highlightBackground
        else -> InputColors.normalBackground
    }
    
    val textColor = when {
        !enabled -> InputColors.disabledText
        focused -> InputColors.highlightText
        else -> InputColors.normalText
    }
    
    val borderColor = InputColors.stroke
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Label above the text field
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // BasicTextField - DIRECTLY focusable like native EditText
        // No wrapper Box needed - the text field itself receives D-pad focus
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                ),
            enabled = enabled,
            singleLine = true,
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions.copy(
                // Ensure IME action is set (like android:imeOptions in XML)
                imeAction = if (keyboardOptions.imeAction == ImeAction.Default) 
                    ImeAction.Done 
                else 
                    keyboardOptions.imeAction
            ),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            textStyle = TextStyle(
                color = textColor,
                fontSize = 16.sp
            ),
            cursorBrush = SolidColor(if (focused) InputColors.highlightText else textColor),
            decorationBox = { innerTextField ->
                // Decoration box wraps the text field content - Jellyfin AndroidTV style
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = backgroundColor,
                            shape = RoundedCornerShape(InputColors.rounding)
                        )
                        .border(
                            width = InputColors.strokeWidth,
                            color = borderColor,
                            shape = RoundedCornerShape(InputColors.rounding)
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        // Show placeholder when empty
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    color = if (focused) 
                                        InputColors.highlightText.copy(alpha = 0.6f)
                                    else 
                                        InputColors.normalText.copy(alpha = 0.6f),
                                    fontSize = 16.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

/**
 * Search-specific text input matching Jellyfin AndroidTV's SearchTextInput
 * Uses rounded pill shape with search icon
 */
@Composable
fun TvSearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onQuerySubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    
    // Colors matching Jellyfin AndroidTV
    val borderColor = if (focused) InputColors.highlightBackground else InputColors.stroke
    val textColor = if (focused) InputColors.highlightText else InputColors.normalText
    val backgroundColor = if (focused) InputColors.highlightBackground else InputColors.normalBackground
    
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            ),
        singleLine = true,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onQuerySubmit() }
        ),
        textStyle = TextStyle(
            color = textColor,
            fontSize = 18.sp
        ),
        cursorBrush = SolidColor(borderColor),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(percent = 30) // Pill shape like Jellyfin
                    )
                    .border(
                        width = InputColors.strokeWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(percent = 30)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 18.sp
                            )
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}
