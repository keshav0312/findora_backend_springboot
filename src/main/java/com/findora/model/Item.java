package com.findora.model;

import com.findora.matching.Matchable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Fields shared by lost and found reports (the two collections stay structurally identical). */
public abstract class Item implements Matchable {

    public static final List<String> CATEGORIES = List.of(
            "Wallet", "Mobile", "Laptop", "Keys", "Passport", "Aadhaar", "PAN Card", "Bag", "Watch",
            "Jewelry", "Pet", "Document", "Earbuds", "Bicycle", "Helmet", "Water Bottle", "Charger",
            "USB Drive", "Other");
    public static final List<String> STATUSES = List.of("open", "matched", "closed");

    @Id
    private String id;
    private String title;
    private String category;
    private String description = "";
    private String color = "";
    private String brand = "";
    private String location;
    private String city = "";
    private String contactPhone = "";
    private Coordinates coordinates = new Coordinates();
    private Instant date;
    private Double reward = 0.0;
    private List<String> photos = new ArrayList<>();
    private List<VerificationQuestion> verificationQuestions = new ArrayList<>();
    private String status = "open";
    @Field(targetType = FieldType.OBJECT_ID)
    private String owner;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public Double getReward() {
        return reward;
    }

    public void setReward(Double reward) {
        this.reward = reward;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(List<String> photos) {
        this.photos = photos;
    }

    public List<VerificationQuestion> getVerificationQuestions() {
        return verificationQuestions;
    }

    public void setVerificationQuestions(List<VerificationQuestion> verificationQuestions) {
        this.verificationQuestions = verificationQuestions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
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
