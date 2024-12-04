package com.ziyoura.wemeet.presentation

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

// 配置 Json 实例和 SerializersModule
val json = Json {
    serializersModule = SerializersModule {
        polymorphic(WebSocketEvent::class) {
            subclass(WebSocketEvent.ChatMessage::class, WebSocketEvent.ChatMessage.serializer())
            subclass(WebSocketEvent.LocationUpdate::class, WebSocketEvent.LocationUpdate.serializer())
            subclass(WebSocketEvent.RoomEvent::class, WebSocketEvent.RoomEvent.serializer())
        }
    }
    encodeDefaults = true
    isLenient = true
    prettyPrint = true
}

@Serializable
//data class Message(val user_id: Int, val message: String, val name: String, val message_time: String)
data class Message(val user_id: String, val message: String, val name: String, val message_time: String)


// 新增用于WebSocket的数据类
@Serializable
sealed class WebSocketEvent {
    @SerialName("chat")////////////////////////////////////////////////////////////////////////////
    @Serializable
    data class ChatMessage(
        val type: String = "chat",
        val pinCode: String, ////////////////////////////////////////////////////////////////////新加的
        val userId: String,
        val name: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    @Serializable
    data class LocationUpdate(
        val type: String = "location",
        val pinCode: String, ////////////////////////////////////////////////////////////////////新加的
        val userId: String,
        val username: String, // 添加username字段
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    @Serializable
    data class RoomEvent(
        val type: String = "room",
        val eventType: String, // "join" or "leave"
        val userId: String,
        val name: String,
        val pinCode: String
    ) : WebSocketEvent()
}

// 添加数据类
data class UserLocationInfo(
    val userId: String,
    val username: String,
    val location: LatLng
)

class WeMeetViewModel(application: Application) : AndroidViewModel(application){
    private var _pinCode = mutableStateOf("0000")
    val pinCode: String get() = _pinCode.value

    private var _username = mutableStateOf("newUser")
    val username: String get() = _username.value

    private var _userId = mutableStateOf("")
    val userId: String get() = _userId.value

    
    fun setPinCode(newPinCode: String) {
        _pinCode.value = newPinCode
    }

    fun setUsername(newUsername: String) {
        // 设置用户名
        _username.value = newUsername
    }

    fun setUserId(newUserId: String) {
        // 设置用户ID
        _userId.value = newUserId
    }

    private val _messages = MutableStateFlow<List<Message>>(listOf(
        Message("1", "Hello", "Alice", "2022-03-01 12:00:00"),
        Message("2", "Hi", "Bob", "2022-03-01 12:01:00"),
        Message("3", "How are you?", "Alice", "2022-03-01 12:02:00"),
        Message("4", "I'm fine", "Bob", "2022-03-01 12:03:00"),
        Message("5", "Good to hear that", "Alice", "2022-03-01 12:04:00"),
        Message("6", "Bye", "Bob", "2022-03-01 12:05:00"),
    ))
    val messagesData = _messages.asStateFlow()


    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    // 将 List 改为 Map
    private val _otherUsersLocations = MutableStateFlow<Map<String, UserLocationInfo>>(
        mapOf(
            // 测试数据
            "1" to UserLocationInfo("1", "Alice", LatLng(39.9042, 116.4074)),
            "2" to UserLocationInfo("2", "Bob", LatLng(31.2304, 121.4737)),
            "3" to UserLocationInfo("3", "Charlie", LatLng(23.1291, 113.2644))
        )
    )
    val otherUsersLocations = _otherUsersLocations.asStateFlow()

    // 添加位置相关状态
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Initial)
    val locationState = _locationState.asStateFlow()

    // 位置检测器
    private val locationDetection = LocationDetection(getApplication<Application>().applicationContext)

    // 位置状态
    sealed class LocationState {
        data object Initial : LocationState()
        data object Loading : LocationState()
        data class Success(val location: Location) : LocationState()
        data class Error(val message: String) : LocationState()
    }

    private val wsListener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d("WebSocketListener", "Received message: $text")
            viewModelScope.launch {
                try {
                    when (val event = Json.decodeFromString<WebSocketEvent>(text)) {
                        is WebSocketEvent.ChatMessage -> {
                            Log.d("WebSocketListener", "is WebSocketEvent.ChatMessage")
                            val newMessage = Message(
                                //user_id = event.userId.toInt(),
                                user_id = event.userId,
                                message = event.message,
                                name = event.name,
                                message_time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                    .format(Date(event.timestamp))
                            )
                            _messages.value += newMessage
                            Log.d("WebSocketListener", "Added new message: $newMessage")
                        }
                        is WebSocketEvent.LocationUpdate -> {
                            Log.d("WebSocketListener", "is WebSocketEvent.LocationUpdate")
                            val newLocation = UserLocationInfo(
                                userId = event.userId,
                                username = event.username,
                                location = LatLng(event.latitude, event.longitude)
                            )
                            // 使用 Map 更新位置并添加日志
                            _otherUsersLocations.value = _otherUsersLocations.value.toMutableMap().apply {
                                put(event.userId, newLocation)
                                Log.d("LocationUpdate", "Updated location for user: ${event.username}")
                                Log.d("LocationUpdate", "Current users on map: ${keys.joinToString()}")
                            }
                        }
                        is WebSocketEvent.RoomEvent -> TODO()
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Error parsing message", e)
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("WebSocket", "Connection closed: $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("WebSocket", "Connection failed", t)
            // 尝试重新连接
            reconnectWebSocket()
        }
    }

    //private fun connectWebSocket() {
    fun connectWebSocket() {
        val request = Request.Builder()
            //.url("ws://your-websocket-server-url/ws/$pinCode") // 替换为实际的WebSocket服务器地址
            .url("ws://192.168.0.128:55722/ws/$pinCode")
            .build()
        webSocket = client.newWebSocket(request, wsListener)
    }

    private fun reconnectWebSocket() {
        viewModelScope.launch {
            delay(5000) // 等待5秒后重连
            connectWebSocket()
        }
    }

    fun sendMessage(message: String) {
        //OK, 发送到服务端，保存到数据库
        viewModelScope.launch {
            Log.d("MyTag", "sendMessage up")
            // 发送到WebSocket
            val chatMessage = WebSocketEvent.ChatMessage(
                type = "chat",
                pinCode = pinCode,
                userId = userId,
                name = username,
                message = message,
                timestamp = System.currentTimeMillis()
            )
            Log.d("MyTag", "chatMessage up")
            val jsonString = Json.encodeToString(WebSocketEvent.ChatMessage.serializer(), chatMessage)

            Log.d("MyTag", "chatMessage: $jsonString")
            webSocket?.send(Json.encodeToString(chatMessage))
        }
    }

    private fun sendLocationToServer(location: Location) {
        //wait test
        viewModelScope.launch {
            val locationUpdate = WebSocketEvent.LocationUpdate(
                pinCode = pinCode,
                userId = userId,
                username = username, // 发送位置时包含用户名
                latitude = location.latitude,
                longitude = location.longitude
            )
            webSocket?.send(Json.encodeToString(locationUpdate))
        }
    }

    fun joinRoom() {
        val joinEvent = WebSocketEvent.RoomEvent(
            eventType = "join",
            userId = userId,
            name = username,
            pinCode = pinCode
        )
        webSocket?.send(Json.encodeToString(joinEvent))
    }

    fun leaveRoom() {
        val leaveEvent = WebSocketEvent.RoomEvent(
            eventType = "leave",
            userId = userId,
            name = username,
            pinCode = pinCode
        )
        webSocket?.send(Json.encodeToString(leaveEvent))
        webSocket?.close(1000, "Leaving room")
        webSocket = null
        _otherUsersLocations.value = emptyMap() // 清空时使用 emptyMap
    }


    // 开始位置更新
    fun startLocationUpdates(interval: Long) {
        viewModelScope.launch {
            try {
                _locationState.value = LocationState.Loading

                if (!locationDetection.hasLocationPermission()) {
                    _locationState.value = LocationState.Error("需要位置权限")
                    return@launch
                }

                if (!locationDetection.isLocationEnabled()) {
                    _locationState.value = LocationState.Error("请开启GPS")
                    return@launch
                }

                // 获取位置更新
                locationDetection.getLocationUpdates(interval)
                    .catch { e ->
                        _locationState.value = LocationState.Error(e.message ?: "位置获取失败")
                    }
                    .collect { location ->
                        _locationState.value = LocationState.Success(location)
                        // 可以在这里发送位置信息到服务器
                        sendLocationToServer(location)
                    }
            } catch (e: Exception) {
                _locationState.value = LocationState.Error(e.message ?: "位置获取失败")
            }
        }
    }

    // 获取单次位置
    fun getCurrentLocation() {
        viewModelScope.launch {
            try {
                _locationState.value = LocationState.Loading
                val location = locationDetection.getLastLocation()
                if (location != null) {
                    _locationState.value = LocationState.Success(location)
                    sendLocationToServer(location)
                } else {
                    _locationState.value = LocationState.Error("无法获取位置")
                }
            } catch (e: Exception) {
                _locationState.value = LocationState.Error(e.message ?: "位置获取失败")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocket?.close(1000, "ViewModel cleared")
        webSocket = null
        viewModelScope.launch {
            // 停止位置更新
            locationDetection.stopLocationUpdates()
        }
    }
}
