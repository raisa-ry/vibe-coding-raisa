package com.hiking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.exception.LocationNotFoundException;
import com.hiking.model.LocationData;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class LocationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public LocationData fetchLocation(String query, String tripDate) throws Exception {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Location query must not be blank");
        }

        if (tripDate == null || tripDate.isBlank()) {
            tripDate = LocalDate.now().toString();
        }

        double lat, lon;
        String displayName;

        if (isCoordinates(query)) {
            String[] parts = query.split(",");
            lat = Double.parseDouble(parts[0].trim());
            lon = Double.parseDouble(parts[1].trim());
            String[] geocoded = reverseGeocode(lat, lon);
            displayName = geocoded[0];
        } else {
            String[] geocoded = geocodeByName(query);
            lat = Double.parseDouble(geocoded[1]);
            lon = Double.parseDouble(geocoded[2]);
            displayName = geocoded[0];
        }

        WeatherResult weather = fetchWeatherForDate(lat, lon, tripDate);

        LocationData data = new LocationData();
        data.setDisplayName(displayName);
        data.setLatitude(lat);
        data.setLongitude(lon);
        data.setElevationFt(weather.elevationFt());
        data.setTempMin(weather.tempMin());
        data.setTempMax(weather.tempMax());
        data.setHasRain(weather.hasRain());
        data.setHasWind(weather.hasWind());
        data.setHasSnow(weather.hasSnow());
        data.setHasHeat(weather.tempMax() > 85);
        data.setTripDate(tripDate);
        data.setWeatherNote(weather.weatherNote());

        // terrain will be set from Nominatim type/class — fetch again for type info
        String[] typeInfo = isCoordinates(query)
                ? reverseGeocodeWithType(lat, lon)
                : geocodeByNameWithType(query);
        data.setTerrain(inferTerrain(typeInfo[1], typeInfo[2]));
        data.setWeatherSummary(buildWeatherSummary(weather, data));

        return data;
    }

    private boolean isCoordinates(String query) {
        return query.matches("^-?\\d+\\.?\\d*,\\s*-?\\d+\\.?\\d*$");
    }

    private String[] geocodeByName(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1";
        String body = nominatimGet(url);
        JsonNode arr = objectMapper.readTree(body);
        if (!arr.isArray() || arr.isEmpty()) {
            throw new LocationNotFoundException("Location not found: " + query);
        }
        JsonNode place = arr.get(0);
        return new String[]{
                place.path("display_name").asText(),
                place.path("lat").asText(),
                place.path("lon").asText()
        };
    }

    private String[] geocodeByNameWithType(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1";
        String body = nominatimGet(url);
        JsonNode arr = objectMapper.readTree(body);
        if (!arr.isArray() || arr.isEmpty()) {
            throw new LocationNotFoundException("Location not found: " + query);
        }
        JsonNode place = arr.get(0);
        return new String[]{
                place.path("display_name").asText(),
                place.path("type").asText(""),
                place.path("class").asText("")
        };
    }

    private String[] reverseGeocode(double lat, double lon) throws Exception {
        String url = "https://nominatim.openstreetmap.org/reverse?lat=" + lat + "&lon=" + lon + "&format=json";
        String body = nominatimGet(url);
        JsonNode place = objectMapper.readTree(body);
        return new String[]{ place.path("display_name").asText() };
    }

    private String[] reverseGeocodeWithType(double lat, double lon) throws Exception {
        String url = "https://nominatim.openstreetmap.org/reverse?lat=" + lat + "&lon=" + lon + "&format=json";
        String body = nominatimGet(url);
        JsonNode place = objectMapper.readTree(body);
        return new String[]{
                place.path("display_name").asText(),
                place.path("type").asText(""),
                place.path("class").asText("")
        };
    }

    private String nominatimGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "HikePack-AI/1.0 (hiking-gear-app)")
                .header("Accept-Language", "en")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Nominatim API error (" + response.statusCode() + ")");
        }
        return response.body();
    }

    private WeatherResult fetchWeatherForDate(double lat, double lon, String tripDate) throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate date = LocalDate.parse(tripDate);
        long daysOut = ChronoUnit.DAYS.between(today, date);

        String url;
        boolean isForecast;
        String weatherNote;
        LocalDate startDate;

        if (daysOut >= 0 && daysOut <= 15) {
            // Forecast API — exact dates
            startDate = date;
            weatherNote = null;
            isForecast = true;
            url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=" + lat +
                    "&longitude=" + lon +
                    "&hourly=temperature_2m,precipitation_probability,snowfall,windspeed_10m" +
                    "&temperature_unit=fahrenheit" +
                    "&start_date=" + startDate +
                    "&end_date=" + startDate.plusDays(2) +
                    "&timezone=auto";
        } else if (daysOut < -5) {
            // Archive API — historical data
            startDate = date;
            weatherNote = "Historical data";
            isForecast = false;
            url = "https://archive-api.open-meteo.com/v1/archive" +
                    "?latitude=" + lat +
                    "&longitude=" + lon +
                    "&hourly=temperature_2m,precipitation,snowfall,windspeed_10m" +
                    "&temperature_unit=fahrenheit" +
                    "&start_date=" + startDate +
                    "&end_date=" + startDate.plusDays(2) +
                    "&timezone=auto";
        } else {
            // Archive API — seasonal estimate (prior year)
            startDate = date.minusYears(1);
            weatherNote = "Seasonal estimate (prior year)";
            isForecast = false;
            url = "https://archive-api.open-meteo.com/v1/archive" +
                    "?latitude=" + lat +
                    "&longitude=" + lon +
                    "&hourly=temperature_2m,precipitation,snowfall,windspeed_10m" +
                    "&temperature_unit=fahrenheit" +
                    "&start_date=" + startDate +
                    "&end_date=" + startDate.plusDays(2) +
                    "&timezone=auto";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo API error (" + response.statusCode() + ")");
        }

        JsonNode root = objectMapper.readTree(response.body());
        int elevationFt = (int) (root.path("elevation").asDouble(0) * 3.28084);

        JsonNode hourly = root.path("hourly");
        JsonNode temps = hourly.path("temperature_2m");
        JsonNode snow = hourly.path("snowfall");
        JsonNode wind = hourly.path("windspeed_10m");

        int tempMin = Integer.MAX_VALUE;
        int tempMax = Integer.MIN_VALUE;
        boolean hasRain = false;
        boolean hasWind = false;
        boolean hasSnow = false;

        int count = Math.min(72, temps.size());
        if (isForecast) {
            JsonNode precip = hourly.path("precipitation_probability");
            for (int i = 0; i < count; i++) {
                int t = (int) Math.round(temps.get(i).asDouble());
                if (t < tempMin) tempMin = t;
                if (t > tempMax) tempMax = t;
                if (precip.size() > i && precip.get(i).asInt() > 40) hasRain = true;
                if (wind.size() > i && wind.get(i).asDouble() > 20) hasWind = true;
                if (snow.size() > i && snow.get(i).asDouble() > 0.1) hasSnow = true;
            }
        } else {
            JsonNode precip = hourly.path("precipitation");
            for (int i = 0; i < count; i++) {
                int t = (int) Math.round(temps.get(i).asDouble());
                if (t < tempMin) tempMin = t;
                if (t > tempMax) tempMax = t;
                if (precip.size() > i && precip.get(i).asDouble() > 0.3) hasRain = true;
                if (wind.size() > i && wind.get(i).asDouble() > 20) hasWind = true;
                if (snow.size() > i && snow.get(i).asDouble() > 0.1) hasSnow = true;
            }
        }

        if (tempMin == Integer.MAX_VALUE) tempMin = 50;
        if (tempMax == Integer.MIN_VALUE) tempMax = 70;

        return new WeatherResult(tempMin, tempMax, hasRain, hasWind, hasSnow, elevationFt, weatherNote);
    }

    private String inferTerrain(String type, String clazz) {
        return switch (type.toLowerCase()) {
            case "peak", "ridge", "hill", "summit", "mountain", "valley" -> "mountain";
            case "forest", "wood" -> "forest";
            case "water", "lake", "reservoir", "bay" -> "lakeside";
            case "beach", "coastline" -> "coastal";
            default -> "trail";
        };
    }

    private String buildWeatherSummary(WeatherResult weather, LocationData data) {
        List<String> parts = new ArrayList<>();
        parts.add(weather.tempMin() + "–" + weather.tempMax() + "°F");
        if (weather.hasSnow()) parts.add("snow possible");
        else if (weather.hasRain()) parts.add("rain expected");
        if (weather.hasWind()) parts.add("high winds");
        if (data.isHasHeat()) parts.add("extreme heat");
        return String.join(", ", parts);
    }

    private record WeatherResult(int tempMin, int tempMax, boolean hasRain,
                                  boolean hasWind, boolean hasSnow, int elevationFt,
                                  String weatherNote) {}
}
