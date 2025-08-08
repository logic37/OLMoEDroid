package com.allenai.olmoe.data.repository

import com.allenai.olmoe.data.dao.ChatDao
import com.allenai.olmoe.data.model.Chat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    
    fun getAllMessages(): Flow<List<Chat>> = chatDao.getAllMessages()
    
    suspend fun getAllMessagesList(): List<Chat> = chatDao.getAllMessagesList()
    
    suspend fun insertMessage(chat: Chat) = chatDao.insertMessage(chat)
    
    suspend fun insertMessages(chats: List<Chat>) = chatDao.insertMessages(chats)
    
    suspend fun deleteAllMessages() = chatDao.deleteAllMessages()
    
    suspend fun getMessageCount(): Int = chatDao.getMessageCount()
} 