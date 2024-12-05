package com.ziyoura.wemeet.presentation

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
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
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

// 首先修改 Json 配置
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
    ignoreUnknownKeys = true
    classDiscriminator = "type"  // 添加这一行，指定类型识别器
}

@Serializable
//data class Message(val user_id: Int, val message: String, val name: String, val message_time: String)
data class Message(val user_id: String, val message: String, val name: String, val message_time: String, val messageId: String = System.nanoTime().toString())


// 新增用于WebSocket的数据类
@Serializable
sealed class WebSocketEvent {
    abstract val type: String  // 添加抽象属性

    @Serializable
    @SerialName("chat")
    data class ChatMessage(
        override val type: String = "chat",
        val pinCode: String, 
        val userId: String,
        val name: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    @Serializable
    @SerialName("location")
    data class LocationUpdate(
        override val type: String = "location",
        val pinCode: String, 
        val userId: String,
        val username: String, // 添加username字段
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    @Serializable
    @SerialName("room")
    data class RoomEvent(
        override val type: String = "room",
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

    // 添加一个变量用于存储 valueFromLoadingEvents
    private var _eventLoadValue = mutableStateOf("")
    val eventLoadValue: String get() = _eventLoadValue.value

    // 添加设置方法
    fun setEventLoadValue(value: String) {
        _eventLoadValue.value = value
    }
    
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
//        Message("1", "Hello", "Alice", "2022-03-01 12:00:00"),
//        Message("2", "Hi", "Bob", "2022-03-01 12:01:00"),
//        Message("3", "How are you?", "Alice", "2022-03-01 12:02:00"),
//        Message("4", "I'm fine", "Bob", "2022-03-01 12:03:00"),
//        Message("5", "Good to hear that", "Alice", "2022-03-01 12:04:00"),
//        Message("6", "Bye", "Bob", "2022-03-01 12:05:00"),
    ))
    val messagesData = _messages.asStateFlow()

    private var webSocket: WebSocket? = null
    private var isConnected = mutableStateOf(false)
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var pongReceived = mutableStateOf(false)
    private val PING_INTERVAL = 15000L // 15秒
    private val PONG_TIMEOUT = 10000L // 10秒
    
    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .pingInterval(PING_INTERVAL, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(PONG_TIMEOUT, TimeUnit.MILLISECONDS) 
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // 将 List 改为 Map
    private val _otherUsersLocations = MutableStateFlow<Map<String, UserLocationInfo>>(
        emptyMap()
//        mapOf(
//            // 测试数据
//            "1" to UserLocationInfo("1", "Alice", LatLng(39.9042, 116.4074)),
//            "2" to UserLocationInfo("2", "Bob", LatLng(31.2304, 121.4737)),
//            "3" to UserLocationInfo("3", "Charlie", LatLng(23.1291, 113.2644))
//        )
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
            if (text == "pong") {
                pongReceived.value = true
                Log.d("WebSocket", "Pong received")
                return
            }
            
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
                                    .format(Date(event.timestamp)),
                                messageId = "${event.timestamp}_${System.nanoTime()}" // 使用时间戳和纳秒时间组合作为唯一标识
                            )
                            _messages.value += newMessage
                            Log.d("WebSocketListener", "Added new message: $newMessage")
                        }
                        is WebSocketEvent.LocationUpdate -> {
                            Log.d("WebSocketListener", "is WebSocketEvent.LocationUpdate")
                            // 检查是否是自己的位置更新
                            if (event.userId != userId) {
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
                            } else {
                                Log.d("LocationUpdate", "Ignored self location update")
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
            super.onFailure(webSocket, t, response)
            isConnected.value = false
            pingJob?.cancel() // 连接失败时停止心跳
            Log.e("WebSocket", "Connection failed", t)
            if (t is java.net.SocketTimeoutException) {
                Log.d("WebSocket", "Timeout occurred, attempting reconnection")
            }
            startReconnection()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            isConnected.value = true
            reconnectJob?.cancel()
            reconnectJob = null
            startPing() // 连接成功时启动心跳
            Log.d("WebSocket", "Connection opened")
        }
    }

    private fun startReconnection() {
        if (reconnectJob?.isActive == true) return
        
        reconnectJob = viewModelScope.launch {
            while (!isConnected.value) {
                try {
                    delay(5000) // 5秒重连间隔
                    Log.d("WebSocket", "Attempting to reconnect...")
                    connectWebSocket()
                } catch (e: Exception) {
                    Log.e("WebSocket", "Reconnection attempt failed", e)
                }
            }
        }
    }

    fun connectWebSocket() {
        if (webSocket != null) {
            webSocket?.cancel()
            webSocket = null
        }

        val request = Request.Builder()
            .url("ws://47.103.112.245:8000/ws/$pinCode?l=$eventLoadValue")
            .addHeader("Origin", "http://20.205.69.87:8000")  // 修改为实际服务器地址
            // 移除 Connection: close 头部
            .build()
        webSocket = client.newWebSocket(request, wsListener)
        startPing() // 启动心跳
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (isConnected.value) {
                try {
                    pongReceived.value = false
                    webSocket?.send("ping")
                    Log.d("WebSocket", "Ping sent")
                    
                    // 等待pong响应
                    delay(PONG_TIMEOUT)
                    if (!pongReceived.value) {
                        Log.e("WebSocket", "Pong not received, reconnecting...")
                        webSocket?.cancel()
                        break
                    }
                    
                    delay(PING_INTERVAL - PONG_TIMEOUT)
                } catch (e: Exception) {
                    Log.e("WebSocket", "Ping failed", e)
                    break
                }
            }
        }
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            Log.d("MyTag", "sendMessage up")
            // 发送��WebSocket
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
            Log.d("WeMeet", "location sent to server $locationUpdate")
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
        reconnectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "ViewModel cleared")
        webSocket = null
        isConnected.value = false
        viewModelScope.launch {
            // 停止位置更新
            locationDetection.stopLocationUpdates()
        }
    }
}
