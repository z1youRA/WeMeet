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

package com.ziyoura.wemeet.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.ziyoura.wemeet.presentation.WeMeetViewModel


/**
 * A composable to render an application top bar with a title, zoom all button, and a switch to
 * toggle between seeing all of the mountains or just a subset.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WeMeetTopBar(
    topBarTitleStringRes: String,
    onBackClick: () -> Unit // 添加一个回调参数，用于处理返回操作
) {
    TopAppBar(
        title = { Text(topBarTitleStringRes) },
        navigationIcon = {
            //IconButton(onClick = { /* 处理返回操作 */ }) {
            IconButton(onClick = (onBackClick)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    )
}



