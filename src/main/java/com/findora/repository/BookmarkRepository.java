package com.findora.repository;

import com.findora.model.Bookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookmarkRepository extends MongoRepository<Bookmark, String> {
    List<Bookmark> findByUserOrderByCreatedAtDesc(String user);

    Optional<Bookmark> findByUserAndItemTypeAndItem(String user, String itemType, String item);
}
