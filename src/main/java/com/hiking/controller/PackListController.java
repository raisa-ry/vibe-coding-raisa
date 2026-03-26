package com.hiking.controller;

import com.hiking.model.PackListResponse;
import com.hiking.model.TripInput;
import com.hiking.service.PackListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PackListController {

    private final PackListService packListService;

    public PackListController(PackListService packListService) {
        this.packListService = packListService;
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
}
