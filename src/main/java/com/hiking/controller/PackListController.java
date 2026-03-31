package com.hiking.controller;

import com.hiking.exception.LocationNotFoundException;
import com.hiking.model.LocationData;
import com.hiking.model.PackListResponse;
import com.hiking.model.ParsedConditions;
import com.hiking.model.TripInput;
import com.hiking.service.HikeParserService;
import com.hiking.service.LocationService;
import com.hiking.service.PackListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PackListController {

    private final PackListService packListService;
    private final LocationService locationService;
    private final HikeParserService hikeParserService;

    public PackListController(PackListService packListService, LocationService locationService,
                              HikeParserService hikeParserService) {
        this.packListService = packListService;
        this.locationService = locationService;
        this.hikeParserService = hikeParserService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody TripInput input) {
        try {
            PackListResponse response = packListService.generatePackList(input);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to generate pack list: " + e.getMessage()));
        }
    }

    @PostMapping("/parse-description")
    public ResponseEntity<?> parseDescription(@RequestBody Map<String, String> body) {
        String description = body.get("description");
        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Description must not be blank"));
        }
        try {
            ParsedConditions conditions = hikeParserService.parseDescription(description);
            return ResponseEntity.ok(conditions);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to parse description: " + e.getMessage()));
        }
    }

    @PostMapping("/location")
    public ResponseEntity<?> fetchLocation(@RequestBody Map<String, String> body) {
        try {
            String query = body.get("query");
            String date = body.get("date");
            if (date == null || date.isBlank()) date = LocalDate.now().toString();
            LocationData data = locationService.fetchLocation(query, date);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (LocationNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to fetch location: " + e.getMessage()));
        }
    }
}
