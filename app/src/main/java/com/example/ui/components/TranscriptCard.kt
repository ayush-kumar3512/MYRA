package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.TranscriptItem

@Composable
fun TranscriptCard(
    item: TranscriptItem,
    modifier: Modifier = Modifier
) {
    val isUser = item.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("transcript_item_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            if (!isUser) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEC4899))
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "MYRA",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MYRA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF472B6),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7))
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Message Bubble
        Card(
            shape = RoundedCornerShape(
                topStart = if (!isUser) 4.dp else 16.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF1E293B) else Color(0xFF281C3E)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF1F5F9),
                    lineHeight = 20.sp
                )

                // If this item represents a device action, show an action status badge
                item.actionResult?.let { action ->
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceActionBadge(
                        actionName = action.action,
                        success = action.success,
                        message = action.message
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceActionBadge(
    actionName: String,
    success: Boolean,
    message: String,
    modifier: Modifier = Modifier
) {
    val bgColor = if (success) Color(0xFF064E3B) else Color(0xFF7F1D1D)
    val textColor = if (success) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
    val icon = when (actionName) {
        "openWhatsApp", "openApp" -> Icons.AutoMirrored.Filled.OpenInNew
        "makeCall", "callContact" -> Icons.Default.Call
        "openUrl" -> Icons.Default.Language
        "toggleFlashlight" -> Icons.Default.FlashOn
        "setAlarm", "setTimer" -> Icons.Default.Alarm
        "openNotificationSettings" -> Icons.Default.Notifications
        else -> if (success) Icons.Default.CheckCircle else Icons.Default.Error
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("action_badge_$actionName")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$actionName: $message",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}
