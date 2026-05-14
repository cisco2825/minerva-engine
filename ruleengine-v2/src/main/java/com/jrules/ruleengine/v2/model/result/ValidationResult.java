package com.jrules.ruleengine.v2.model.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ValidationResult {

    private boolean valid;
    private List<ValidationError> errors;
    private List<ValidationError> warnings;

    @Data
    @Builder
    public static class ValidationError {
        private String code;
        private String severity;
        private String stage;
        private String location;
        private String message;
        private String suggestion;
        private TokenPosition position;
    }

    @Data
    @Builder
    public static class TokenPosition {
        private int line;
        private int column;
        private int length;
    }
}
