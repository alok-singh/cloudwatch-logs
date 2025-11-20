package com.example.ues_portal.service.impl;

import com.example.ues_portal.model.CloudWatchRequest;
import com.example.ues_portal.model.Log;
import com.example.ues_portal.service.CloudWatchService;
import com.example.ues_portal.util.LogParser;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;


import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CloudWatchServiceImpl implements CloudWatchService {

    @Override
    public List<Log> getLogs(CloudWatchRequest request) {
        if (request.getLog_group_name_list() == null || request.getLog_group_name_list().isEmpty()) {
            throw new IllegalArgumentException("log_group_name_list is required for this query_type");
        }
        CloudWatchLogsClient logsClient = createLogsClient(request);
        List<Map<String, String>> results = executeCloudwatchQuery(logsClient, request.getLog_group_name_list().get(0), Long.parseLong(request.getStart_time()), Long.parseLong(request.getEnd_time()), request.getQuery());
        return results.stream()
                .map(this::toLog)
                .collect(Collectors.toList());
    }

    private Log toLog(Map<String, String> resultField) {
        String message = resultField.getOrDefault("@message", "");
        long timestamp = Long.parseLong(resultField.getOrDefault("@timestamp", "0"));
        return new Log(null, message, LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    }

    @Override
    public Map<String, Object> retrieveConsolidatedTrigger(CloudWatchRequest request) {
        CloudWatchLogsClient logsClient = createLogsClient(request);
        if (request.getLog_group_name_list() == null || request.getLog_group_name_list().isEmpty()) {
            throw new IllegalArgumentException("log_group_name_list is required for this query_type");
        }
        List<Map<String, String>> results = createQueryForDataSendTo3pp(logsClient, request.getQuery(), request.getLog_group_name_list().get(0), Long.parseLong(request.getStart_time()), Long.parseLong(request.getEnd_time()));
        return LogParser.parseConsolidatesResponse(results);
    }

    private List<Map<String, String>> executeCloudwatchQuery(CloudWatchLogsClient logsClient, String logGroupName, long startTime, long endTime, String queryString) {
        StartQueryRequest startQueryRequest = StartQueryRequest.builder()
                .logGroupNames(Collections.singletonList(logGroupName))
                .startTime(startTime)
                .endTime(endTime)
                .queryString(queryString)
                .build();

        StartQueryResponse startQueryResponse = logsClient.startQuery(startQueryRequest);
        String queryId = startQueryResponse.queryId();

        GetQueryResultsResponse getQueryResultsResponse;
        do {
            getQueryResultsResponse = logsClient.getQueryResults(GetQueryResultsRequest.builder().queryId(queryId).build());
        } while (getQueryResultsResponse.status() == QueryStatus.SCHEDULED || getQueryResultsResponse.status() == QueryStatus.RUNNING);

        return parseQueryResult(getQueryResultsResponse.results());
    }

    private List<Map<String, String>> parseQueryResult(List<List<ResultField>> results) {
        return results.stream()
                .map(item -> item.stream()
                        .filter(field -> !field.field().equals("@ptr"))
                        .collect(Collectors.toMap(ResultField::field, ResultField::value)))
                .collect(Collectors.toList());
    }

    private List<Map<String, String>> createQueryForDataSendTo3pp(CloudWatchLogsClient logsClient, String queryStringWithSearch, String logGroupName, long startTime, long endTime) {
        List<Map<String, String>> queryResultsForRequestId = executeCloudwatchQuery(logsClient, logGroupName, startTime, endTime, queryStringWithSearch);
        List<String> requestIdList = queryResultsForRequestId.stream()
                .map(item -> item.get("@requestId"))
                .distinct()
                .filter(item -> item != null && !item.equals("undefined"))
                .collect(Collectors.toList());

        if (requestIdList.isEmpty()) {
            return Collections.emptyList();
        }

        return fetchRequestIdList(logsClient, requestIdList, logGroupName, startTime, endTime);
    }

    private List<Map<String, String>> fetchRequestIdList(CloudWatchLogsClient logsClient, List<String> requestIdList, String logGroupName, long startTime, long endTime) {
        String queryStringWithRequestId = String.format("fields @timestamp, @requestId, @message, msg, meta | filter @requestId in ['%s'] | sort @timestamp asc",
                String.join("','", requestIdList));
        return executeCloudwatchQuery(logsClient, logGroupName, startTime, endTime, queryStringWithRequestId);
    }

    @Override
    public List<String> retrieveLogGroups(CloudWatchRequest request) {
        CloudWatchLogsClient logsClient = createLogsClient(request);
        return logsClient.describeLogGroups().logGroups().stream()
                .map(LogGroup::logGroupName)
                .collect(Collectors.toList());
    }

    private CloudWatchLogsClient createLogsClient(CloudWatchRequest request) {
        return CloudWatchLogsClient.builder()
                .region(Region.of(request.getRegion()))
                .credentialsProvider(ProfileCredentialsProvider.create("aws-role"))
                .build();
    }

    @Override
    public Map<String, String> generateAwsConfig(CloudWatchRequest request) {
        // Step 1: Create a base STS client with initial credentials
        StsClient baseStsClient = StsClient.builder()
                .region(Region.of(request.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsSessionCredentials.create(
                        request.getAccessKey(),
                        request.getSecretKey(),
                        request.getSessionToken()
                )))
                .build();

        // Step 2: Get a session token using MFA
        GetSessionTokenRequest sessionTokenRequest = GetSessionTokenRequest.builder()
                .serialNumber(request.getMfa_arn())
                .tokenCode(request.getMfa_token())
                .build();
        GetSessionTokenResponse sessionTokenResponse = baseStsClient.getSessionToken(sessionTokenRequest);

        String credentialsFileContent = String.format(
                "[mfa]\n" +
                "aws_access_key_id = %s\n" +
                "aws_secret_access_key = %s\n" +
                "aws_session_token = %s\n",
                sessionTokenResponse.credentials().accessKeyId(),
                sessionTokenResponse.credentials().secretAccessKey(),
                sessionTokenResponse.credentials().sessionToken()
        );

        String configFileContent = String.format(
                "[profile aws-role]\n" +
                "role_arn = %s\n" +
                "source_profile = mfa\n" +
                "region = %s\n",
                request.getRole(),
                request.getRegion()
        );

        return Map.of(
                "credentials_file_content", credentialsFileContent,
                "config_file_content", configFileContent
        );
    }
}