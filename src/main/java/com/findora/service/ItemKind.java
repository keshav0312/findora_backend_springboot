package com.findora.service;

import com.findora.model.FoundItem;
import com.findora.model.Item;
import com.findora.model.LostItem;

/** Lets one implementation serve both /api/lost and /api/found (the Node `buildItemController` factory). */
public enum ItemKind {
    LOST("lost", LostItem.class),
    FOUND("found", FoundItem.class);

    private final String label;
    private final Class<? extends Item> type;

    ItemKind(String label, Class<? extends Item> type) {
        this.label = label;
        this.type = type;
    }

    public String label() {
        return label;
    }

    public Class<? extends Item> type() {
        return type;
    }

    public ItemKind opposite() {
        return this == LOST ? FOUND : LOST;
    }

    public Item newItem() {
        return this == LOST ? new LostItem() : new FoundItem();
    }
}
