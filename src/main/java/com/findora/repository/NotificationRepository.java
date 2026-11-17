package com.findora.repository;

import com.findora.model.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findTop100ByUserOrderByCreatedAtDesc(String user);

    long countByUserAndRead(String user, Boolean read);

    Optional<Notification> findByIdAndUser(String id, String user);
}
