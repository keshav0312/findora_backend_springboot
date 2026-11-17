package com.findora.config;

import com.findora.model.Bookmark;
import com.findora.model.FoundItem;
import com.findora.model.LostItem;
import com.findora.model.Match;
import com.findora.model.Message;
import com.findora.model.Notification;
import com.findora.model.User;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

/**
 * Recreates the indexes Mongoose declared. Each one is created defensively: if an equivalent index
 * already exists in your database (created by the Node app) the conflict is logged and ignored.
 */
@Component
public class MongoIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexConfig.class);
    private final MongoTemplate mongo;

    public MongoIndexConfig(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @PostConstruct
    void ensureIndexes() {
        ensure(User.class, new Index().on("email", Sort.Direction.ASC).unique());
        ensure(User.class, new Index().on("role", Sort.Direction.ASC));

        for (Class<?> type : new Class<?>[] {LostItem.class, FoundItem.class}) {
            ensure(type, new Index().on("category", Sort.Direction.ASC).on("city", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC));
            ensure(type, new TextIndexDefinition.TextIndexDefinitionBuilder()
                    .onFields("title", "description", "brand").build());
        }

        ensure(Match.class, new Index().on("lostItem", Sort.Direction.ASC).on("foundItem", Sort.Direction.ASC).unique());
        ensure(Message.class, new Index().on("conversationId", Sort.Direction.ASC));
        ensure(Notification.class, new Index().on("user", Sort.Direction.ASC));
        ensure(Bookmark.class, new Index().on("user", Sort.Direction.ASC).on("itemType", Sort.Direction.ASC)
                .on("item", Sort.Direction.ASC).unique());
    }

    private void ensure(Class<?> type, IndexDefinition definition) {
        try {
            mongo.indexOps(type).ensureIndex(definition);
        } catch (Exception e) {
            log.warn("Index on {} could not be created", type.getSimpleName(), e);
        }
    }
}
