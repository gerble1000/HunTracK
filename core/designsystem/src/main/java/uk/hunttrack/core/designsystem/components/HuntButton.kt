package uk.hunttrack.core.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.theme.HuntColors

@Composable
fun HuntButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HuntColors.Bronze,
            contentColor = HuntColors.Ink,
            disabledContainerColor = HuntColors.Slate,
            disabledContentColor = HuntColors.Mist,
        ),
    ) {
        Text(text)
    }
}

@Composable
fun HuntTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    color: Color = HuntColors.BronzeBright,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = color)
    }
}
