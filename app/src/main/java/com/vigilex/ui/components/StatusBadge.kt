package com.vigilex.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class StatusType {
    ACTIVE, ALERT, STATIONARY, COMPLETE, INACTIVE
}

@Composable
fun StatusBadge(type: StatusType) {
    val (bg, text, label) = when (type) {
        StatusType.ACTIVE      -> Triple(Color(0xFF1B4332), Color(0xFF4CAF50), "Active")
        StatusType.ALERT       -> Triple(Color(0xFF4A0E0E), Color(0xFFCF6679), "Alert")
        StatusType.STATIONARY  -> Triple(Color(0xFF3D2B00), Color(0xFFFF9800), "Stationary")
        StatusType.COMPLETE    -> Triple(Color(0xFF1A2533), Color(0xFF6B7280), "Complete")
        StatusType.INACTIVE    -> Triple(Color(0xFF1A2533), Color(0xFF6B7280), "No Trip")
    }

    Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = label,
            color = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
