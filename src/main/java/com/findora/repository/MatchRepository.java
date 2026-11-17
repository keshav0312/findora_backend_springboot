package com.findora.repository;

import com.findora.model.Match;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchRepository extends MongoRepository<Match, String> {
    List<Match> findByLostOwnerOrFoundOwner(String lostOwner, String foundOwner, Sort sort);

    Optional<Match> findByLostItemAndFoundItem(String lostItem, String foundItem);
}
