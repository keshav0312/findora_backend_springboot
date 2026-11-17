package com.findora.matching;

/** Great-circle distance helpers (port of utils/geo.ts). */
public final class GeoUtils {

    private GeoUtils() {}

    /** Haversine distance in km, rounded to 1 decimal place. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double r = 6371; // Earth's mean radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.floor(r * c * 10 + 0.5) / 10.0;
    }
}
