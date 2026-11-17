package com.findora.model;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "matches")
public class Match {

    public static final List<String> STATUSES = List.of("suggested", "confirmed", "rejected", "returned");

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String lostItem;
    @Field(targetType = FieldType.OBJECT_ID)
    private String foundItem;
    @Field(targetType = FieldType.OBJECT_ID)
    private String lostOwner;
    @Field(targetType = FieldType.OBJECT_ID)
    private String foundOwner;
    private int score;
    private MatchBreakdown breakdown = new MatchBreakdown();
    private String status = "suggested";
    private String aiExplanation = "";
    private Double distanceKm;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLostItem() {
        return lostItem;
    }

    public void setLostItem(String lostItem) {
        this.lostItem = lostItem;
    }

    public String getFoundItem() {
        return foundItem;
    }

    public void setFoundItem(String foundItem) {
        this.foundItem = foundItem;
    }

    public String getLostOwner() {
        return lostOwner;
    }

    public void setLostOwner(String lostOwner) {
        this.lostOwner = lostOwner;
    }

    public String getFoundOwner() {
        return foundOwner;
    }

    public void setFoundOwner(String foundOwner) {
        this.foundOwner = foundOwner;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public MatchBreakdown getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(MatchBreakdown breakdown) {
        this.breakdown = breakdown;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(String aiExplanation) {
        this.aiExplanation = aiExplanation;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
