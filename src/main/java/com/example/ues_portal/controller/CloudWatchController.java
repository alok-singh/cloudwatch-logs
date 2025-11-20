package com.example.ues_portal.controller;

import com.example.ues_portal.model.CloudWatchRequest;
import com.example.ues_portal.service.CloudWatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CloudWatchController {

    private final CloudWatchService cloudWatchService;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.profile}")
    private String profile;

    @Autowired
    public CloudWatchController(CloudWatchService cloudWatchService) {
        this.cloudWatchService = cloudWatchService;
    }

    @PostMapping("/execute-query")
    public ResponseEntity<Object> executeQuery(@Valid @RequestBody CloudWatchRequest request) {
        request.setRegion(region);
        request.setProfile(profile);

        if ("consolidated".equals(request.getQuery_type())) {
            return ResponseEntity.ok(cloudWatchService.retrieveConsolidatedTrigger(request));
        } else if ("logGroups".equals(request.getQuery_type())) {
            return ResponseEntity.ok(cloudWatchService.retrieveLogGroups(request));
        }
        return ResponseEntity.ok(cloudWatchService.getLogs(request));
    }
}