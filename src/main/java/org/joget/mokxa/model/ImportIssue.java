package org.joget.mokxa.model;

public class ImportIssue {

    private String risk;
    private String code;
    private int rowNumber;
    private String type;
    private String clientId;

    private String field;
    private String value;
    private String message;
    private String action;

    public ImportIssue() {
    }

    public ImportIssue(String risk, String code, int rowNumber, String type, String clientId,
                       String field, String value, String message, String action) {

        this.risk = risk;
        this.code = code;
        this.rowNumber = rowNumber;
        this.type = type;
        this.clientId = clientId;
        this.field = field;
        this.value = value;
        this.message = message;
        this.action = action;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(risk);
    }

    public boolean isMediumRisk() {
        return "MEDIUM".equalsIgnoreCase(risk);
    }

    public boolean isLowRisk() {
        return "LOW".equalsIgnoreCase(risk);
    }
}