package org.joget.mokxa.model;

public class ImportPhone {

    private String type;
    private String countryCode;
    private String phone;
    private String extension;
    private String mobile;
    private boolean primary;

    private String sourceField;
    private String sourceValue;

    public ImportPhone() {
    }

    public ImportPhone(String type, String countryCode, String phone, String extension, String mobile, boolean primary) {
        this.type = type;
        this.countryCode = countryCode;
        this.phone = phone;
        this.extension = extension;
        this.mobile = mobile;
        this.primary = primary;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
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

    public String getUniqueKey() {
        return safe(countryCode) + "|" + safe(phone);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}