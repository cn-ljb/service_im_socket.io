package com.ljb.socket.service.utils

import com.ljb.socket.service.Setting
import com.ljb.socket.service.modle.ChatMessage
import java.util.Random


object ChatUtils {


    fun getContactListChatMessage(body: String): ChatMessage {
        val chatMessage = ChatMessage()
        chatMessage.fromId = Setting.SERVICE_SOCKET_ID
        chatMessage.pid = getPid()
        chatMessage.type = ChatMessage.TYPE_CMD
        chatMessage.cmd = ChatMessage.CMD_CONTACT_LIST
        chatMessage.body = body
        chatMessage.time = System.currentTimeMillis()
        return chatMessage
    }


    // 消息唯一标识
    fun getPid(): String {
        val stringBuffer = StringBuilder()
        val time = System.currentTimeMillis().toString()
        stringBuffer.append("AN")
                .append(getRandomInt())
                .append(getRandomString())
                .append(getRandomInt())
                .append(getRandomString())
                .append(getRandomInt())
                .append(time.substring(time.length - 4, time.length))
        return stringBuffer.toString()
    }

    /**
     * 随机产生一个4个字节的int
     */
    fun getRandomInt(): Int {
        val min = 10
        val max = 99
        val random = Random()
        return random.nextInt(max - min + 1) + min
    }


    fun getRandomString(): String {
        val str = "abcdefghigklmnopkrstuvwxyzABCDEFGHIGKLMNOPQRSTUVWXYZ0123456789"
        val random = Random()
        val sf = StringBuffer()
        for (i in 0..1) {
            val number = random.nextInt(62)// 0~61
            sf.append(str[number])

        }
        return sf.toString()
    }

    fun copyChatMessage(chatMessage: ChatMessage): ChatMessage {
        return JsonParser.fromJsonObj(JsonParser.toJson(chatMessage), ChatMessage::class.java)
    }

    fun getAck(chatMessage: ChatMessage): String {
        val ackChatMessage = ChatUtils.copyChatMessage(chatMessage)
        ackChatMessage.type = ChatMessage.TYPE_CMD
        ackChatMessage.cmd = ChatMessage.CMD_RECEIVE_ACK
        ackChatMessage.status = ChatMessage.MSG_STATUS_SEND_SUCCESS
        return JsonParser.toJson(ackChatMessage)
    }

}