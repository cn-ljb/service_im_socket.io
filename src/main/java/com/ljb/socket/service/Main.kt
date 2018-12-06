package com.ljb.socket.service

import com.corundumstudio.socketio.AckRequest
import com.corundumstudio.socketio.Configuration
import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import com.ljb.socket.service.modle.ChatMessage
import com.ljb.socket.service.modle.UserBean
import com.ljb.socket.service.utils.JsonParser

//Socket客户端容器
private val mClientMap = HashMap<String, SocketIOClient>()
//离线消息容器
private val mCacheChatMessageMap = HashMap<String, ArrayList<ChatMessage>>()

/**
 * 开启服务入口
 * */
fun main(args: Array<String>) {
    println("socket service main start")

    val socketService = createSocketService("172.16.201.33", 9092)
    initAboutConnectListener(socketService)
    initAboutChatListener(socketService)
    socketService.start()

    println("socket service main end")
}

/**
 * 聊天相关监听
 * */
fun initAboutChatListener(socketService: SocketIOServer) {
    socketService.addEventListener(ChatMessage.EVENT_CHAT, String::class.java) { socketClient, chatMsgStr, ackRequest ->
        println("receive chat message: $chatMsgStr")
        try {
            val chatMessage = JsonParser.fromJsonObj(chatMsgStr, ChatMessage::class.java)
            when (chatMessage.type) {
                ChatMessage.TYPE_CHAT -> handleChatMessage(chatMessage, ackRequest)
                ChatMessage.TYPE_CMD -> handleChatCmd(chatMessage, socketClient)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("error chat message: $chatMsgStr")
        }
    }
}

/**
 * 连接相关监听
 * */
fun initAboutConnectListener(socketService: SocketIOServer) {

    socketService.addConnectListener { socketClient ->
        val user = getUserByClient(socketClient) ?: return@addConnectListener
        println("connect uid : ${user.uid} -> ${user.name}")
        mClientMap.put(user.uid, socketClient)
        //发送离线数据
        handleCacheChatMessage(user.uid)
    }

    socketService.addDisconnectListener { socketClient ->
        val user = getUserByClient(socketClient) ?: return@addDisconnectListener
        println("disconnect uid : ${user.uid} -> ${user.name}")
        mClientMap[user.uid]?.disconnect()
        mClientMap.remove(user.uid)
    }

}

/**
 * 创建socket服务
 * */
fun createSocketService(host: String, port: Int): SocketIOServer {
    val config = Configuration()
    config.hostname = host
    config.port = port
    return SocketIOServer(config)
}

/**
 * 聊天相关命令
 * */
fun handleChatCmd(chatMessage: ChatMessage, socketClient: SocketIOClient) {
    when (chatMessage.cmd) {
        ChatMessage.CMD_RECEIVE_ACK -> {
            //转发消息成功后，客户端的回调
            val user = getUserByClient(socketClient)
            println("${user?.uid} client receive success for chat message pid: ${chatMessage.pid}")
        }
    }
}

/**
 * 转发聊天消息
 * */
fun handleChatMessage(chatMessage: ChatMessage, ackRequest: AckRequest) {
    if (chatMessage.toId.isNotEmpty()) {
        chatMessage.status = ChatMessage.MSG_STATUS_SEND_SUCCESS
        ackRequest.sendAckData(JsonParser.toJson(chatMessage))
        val socketClient = mClientMap[chatMessage.toId]
        if (socketClient != null) {
            //转发
            sendChatMessage(socketClient, chatMessage)
        } else {
            //转发用户不在线，进行缓存
            println("add cache chat message : ${JsonParser.toJson(chatMessage)}")
            val cacheList = mCacheChatMessageMap[chatMessage.toId] ?: ArrayList()
            cacheList.add(chatMessage)
            mCacheChatMessageMap[chatMessage.toId] = cacheList
        }
    }
}

/**
 * 离线消息处理
 * */
fun handleCacheChatMessage(uid: String) {
    val socketClient = mClientMap[uid]
    val msgList = mCacheChatMessageMap[uid]
    if (socketClient != null && msgList != null) {
        for (chatMessage in msgList) {
            sendChatMessage(socketClient, chatMessage)
        }
        msgList.clear()
        mCacheChatMessageMap.remove(uid)
    }
}

/**
 * 获取用户相关信息
 * */
fun getUserByClient(socketClient: SocketIOClient): UserBean? {
    val uid = socketClient.handshakeData.getSingleUrlParam("uid")
    val name = socketClient.handshakeData.getSingleUrlParam("name")
    val headImg = socketClient.handshakeData.getSingleUrlParam("headImg")
    if (uid == null || uid == "") return null
    return UserBean(uid, name, headImg)
}

/**
 * 发送聊天消息
 * */
fun sendChatMessage(socketClient: SocketIOClient, chatMessage: ChatMessage) {
    val msg = JsonParser.toJson(chatMessage)
    println("send chat message : $msg")
    socketClient.sendEvent(ChatMessage.EVENT_CHAT, msg)
}