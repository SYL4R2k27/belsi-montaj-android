package com.belsi.work.data.local.dao

import androidx.room.*
import com.belsi.work.data.local.entities.TicketEntity
import com.belsi.work.data.local.entities.TicketMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {
    
    @Query("SELECT * FROM tickets ORDER BY updatedAt DESC")
    fun observeAllTickets(): Flow<List<TicketEntity>>
    
    @Query("SELECT * FROM tickets WHERE id = :ticketId")
    suspend fun getTicketById(ticketId: String): TicketEntity?
    
    @Query("SELECT * FROM tickets WHERE id = :ticketId")
    fun observeTicketById(ticketId: String): Flow<TicketEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: TicketEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTickets(tickets: List<TicketEntity>)
    
    @Update
    suspend fun updateTicket(ticket: TicketEntity)
    
    @Delete
    suspend fun deleteTicket(ticket: TicketEntity)
    
    // Ticket Messages
    @Query("SELECT * FROM ticket_messages WHERE ticketId = :ticketId ORDER BY createdAt ASC")
    fun observeMessagesByTicketId(ticketId: String): Flow<List<TicketMessageEntity>>
    
    @Query("SELECT * FROM ticket_messages WHERE ticketId = :ticketId ORDER BY createdAt ASC")
    suspend fun getMessagesByTicketId(ticketId: String): List<TicketMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: TicketMessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<TicketMessageEntity>)
}
