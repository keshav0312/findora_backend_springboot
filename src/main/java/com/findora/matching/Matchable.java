package com.findora.matching;

import java.time.Instant;
import java.util.List;

/** What the matching engine needs from a lost/found report. */
public interface Matchable {
    String getCategory();
    String getLocation();
    String getCity();
    String getDescription();
    String getColor();
    String getBrand();
    Instant getDate();
    List<String> getPhotos();
}
