package com.ziyoura.wemeet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ziyoura.wemeet.presentation.Message
import com.ziyoura.wemeet.presentation.WeMeetViewModel

@Composable
fun ChatRoom(viewModel: WeMeetViewModel) {
//    var isChatRoomLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            ChatInputField(onMessageSent = { text -> run {
                viewModel.sendMessage(text)

            } })
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            ChatRecordContent(viewModel, rememberLazyListState())
        }
    }

}

@Composable
fun ChatRecordContent(
    viewModel: WeMeetViewModel,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val chatMessages by viewModel.messagesData.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = lazyListState
    ) {
        items(chatMessages, key = { "${it.name}_${it.message_time}" }) { message ->
            val isCurrentUser = message.name == viewModel.username // 根据实际需求修改判断逻辑
            val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
            val bubbleBackgroundColor = if (isCurrentUser) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer
            val bubbleTextColor = if (isCurrentUser)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSecondaryContainer

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = alignment
            ) {
                ChatBubble(
                    message = message,
                    backgroundColor = bubbleBackgroundColor,
                    textColor = bubbleTextColor
                )
            }
        }
    }
}

@Composable
fun ChatInputField(onMessageSent: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(30)
                )
        )

        Button(onClick = {
            if (text.isNotBlank())
                onMessageSent(text)
            text = ""
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send"
            )
        }
    }
}

@Composable
fun ChatBubble(message: Message, backgroundColor: Color, textColor: Color) {
    Column (
        modifier = Modifier
            .widthIn(150.dp, 300.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(30)
            )
            .background(backgroundColor, shape = RoundedCornerShape(30))
            .padding(15.dp)
    ){
        Text(
            color = textColor,
            text = "User: " + message.name
        )
        Text(
            color = textColor,
            text = message.message
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            modifier = Modifier.align(Alignment.End),
            color = textColor,
//            text = SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(message.message_time)
            text = message.message_time
        )
    }

}