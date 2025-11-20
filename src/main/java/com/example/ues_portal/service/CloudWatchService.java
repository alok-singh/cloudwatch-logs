package com.example.ues_portal.service;

import com.example.ues_portal.model.CloudWatchRequest;
import com.example.ues_portal.model.Log;

import java.util.List;

import java.util.Map;

import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;

import java.util.List;
import java.util.Map;

public interface CloudWatchService {
    List<Log> getLogs(CloudWatchRequest request);
    Map<String, Object> retrieveConsolidatedTrigger(CloudWatchRequest request);
    List<String> retrieveLogGroups(CloudWatchRequest request);
    Map<String, String> generateAwsConfig(CloudWatchRequest request);
}