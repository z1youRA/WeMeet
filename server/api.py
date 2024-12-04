from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from datetime import date, datetime
from fastapi.responses import JSONResponse
from fastapi.encoders import jsonable_encoder
from pydantic import BaseModel
from fastapi import Request
import json
from pymongo.mongo_client import MongoClient
from pymongo.server_api import ServerApi
from bson.json_util import dumps
from starlette.middleware.base import BaseHTTPMiddleware
from typing import List, Dict
import os
from dotenv import load_dotenv

# 加载.env文件
load_dotenv()

# 使用环境变量构建MongoDB URI
uri = f"mongodb+srv://{os.getenv('MONGODB_USERNAME')}:{os.getenv('MONGODB_PASSWORD')}@{os.getenv('MONGODB_CLUSTER')}/?retryWrites=true&w=majority&appName=Cluster0"
client = MongoClient(uri, server_api=ServerApi('1'))
db = client.Wemeet

# 定义事件的结构
class RoomEvent(BaseModel):
    type: str = "room"
    eventType: str
    userId: str
    name: str
    pinCode: str

class ChatMessage(BaseModel):
    type: str = "chat"
    pinCode: str
    userId: str
    name: str
    message: str
    timestamp: int

class LocationUpdate(BaseModel):
    type: str = "location"
    pinCode: str
    userId: str
    username: str
    latitude: float 
    longitude: float 
    timestamp: int


app = FastAPI()

@app.get("/")
async def read_root():
    return {"Hello": "World"}

# 房间管理类
class RoomManager:
    def __init__(self):
        self.rooms: Dict[str, List[WebSocket]] = {}  # 房间ID (pinCode) 到 WebSocket 列表的映射

    async def connect(self, websocket: WebSocket, pin_code: str):
        await websocket.accept()
        if pin_code not in self.rooms:
            self.rooms[pin_code] = []
        self.rooms[pin_code].append(websocket)

    def disconnect(self, websocket: WebSocket, pin_code: str):
        if pin_code in self.rooms:
            self.rooms[pin_code].remove(websocket)
            if not self.rooms[pin_code]:
                del self.rooms[pin_code]

    '''async def broadcast(self, pin_code: str, message: str):
        """向房间内所有用户发送消息"""
        if pin_code in self.rooms:
            for connection in self.rooms[pin_code]:
                await connection.send_text(message)'''

    async def broadcast(self, pin_code: str, user_id: str, name: str, message: str, timestamp: int):
        """
        向房间内所有用户发送消息。
        消息格式为 ChatMessage 类型，并序列化为 JSON 格式。
        """
        if pin_code in self.rooms:
            # 创建 ChatMessage 实例
            chat_message = ChatMessage(
                pinCode=pin_code,
                userId=user_id,
                name=name,
                message=message,
                timestamp=timestamp
            )
            # 序列化为 JSON 字符串
            json_message = chat_message.json()
            # 向房间内所有连接广播消息
            for connection in self.rooms[pin_code]:
                try:
                    await connection.send_text(json_message)
                    print(json_message)
                except Exception as e:
                    print(f"Failed to send message to a client: {e}")
        
    async def send_locations(self, pin_code: str, user_id: str, username: str, latitude: float, longitude:float, timestamp: int):
        '''向前端发送location信息 wait test
            class LocationUpdate(BaseModel):
                type: str = "location"
                userId: str
                username: str
                latitude: float 
                longitude: float 
                timestamp: int
        '''
        if pin_code in self.rooms:
            # 创建 ChatMessage 实例
            location_message = LocationUpdate(
                pinCode = pin_code,
                userId = user_id,
                username = username,
                latitude = latitude,
                longitude = longitude,
                timestamp = timestamp
            )
            # 序列化为 JSON 字符串
            json_message = location_message.json()
            print(json_message)
            # 向房间内所有连接广播消息
            for connection in self.rooms[pin_code]:
                try:
                    await connection.send_text(json_message)
                    print(json_message)
                except Exception as e:
                    print(f"Failed to send location to a client: {e}")


manager = RoomManager()


'''def get_messages(pinCode: str):
    # Query the messages for that chat room, sorted by time
    messages_cursor = db.messages.find(
        {"room_id": str(pinCode)},
        {"_id": 0, "userId": 1, "name": 1, "message": 1}
    ).sort("timestamp", 1)
    # Convert the cursor to a list for iteration
    messages = list(messages_cursor)
    data = {
        "data": {
            "messages": messages,
        },
        "status": "OK"
    }
    return JSONResponse(content=jsonable_encoder(data))'''
