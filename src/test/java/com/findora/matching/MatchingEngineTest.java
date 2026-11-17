package com.findora.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.findora.model.FoundItem;
import com.findora.model.Item;
import com.findora.model.LostItem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    private static <T extends Item> T item(T i, String category, String location, String city, String desc,
                                           String color, String brand, int daysAgo, boolean photo) {
        i.setCategory(category);
        i.setLocation(location);
        i.setCity(city);
        i.setDescription(desc);
        i.setColor(color);
        i.setBrand(brand);
        i.setDate(Instant.now().minus(Duration.ofDays(daysAgo)));
        i.setPhotos(photo ? List.of("/uploads/x.jpg") : List.of());
        return i;
    }

    @Test
    void seedWalletsAreAMatch() {
        Item lost = item(new LostItem(), "Wallet", "DB Mall, Food Court", "Bhopal",
                "Black leather wallet, has a PAN card and around 1500 cash", "Black", "Wildcraft", 2, false);
        Item found = item(new FoundItem(), "Wallet", "DB Mall, near parking", "Bhopal",
                "Found a black leather wallet near the food court, has a PAN card and some cash", "Black", "Wildcraft", 1, false);
        MatchResult r = MatchingEngine.compute(lost, found);
        assertTrue(r.score() >= MatchingEngine.MATCH_THRESHOLD, "score was " + r.score());
        assertEquals(25, r.breakdown().getCategory());
    }

    @Test
    void differentCategoryFarApartIsNotAMatch() {
        Item lost = item(new LostItem(), "Keys", "Kolar Road", "Bhopal", "Toyota key fob", "Silver", "Toyota", 30, false);
        Item found = item(new FoundItem(), "Mobile", "MP Nagar", "Bhopal", "cracked screen", "Blue", "Apple", 1, false);
        assertTrue(MatchingEngine.compute(lost, found).score() < MatchingEngine.MATCH_THRESHOLD);
    }

    @Test
    void photosOnBothSidesAddImageSignal() {
        Item a = item(new LostItem(), "Bag", "Bus Stand", "Bhopal", "red bag", "Red", "", 1, true);
        Item b = item(new FoundItem(), "Bag", "Bus Stand", "Bhopal", "red bag", "Red", "", 1, true);
        assertEquals(10, MatchingEngine.compute(a, b).breakdown().getImage());
    }

    @Test
    void haversineOneDegreeOfLongitudeAtEquator() {
        assertEquals(111.2, GeoUtils.haversineKm(0, 0, 0, 1), 0.0001);
    }
}
