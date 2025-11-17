package com.example.ues_portal.validation;

import com.example.ues_portal.model.CloudWatchRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ConditionalValidator implements ConstraintValidator<ConditionalValidation, CloudWatchRequest> {

    @Override
    public boolean isValid(CloudWatchRequest request, ConstraintValidatorContext context) {
        boolean isValid = true;
        if ("logGroups".equals(request.getQuery_type())) {
            if (request.getStart_time() != null) {
                context.buildConstraintViolationWithTemplate("start_time must be null when query_type is logGroups")
                        .addPropertyNode("start_time").addConstraintViolation();
                isValid = false;
            }
            if (request.getEnd_time() != null) {
                context.buildConstraintViolationWithTemplate("end_time must be null when query_type is logGroups")
                        .addPropertyNode("end_time").addConstraintViolation();
                isValid = false;
            }
            if (request.getQuery() != null) {
                context.buildConstraintViolationWithTemplate("query must be null when query_type is logGroups")
                        .addPropertyNode("query").addConstraintViolation();
                isValid = false;
            }
        } else if ("consolidated".equals(request.getQuery_type())) {
            if (request.getStart_time() == null) {
                context.buildConstraintViolationWithTemplate("start_time is required when query_type is consolidated")
                        .addPropertyNode("start_time").addConstraintViolation();
                isValid = false;
            }
            if (request.getEnd_time() == null) {
                context.buildConstraintViolationWithTemplate("end_time is required when query_type is consolidated")
                        .addPropertyNode("end_time").addConstraintViolation();
                isValid = false;
            }
            if (request.getQuery() == null) {
                context.buildConstraintViolationWithTemplate("query is required when query_type is consolidated")
                        .addPropertyNode("query").addConstraintViolation();
                isValid = false;
            }
            if (request.getLog_group_name_list() == null || request.getLog_group_name_list().isEmpty()) {
                context.buildConstraintViolationWithTemplate("log_group_name_list is required when query_type is consolidated")
                        .addPropertyNode("log_group_name_list").addConstraintViolation();
                isValid = false;
            }
        } else {
            context.buildConstraintViolationWithTemplate("Invalid query_type")
                    .addPropertyNode("query_type").addConstraintViolation();
            isValid = false;
        }
        return isValid;
    }
}