async def send_messages(pin_code: str):
    """
    按时间顺序逐条发送历史消息给客户端
    """
    try:
        # 查询数据库中的消息，按时间戳升序排序
        messages_cursor = db.messages.find(
            {"room_id": str(pin_code)},
            {"_id": 0, "userId": 1, "name": 1, "message": 1, "timestamp": 1}
        ).sort("timestamp", 1)
        # 逐条读取消息并发送
        for single_message in messages_cursor:
            user_id = single_message["userId"]
            name = single_message["name"]
            message = single_message["message"]
            timestamp = int(single_message["timestamp"])
            # 转换为 JSON 格式并发送
            await manager.broadcast(pin_code, str(user_id), name, message, timestamp)
    except Exception as e:
        print(f"Failed to send messages: {e}")



@app.websocket("/ws/{pin_code}")
async def websocket_endpoint(websocket: WebSocket, pin_code: str):
    await manager.connect(websocket, pin_code)
    print(f"User connected to room {pin_code}")

    try:
        while True:
            # 接收来自客户端的消息
            data = await websocket.receive_text()
            event = json.loads(data)  # 解析 JSON 数据

            #joinRoom
            if event.get("eventType") == "join":
                # 处理 join 事件
                user_id = event.get("userId")
                username = event.get("name")
                pinCode = event.get("pinCode")

                query = {"room_id": pinCode}
                room = db.rooms.find_one(query)
                if room:
                    print("已存在该聊天室")

                    # 读取当前房间的 user_num
                    user_num = int(room.get("user_num", 0))
                    # 删除旧的 room
                    db.rooms.delete_one(query)
                    # 更新 user_num
                    user_num += 1
                    # 重新插入更新后的 room
                    updated_room = {
                        "room_id": str(pinCode),
                        "user_num": str(user_num)
                    }
                    db.rooms.insert_one(updated_room)

                    #加载历史聊天消息
                    await send_messages(pinCode)
                else:
                    initial_room = {
                        "room_id": str(pinCode),
                        "user_num": "1"
                    }
                    db.rooms.insert_one(initial_room)
                    print(f"新建聊天室{pinCode}")

                print(f"User {username} ({str(user_id)}) joined room {pin_code}")


            #sendMessage
            if event.get("message"):
                '''
                ChatMessage:
                val type: String = "chat",
                val pinCode: String,
                val userId: String,
                val name: String,
                val message: String,
                val timestamp: Long = System.currentTimeMillis()
                '''
                # 接收消息
                print("receive one message")

                pinCode = event.get("pinCode")
                userId = event.get("userId")
                name = event.get("name")
                message_content = event.get("message")
                if event.get("timestamp"):
                    timestamp = event.get("timestamp")
                else:
                    current_timestamp = int(datetime.now().timestamp() * 1000)
                    timestamp = current_timestamp

                print(f"{pinCode} got one message {message_content}")

                message = {
                    "room_id": str(pinCode),
                    "userId": str(userId),
                    "name": str(name),
                    "message": str(message_content),
                    "timestamp": int(timestamp),
                }
                db.messages.insert_one(message)
                await manager.broadcast(str(pin_code), str(userId), str(name), str(message_content), int(timestamp))


            #sendLocationToServer
            if event.get("latitude"):
                '''接收位置信息，保存数据库'''
                pinCode = event.get("pinCode")
                userId = event.get("userId")
                username = event.get("username")
                latitude = event.get("latitude") 
                longitude = event.get("longitude") 
                if event.get("timestamp"):
                    timestamp = event.get("timestamp")
                else:
                    current_timestamp = int(datetime.now().timestamp() * 1000)
                    timestamp = current_timestamp

                location = {
                    "room_id": str(pinCode),
                    "userId": str(userId),
                    "username": str(username),
                    "latitude": float(latitude),
                    "longitude": float(longitude),
                    'timestamp': int(timestamp)
                }
                db.locations.insert_one(location)
                await manager.send_locations(str(pin_code), str(userId), str(username), float(latitude), float(longitude), int(timestamp))


            #leaveRoom
            if event.get("eventType") == "leave":
                pinCode = event.get("pinCode")

                query = {"room_id": pinCode}
                room = db.rooms.find_one(query)
                if room:
                    # 读取当前房间的 user_num
                    user_num = int(room.get("user_num", 0))
                    # 删除旧的 room
                    db.rooms.delete_one(query)
                    # 更新 user_num
                    user_num -= 1
                    if user_num != 0:
                        # 重新插入更新后的 room
                        updated_room = {
                            "room_id": str(pinCode),
                            "user_num": str(user_num)
                        }
                        db.rooms.insert_one(updated_room)
                    else:
                        '''如果退出后房间中没有用户了就删除该房间'''
                        db.rooms.delete_one(query)

    
    except WebSocketDisconnect:
        manager.disconnect(websocket, pin_code)
        print(f"User disconnected from room {pin_code}")

