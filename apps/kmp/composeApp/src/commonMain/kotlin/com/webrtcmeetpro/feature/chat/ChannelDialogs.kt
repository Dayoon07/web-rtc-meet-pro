package com.webrtcmeetpro.feature.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.model.ChatCategory
import com.webrtcmeetpro.core.model.ChatRoom
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_category
import com.webrtcmeetpro.resources.chat_channel_settings
import com.webrtcmeetpro.resources.chat_group_name
import com.webrtcmeetpro.resources.chat_no_category
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.common_save
import org.jetbrains.compose.resources.stringResource

/** 이름 하나 입력받는 창 (카테고리 만들기·이름 바꾸기) */
@Composable
fun NameDialog(title: String, label: String, initial: String = "", maxLength: Int = 50, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(maxLength) },
                label = { Text(label) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** 카테고리(부서) 고르기: "카테고리 없음" + 카테고리들 (가로로 넘기는 칩) */
@Composable
fun CategoryPicker(categories: List<ChatCategory>, selected: Long?, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.chat_category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(Res.string.chat_no_category)) })
            categories.forEach { c ->
                FilterChip(selected = selected == c.id, onClick = { onSelect(c.id) }, label = { Text(c.name) })
            }
        }
    }
}

/** 채널 설정: 이름 바꾸기 + 다른 카테고리로 옮기기 (워크스페이스 OWNER·ADMIN) */
@Composable
fun ChannelSettingsDialog(room: ChatRoom, categories: List<ChatCategory>, onSave: (name: String, categoryId: Long?) -> Unit, onDismiss: () -> Unit) {
    var name by remember(room.id) { mutableStateOf(room.name.orEmpty()) }
    var categoryId by remember(room.id) { mutableStateOf(room.categoryId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_channel_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text(stringResource(Res.string.chat_group_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(categories, categoryId, { categoryId = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), categoryId) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
