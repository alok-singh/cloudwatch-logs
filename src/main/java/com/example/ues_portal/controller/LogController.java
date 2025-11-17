package com.example.ues_portal.controller;

import com.example.ues_portal.model.Log;
import com.example.ues_portal.model.CloudWatchRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import com.example.ues_portal.service.CloudWatchService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

@RestController
public class LogController {

    @Autowired
    private CloudWatchService cloudWatchService;

    @GetMapping("/logs")
    public List<Log> getLogs() {
        return Collections.singletonList(
                new Log("1", "This is a dummy log message.", LocalDateTime.now())
        );
    }

    @PostMapping("/cloudwatch-logs")
    public List<Log> getCloudWatchLogs(@RequestBody CloudWatchRequest request) {
        return cloudWatchService.getLogs(request);
    }
}