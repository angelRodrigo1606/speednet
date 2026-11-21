package com.angel.speednet.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.angel.speednet.domain.model.ScoreLevel
import com.angel.speednet.ui.levelColor
import com.angel.speednet.ui.levelLabel

@Composable
fun LevelBadge(level: ScoreLevel, modifier: Modifier = Modifier) {
    val color = levelColor(level)
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = levelLabel(level),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
