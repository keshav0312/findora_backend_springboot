package com.findora.model;

/** Per-field score contributions (category 0-25, location 0-35, description 0-20, date 0-10, image 0-10). */
public class MatchBreakdown {
    private int category;
    private int location;
    private int description;
    private int date;
    private int image;

    public MatchBreakdown() {}

    public MatchBreakdown(int category, int location, int description, int date, int image) {
        this.category = category;
        this.location = location;
        this.description = description;
        this.date = date;
        this.image = image;
    }

    public int getCategory() {
        return category;
    }

    public void setCategory(int category) {
        this.category = category;
    }

    public int getLocation() {
        return location;
    }

    public void setLocation(int location) {
        this.location = location;
    }

    public int getDescription() {
        return description;
    }

    public void setDescription(int description) {
        this.description = description;
    }

    public int getDate() {
        return date;
    }

    public void setDate(int date) {
        this.date = date;
    }

    public int getImage() {
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }
}
