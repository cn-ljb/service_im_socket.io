package com.ljb.socket.service

import com.corundumstudio.socketio.AckRequest
import com.corundumstudio.socketio.Configuration
import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import com.ljb.socket.service.modle.ChatMessage
import com.ljb.socket.service.modle.SocketEvent
import com.ljb.socket.service.modle.UserBean
import com.ljb.socket.service.utils.ChatMessageUtils
import com.ljb.socket.service.utils.JsonParser

//Socket客户端容器
private val mClientMap = HashMap<String, SocketIOClient>()
//离线消息容器
private val mCacheChatMessageMap = HashMap<String, ArrayList<ChatMessage>>()
//在线用户
private val mUserMap = HashMap<String, UserBean>()

/**
 * 开启服务入口
 * */
fun main(args: Array<String>) {
    println("socket service main start")

    val socketService = createSocketService(Setting.SERVICE_IP, Setting.SERVICE_PORT)
    initAboutConnectListener(socketService)
    initAboutChatListener(socketService)
    socketService.start()

    println("socket service main end")
}

/**
 * 聊天相关监听
 * */
fun initAboutChatListener(socketService: SocketIOServer) {
    socketService.addEventListener(SocketEvent.EVENT_CHAT, ChatMessage::class.java) { socketClient, chatMessage, ackRequest ->
        println("receive chat message: ${chatMessage.pid}")
        try {
            when (chatMessage.type) {
                ChatMessage.TYPE_CHAT -> handleChatMessage(chatMessage, ackRequest)
                ChatMessage.TYPE_CMD -> handleChatCmd(chatMessage, socketClient)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("error chat message: ${chatMessage.pid}")
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
        mUserMap.put(user.uid, user)

        //此处方便客户端拉取当前所有联系人，实际开发优先考虑接口返回在线联系人
        handleContactList(socketService)

        //发送离线数据
        handleCacheChatMessage(user.uid)
    }

    socketService.addDisconnectListener { socketClient ->
        val user = getUserByClient(socketClient) ?: return@addDisconnectListener
        println("disconnect uid : ${user.uid} -> ${user.name}")
        mClientMap[user.uid]?.disconnect()
        mClientMap.remove(user.uid)
        mUserMap.remove(user.uid)
    }

}

/**
 * 为所有用户，广播当前在线联系人
 * */
fun handleContactList(socketService: SocketIOServer) {
    val body = JsonParser.toJson(mUserMap.values)
    val chatMessage = ChatMessageUtils.getContactListChatMessage(body)
    val msgStr = JsonParser.toJson(chatMessage)
    socketService.broadcastOperations.sendEvent(SocketEvent.EVENT_CHAT, msgStr)
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
        val socketClient = mClientMap[chatMessage.toId]
        if (socketClient != null) {
            //转发
            sendChatMessage(socketClient, chatMessage)
        } else {
            //转发用户不在线，进行缓存（仅内存缓存，请勿参考）
            println("add cache chat message : ${JsonParser.toJson(chatMessage)}")
            val cacheList = mCacheChatMessageMap[chatMessage.toId] ?: ArrayList()
            cacheList.add(chatMessage)
            mCacheChatMessageMap[chatMessage.toId] = cacheList
        }

        //145731353 回调给客户端，发送成功
        chatMessage.type = ChatMessage.TYPE_CMD
        chatMessage.cmd = ChatMessage.CMD_RECEIVE_ACK
        ackRequest.sendAckData(JsonParser.toJson(chatMessage))
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
    socketClient.sendEvent(SocketEvent.EVENT_CHAT, msg)
}