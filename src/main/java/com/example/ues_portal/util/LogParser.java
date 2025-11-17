package com.example.ues_portal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Object> parseConsolidatesResponse(List<Map<String, String>> data) {
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("backend_log", new ArrayList<Map<String, Object>>());
        finalResult.put("ues_log", new ArrayList<Map<String, Object>>());
        finalResult.put("queue_log", new ArrayList<Map<String, Object>>());

        if (isCasHandlerProvisionFlow(data)) {
            return parseCasHandlerProvision(data, finalResult);
        }

        Map<String, List<Map<String, String>>> groupedLogs = data.stream()
                .filter(log -> log.containsKey("@requestId"))
                .collect(Collectors.groupingBy(log -> log.get("@requestId")));

        for (List<Map<String, String>> group : groupedLogs.values()) {
            Map<String, Object> result = processLogGroup(group);
            ((List<Map<String, Object>>) finalResult.get("backend_log")).addAll((List<Map<String, Object>>) result.get("backend_log"));
            ((List<Map<String, Object>>) finalResult.get("ues_log")).addAll((List<Map<String, Object>>) result.get("ues_log"));
            ((List<Map<String, Object>>) finalResult.get("queue_log")).addAll((List<Map<String, Object>>) result.get("queue_log"));
        }

        return finalResult;
    }

    private static Map<String, Object> processLogGroup(List<Map<String, String>> logs) {
        Map<String, Object> result = new HashMap<>();
        result.put("backend_log", new ArrayList<Map<String, Object>>());
        result.put("ues_log", new ArrayList<Map<String, Object>>());
        result.put("queue_log", new ArrayList<Map<String, Object>>());

        for (int i = 0; i < logs.size(); i++) {
            Map<String, String> logItem = logs.get(i);
            String msg = logItem.getOrDefault("msg", "");

            if ("method".equals(msg)) {
                Map<String, Object> item = handleMethod(logs, i);
                if (item != null) {
                    ((List<Map<String, Object>>) result.get("backend_log")).add(item);
                }
            } else if (msg.startsWith("wsdlUrl: ")) {
                Map<String, Object> item = handleWsdl(logs, i);
                if (item != null) {
                    ((List<Map<String, Object>>) result.get("backend_log")).add(item);
                }
            } else if ("records".equals(msg)) {
                List<Map<String, Object>> items = handleRecords(logItem, Long.parseLong(logItem.get("@timestamp")));
                if (items != null && !items.isEmpty()) {
                    ((List<Map<String, Object>>) result.get("queue_log")).addAll(items);
                }
            } else if ("APIRequest".equals(msg)) {
                handleApiRequest(logItem, (List<Map<String, Object>>) result.get("ues_log"));
            }
        }
        return result;
    }

    private static boolean isCasHandlerProvisionFlow(List<Map<String, String>> data) {
        if (data.size() < 2) return false;
        String trimmed = cleanLogMessage(data.get(1).get("@message"));
        Map<String, Object> parsedData = parserWithCatch(trimmed);
        if (parsedData == null || !parsedData.containsKey("meta")) return false;
        List<Map<String, Object>> meta = (List<Map<String, Object>>) parsedData.get("meta");
        return meta.stream().anyMatch(item -> item.containsKey("eventSourceARN") && ((String) item.get("eventSourceARN")).contains("cas_queue_"));
    }

    private static Map<String, Object> parseCasHandlerProvision(List<Map<String, String>> data, Map<String, Object> result) {
        Map<String, String> recordsLog = data.stream().filter(log -> "records".equals(log.get("msg"))).findFirst().orElse(null);
        if (recordsLog == null) {
            return result;
        }

        Map<String, Object> recordsMessage = parserWithCatch(cleanLogMessage(recordsLog.get("@message")));
        List<Map<String, Object>> queueRecords = (List<Map<String, Object>>) recordsMessage.get("meta");
        if (queueRecords == null) {
            queueRecords = new ArrayList<>();
        }

        Map<String, Integer> smcToRecordIndex = new HashMap<>();
        Map<String, Integer> householdToRecordIndex = new HashMap<>();
        Map<String, Integer> subscriberToRecordIndex = new HashMap<>();

        List<Map<String, Object>> queueLog = new ArrayList<>();
        for (int i = 0; i < queueRecords.size(); i++) {
            Map<String, Object> record = queueRecords.get(i);
            Map<String, Object> body = (Map<String, Object>) record.get("body");
            String smc = (String) body.get("smc_number");
            String householdId = (String) body.get("household_id");
            smcToRecordIndex.put(smc, i);
            householdToRecordIndex.put(householdId, i);

            Map<String, Object> queueLogItem = new HashMap<>();
            queueLogItem.put("request", body);
            queueLogItem.put("request_id", recordsLog.get("@requestId") + "_" + i);
            Map<String, Object> requestContext = new HashMap<>();
            requestContext.put("eventSource", record.get("eventSource"));
            requestContext.put("eventSourceARN", maskArnAccountNumber((String) record.get("eventSourceARN")));
            requestContext.put("requestTime", formatTimestamp(Long.parseLong(recordsLog.get("@timestamp"))));
            queueLogItem.put("requestContext", requestContext);
            queueLog.add(queueLogItem);
        }
        result.put("queue_log", queueLog);

        data.forEach(log -> {
            String msg = log.getOrDefault("msg", "");
            if (msg.startsWith("SMC Number #")) {
                Pattern pattern = Pattern.compile("SMC Number #(\\S+) getSubscriberId subscriberDetails: (.*)");
                Matcher matcher = pattern.matcher(msg);
                if (matcher.find()) {
                    String smc = matcher.group(1);
                    Map<String, Object> details = parserWithCatch(matcher.group(2));
                    if (details.containsKey("subscriberId") && smcToRecordIndex.containsKey(smc)) {
                        subscriberToRecordIndex.put((String) details.get("subscriberId"), smcToRecordIndex.get(smc));
                    }
                }
            }
        });

        List<Map<String, Object>> backendLog = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Map<String, String> logItem = data.get(i);
            String msg = logItem.getOrDefault("msg", "");
            String message = cleanLogMessage(logItem.get("@message"));
            String requestId = logItem.get("@requestId");
            long timestamp = Long.parseLong(logItem.get("@timestamp"));
            int recordIndex = -1;

            switch (msg) {
                case "Request for CAS server": {
                    Map<String, Object> messageContent = parserWithCatch(message);
                    if ("CreateSubscriber".equals(((Map<String, Object>)messageContent.get("commandDetails")).get("command"))) {
                        String smc = (String) messageContent.get("param2");
                        recordIndex = smcToRecordIndex.getOrDefault(smc, -1);
                        if (recordIndex != -1) {
                            Map<String, Object> log = new HashMap<>();
                            log.put("timestamp", timestamp);
                            log.put("url", "CreateSubscriber");
                            log.put("body", Collections.singletonMap("smc_number", smc));
                            log.put("response", parserWithCatch(data.stream().filter(d -> d.get("msg").contains("getSubscriberId subscriberDetails") && d.get("@message").contains(smc)).findFirst().get().get("msg")));
                            log.put("request_id", requestId + "_" + recordIndex);
                            backendLog.add(log);
                        }
                    }
                    break;
                }
                case "provision_cas_linear":
                case "provision_cas_od": {
                    Map<String, Object> parsedMessage = parserWithCatch(message);
                    String subscriberId = (String) ((Map<String, Object>)parsedMessage.get("request")).get("subscriberId");
                    if (subscriberId != null && subscriberToRecordIndex.containsKey(subscriberId)) {
                        recordIndex = subscriberToRecordIndex.get(subscriberId);
                        Map<String, Object> log = new HashMap<>();
                        log.put("timestamp", timestamp);
                        log.put("url", msg);
                        log.put("body", parsedMessage.get("request"));
                        log.put("response", parsedMessage.get("response"));
                        log.put("request_id", requestId + "_" + recordIndex);
                        backendLog.add(log);
                    }
                    break;
                }
                case "provision_boa_od": {
                    Map<String, Object> parsedMessage = parserWithCatch(message);
                    String householdId = (String) ((Map<String, Object>)parsedMessage.get("request")).get("householdId");
                    if (householdId != null && householdToRecordIndex.containsKey(householdId)) {
                        recordIndex = householdToRecordIndex.get(householdId);
                        Map<String, Object> log = new HashMap<>();
                        log.put("timestamp", timestamp);
                        log.put("url", msg);
                        log.put("body", parsedMessage.get("request"));
                        log.put("response", parsedMessage.get("response"));
                        log.put("request_id", requestId + "_" + recordIndex);
                        backendLog.add(log);
                    }
                    break;
                }
            }
        }
        backendLog.sort(Comparator.comparing(a -> (Long) a.get("timestamp")));
        result.put("backend_log", backendLog);

        return result;
    }

    private static Map<String, Object> handleMethod(List<Map<String, String>> data, int index) {
        Map<String, String> logItem = data.get(index);
        String responseMessage = cleanLogMessage(data.get(index + 5).get("@message"));
        Map<String, Object> parsedResponse = parserWithCatch(responseMessage);

        Map<String, Object> response;
        if ("EXTERNAL_API_SUCCESS".equals(data.get(index + 4).get("msg"))) {
            response = parserWithCatch(data.get(index + 5).get("meta"));
        } else {
            response = new HashMap<>();
            response.put("result", parsedResponse);
            response.put("error", parsedResponse);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", logItem.get("@timestamp"));
        result.put("method", logItem.get("meta"));
        result.put("url", cleanUrl(data.get(index + 1).get("meta")));
        result.put("body", parserWithCatch(data.get(index + 2).get("meta")));
        result.put("headers", cleanHeaders(parserWithCatch(data.get(index + 3).get("meta"))));
        result.put("response", response);
        result.put("request_id", logItem.get("@requestId"));
        result.put("response_time", data.get(index + 4).get("@timestamp"));
        return result;
    }

    private static Map<String, Object> handleWsdl(List<Map<String, String>> data, int index) {
        Map<String, String> logItem = data.get(index);
        String functionName = data.get(index + 1).get("msg").replace("functionName: ", "");
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", logItem.get("@timestamp"));
        result.put("method", "SOAP: " + functionName);
        result.put("url", cleanUrl(logItem.get("msg").replace("wsdlUrl: ", "")));
        result.put("body", data.get(index + 2).get("msg").replace("functionArguments: ", ""));
        result.put("headers", data.get(index + 3).get("msg").replace("soapHeaders: ", ""));
        result.put("response", data.get(index + 6).get("meta"));
        result.put("request_id", logItem.get("@requestId"));
        result.put("response_time", data.get(index + 6).get("@timestamp"));
        return result;
    }

    private static List<Map<String, Object>> handleRecords(Map<String, String> logItem, long timestamp) {
        String trimmed = cleanLogMessage(logItem.get("@message"));
        Map<String, Object> parsedData = parserWithCatch(trimmed);
        String requestTime = formatTimestamp(timestamp);
        List<Map<String, Object>> meta = (List<Map<String, Object>>) parsedData.get("meta");
        if (meta == null) {
            return new ArrayList<>();
        }
        return meta.stream().map(item -> {
            Map<String, Object> record = new HashMap<>();
            record.put("request", item.get("body"));
            record.put("request_id", logItem.get("@requestId") + "_" + meta.indexOf(item));
            Map<String, Object> requestContext = new HashMap<>();
            requestContext.put("eventSource", ((Map<String, Object>)meta.get(0)).get("eventSource"));
            requestContext.put("eventSourceARN", maskArnAccountNumber((String)((Map<String, Object>)meta.get(0)).get("eventSourceARN")));
            requestContext.put("requestTime", requestTime);
            record.put("requestContext", requestContext);
            return record;
        }).collect(Collectors.toList());
    }

    private static void handleApiRequest(Map<String, String> logItem, List<Map<String, Object>> uesAPIRequest) {
        String trimmed = cleanLogMessage(logItem.get("@message"));
        Map<String, Object> parsedData = parserWithCatch(trimmed);
        String requestId = logItem.get("@requestId");
        Map<String, Object> request = new HashMap<>();
        request.put("statusCode", parsedData.get("statusCode"));
        request.put("requestContext", parsedData.get("requestContext"));
        request.put("request", filterXmlFromRequest((Map<String, Object>) parsedData.get("request")));
        request.put("response", parsedData.get("response"));
        request.put("request_id", requestId);
        uesAPIRequest.add(request);
    }

    private static Map<String, Object> filterXmlFromRequest(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        String data = input.values().stream().map(String::valueOf).collect(Collectors.joining(""));
        if (data.contains("soapenv:Envelope")) {
            Map<String, Object> request = new HashMap<>();
            // This logic is complex and may need refinement based on the exact XML structure.
            // For now, returning the original input.
            return input;
        }
        return input;
    }

    private static String cleanLogMessage(String log, int length) {
        if (log == null) return "";
        return log.replace("\t", "").replace("\n", "").substring(length);
    }

    private static String cleanLogMessage(String log) {
        return cleanLogMessage(log, 64);
    }

    private static Map<String, Object> parserWithCatch(String data) {
        try {
            if (data.startsWith("\"") && data.endsWith("\"")) {
                data = data.substring(1, data.length() - 1);
                data = data.replace("\\\"", "\"");
            }
            return objectMapper.readValue(data, Map.class);
        } catch (JsonProcessingException e) {
            return Collections.singletonMap("error", data.replace("/", "").replace("\n", "").replace("    ", ""));
        }
    }

    private static String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return null;
        }
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    private static String maskArnAccountNumber(String arn) {
        if (arn == null) return null;
        String[] parts = arn.split(":");
        if (parts.length >= 6) {
            String accountNumber = parts[4];
            String masked = new String(new char[accountNumber.length()]).replace("\0", "*");
            parts[4] = masked;
            return String.join(":", parts);
        }
        return arn;
    }

    private static String cleanUrl(String url) {
        try {
            URL cleanedUrl = new URL(url);
            return cleanedUrl.getProtocol() + "://" + cleanedUrl.getHost() + cleanedUrl.getPath();
        } catch (MalformedURLException e) {
            return url;
        }
    }

    private static Map<String, Object> cleanHeaders(Map<String, Object> headers) {
        if (headers == null) return new HashMap<>();
        headers.remove("Authorization");
        headers.remove("X-CSRF-Token");
        headers.remove("cookie");
        headers.remove("Client-id");
        headers.remove("Signature");
        headers.remove("APIKEY");
        headers.remove("x-api-key");
        headers.remove("pai");
        headers.remove("partner");
        return headers;
    }
}