package org.joget.mokxa.model;

public class ImportEmail {

    private String type;
    private String email;
    private boolean primary;

    private String sourceField;
    private String sourceValue;

    public ImportEmail() {
    }

    public ImportEmail(String type, String email, boolean primary) {
        this.type = type;
        this.email = email;
        this.primary = primary;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getSourceValue() {
        return sourceValue;
    }

    public void setSourceValue(String sourceValue) {
        this.sourceValue = sourceValue;
    }

    public String getNormalizedEmail() {
        return email == null ? "" : email.trim().toLowerCase();
    }
}