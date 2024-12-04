// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ziyoura.wemeet

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.ziyoura.wemeet.presentation.WeMeetViewModel
import com.ziyoura.wemeet.ui.ChatRoom
import com.ziyoura.wemeet.ui.WeMeetMap
import com.ziyoura.wemeet.ui.WeMeetTopBar
import com.ziyoura.wemeet.ui.theme.WeMeetTheme

const val interval = 1000L

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    val viewModel: WeMeetViewModel by viewModels()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // 精确位置权限已获取
                viewModel.startLocationUpdates(interval)
                Log.e("location", "Location get")
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // 粗略位置权限已获取
                viewModel.startLocationUpdates(interval)
                Log.e("location", "Location get")
            }
            else -> {
                // 显示权限被拒绝对话框
                Log.e("WeMeet", "Location permission denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 获取 pincode
        val pinCode = intent.getStringExtra("pinCode")
        val username = intent.getStringExtra("username")
        val userId = intent.getStringExtra("userId")

        if (pinCode != null) {
            viewModel.setPinCode(pinCode)
            viewModel.setUsername(username ?: "")
            viewModel.setUserId(userId ?: "")
        } else {
            // 未获取到 pincode，返回 PairingActivity
            finish()
        }

        viewModel.connectWebSocket()
        viewModel.joinRoom()
        
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        setContent {
            WeMeetTheme(dynamicColor = false) {
                //WeMeetScreen(viewModel)
                WeMeetScreen(viewModel = viewModel,
                    onBackPressed = {
                        viewModel.leaveRoom()
                        finish()
                    }) // 调用 `finish` 关闭当前页面
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
//fun WeMeetScreen(viewModel: WeMeetViewModel) {
fun WeMeetScreen(viewModel: WeMeetViewModel, onBackPressed: () -> Unit) { // 添加参数用于处理返回
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 128.dp,
        topBar = {
            WeMeetTopBar(
                topBarTitleStringRes = "ROOM" + viewModel.pinCode,
                onBackClick = onBackPressed
            )
        },
        sheetContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChatRoom(viewModel = viewModel)
            }
        }
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            WeMeetMap(viewModel)
        }
    }
}