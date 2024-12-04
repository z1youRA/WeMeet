package com.ziyoura.wemeet.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ParingViewModel"

class PairingViewModel: ViewModel() {
    private val _uiState = MutableStateFlow<PairingState>(PairingState.Initial)
    val uiState = _uiState.asStateFlow()

    sealed class PairingState {
        object Initial : PairingState()
        object Loading : PairingState()
        data class Success(val chatroomId: String) : PairingState()
        data class Error(val message: String) : PairingState()
    }

    fun joinChatroom(pinCode: String) {
        Log.d(TAG, "Joining chatroom with pin code: $pinCode")
        viewModelScope.launch {
            _uiState.value = PairingState.Loading
            try {
                    //在WeMeetViewModel中joinroom

            } catch (e: Exception) {
                _uiState.value = PairingState.Error(e.message ?: "配对失败")
            }
        }
    }

//    private suspend fun joinOrCreateChatroom(pinCode: String): ChatroomResponse {
//        return withContext(Dispatchers.IO) {
//            // 实现你的API调用
//            // 返回聊天室信息
//        }
//    }
}
