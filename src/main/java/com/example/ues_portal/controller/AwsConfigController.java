package com.example.ues_portal.controller;

import com.example.ues_portal.model.CloudWatchRequest;
import com.example.ues_portal.service.CloudWatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class AwsConfigController {

    private final CloudWatchService cloudWatchService;

    @Value("${aws.accessKey}")
    private String accessKey;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Value("${aws.sessionToken}")
    private String sessionToken;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.role}")
    private String role;

    @Value("${aws.mfa_arn}")
    private String mfa_arn;

    @Autowired
    public AwsConfigController(CloudWatchService cloudWatchService) {
        this.cloudWatchService = cloudWatchService;
    }

    @PostMapping("/aws/generate-config")
    public ResponseEntity<Map<String, String>> generateConfig(@RequestBody Map<String, String> payload) {
        String mfaToken = payload.get("mfa_token");

        CloudWatchRequest request = new CloudWatchRequest();
        request.setAccessKey(accessKey);
        request.setSecretKey(secretKey);
        request.setSessionToken(sessionToken);
        request.setRegion(region);
        request.setRole(role);
        request.setMfa_arn(mfa_arn);
        request.setMfa_token(mfaToken);

        return ResponseEntity.ok(cloudWatchService.generateAwsConfig(request));
    }
}