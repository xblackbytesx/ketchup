package com.example.ketchup.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ketchup.ui.theme.KetchupRed
import com.example.ketchup.ui.theme.TextMuted

@Composable
fun PinInput(
    pin: String,
    pinLength: Int = 4,
    error: Boolean = false,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error) {
            for (i in 0 until 4) {
                shakeOffset.animateTo(
                    if (i % 2 == 0) 12f else -12f,
                    animationSpec = tween(50),
                )
            }
            shakeOffset.animateTo(0f, animationSpec = tween(50))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // PIN dots
        Row(
            modifier = Modifier
                .offset { IntOffset(shakeOffset.value.toInt(), 0) }
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(pinLength) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) KetchupRed
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val keys = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    TextButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(40.dp),
                    ) {
                        Text(
                            text = digit.toString(),
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Bottom row: empty, 0, backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(modifier = Modifier.size(80.dp))

            TextButton(
                onClick = { onDigit('0') },
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(40.dp),
            ) {
                Text(
                    text = "0",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
