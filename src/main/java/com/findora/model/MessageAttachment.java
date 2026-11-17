package com.findora.model;

public class MessageAttachment {
    private String kind; // "image" | "audio"
    private String url;
    private Integer durationSeconds = 0;

    public MessageAttachment() {}

    public MessageAttachment(String kind, String url, Integer durationSeconds) {
        this.kind = kind;
        this.url = url;
        this.durationSeconds = durationSeconds;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
