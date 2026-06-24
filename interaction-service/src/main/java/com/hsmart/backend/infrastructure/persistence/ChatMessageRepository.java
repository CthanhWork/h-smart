package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.domain.entities.ChatMessage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    @Query(value = "{ 'productId': ?0, '$or': [ { 'senderId': ?1, 'receiverId': ?2 }, { 'senderId': ?2, 'receiverId': ?1 } ] }", sort = "{ 'timestamp': 1 }")
    List<ChatMessage> findConversation(Long productId, String firstParticipantId, String secondParticipantId);

    @Query(value = "{ '$or': [ { 'senderId': ?0 }, { 'receiverId': ?0 } ] }", sort = "{ 'timestamp': -1 }")
    List<ChatMessage> findUserMessages(String userId);

    @Query(value = "{ '$or': [ { 'senderId': ?0, 'receiverId': ?1 }, { 'senderId': ?1, 'receiverId': ?0 } ] }", sort = "{ 'timestamp': -1 }")
    List<ChatMessage> findAssistantConversationHistory(String userId, String assistantId, Pageable pageable);

    @Query(value = "{ '$or': [ { 'senderId': ?0, 'receiverId': ?1 }, { 'senderId': ?1, 'receiverId': ?0 } ] }", delete = true)
    long deleteAssistantConversation(String userId, String assistantId);

    @Query(value = "{ '$or': [ { 'senderId': ?0 }, { 'receiverId': ?0 } ], 'timestamp': { '$lt': ?1 } }", delete = true)
    long deleteOldAssistantMessages(String assistantId, Instant cutoff);
}
