package com.ziyoura.wemeet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ziyoura.wemeet.presentation.PairingViewModel
import com.ziyoura.wemeet.ui.theme.WeMeetTheme
import java.util.UUID


class PairingActivity : ComponentActivity() {
    private fun getUserId(): String {
        val prefs = getSharedPreferences("WeMeet", MODE_PRIVATE)
        var userId = prefs.getString("userId", null)
        
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            prefs.edit().putString("userId", userId).apply()
        }
        
        return userId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel: PairingViewModel by viewModels()
        val userId = getUserId()
        
        setContent {
            WeMeetTheme(dynamicColor = false) {
                PairingScreen(
                    onPinComplete = { pinCode, username ->
                        viewModel.joinChatroom(pinCode)

                        val intent = Intent(this@PairingActivity, MainActivity::class.java).apply {
                            putExtra("pinCode", pinCode)
                            putExtra("username", username)
                            putExtra("userId", userId)
                        }

                        startActivity(intent)

                    }
                )
            }
        }
    }
}

@Composable
fun PairingScreen(
    onPinComplete: (String, String) -> Unit
) {
    var pinCode by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "输入您的用户名",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        BasicTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier
                .padding(bottom = 32.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Text(
            text = "输入4位数字码",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        PinInput(
            pin = pinCode,
            onPinChange = { newPin ->
                if (newPin.length <= 4 && newPin.all { it.isDigit() }) {
                    pinCode = newPin
                    if (newPin.length == 4 && username.isNotBlank()) {
                        onPinComplete(newPin, username)
                    }
                }
            }
        )
    }
}

@Composable
fun PinInput(
    pin: String,
    onPinChange: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val digitWidth = 56.dp
    val spacing = 8.dp
    val totalWidth = (digitWidth * 4) + (spacing * 3) // 4个数字框的宽度加上3个间距

    Box(contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            repeat(4) { index ->
                PinDigit(
                    digit = pin.getOrNull(index)?.toString() ?: "",
                    isFocused = pin.length == index
                )
            }
        }

        BasicTextField(
            value = pin,
            onValueChange = onPinChange,
            modifier = Modifier
                .alpha(0.01f)
                .size(width = totalWidth, height = digitWidth),
            textStyle = TextStyle.Default.copy(color = Color.Transparent),

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
            }),
            cursorBrush = SolidColor(Color.Transparent),

        )
    }
}

@Composable
fun PinDigit(
    digit: String,
    isFocused: Boolean
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}