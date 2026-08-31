package org.joget.mokxa.model;

public class ImportAddress {

    private String type;

    private String line1;
    private String line2;

    private String city;
    private String state;
    private String country;
    private String zip;

    private String rawCity;
    private String rawState;
    private String rawCountry;

    private boolean primary;

    private String sourcePrefix;

    public ImportAddress() {
    }

    public ImportAddress(String type, String line1, String line2, String city, String state, String country, String zip, boolean primary) {
        this.type = type;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.country = country;
        this.zip = zip;
        this.primary = primary;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getLine2() {
        return line2;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getRawCity() {
        return rawCity;
    }

    public void setRawCity(String rawCity) {
        this.rawCity = rawCity;
    }

    public String getRawState() {
        return rawState;
    }

    public void setRawState(String rawState) {
        this.rawState = rawState;
    }

    public String getRawCountry() {
        return rawCountry;
    }

    public void setRawCountry(String rawCountry) {
        this.rawCountry = rawCountry;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getSourcePrefix() {
        return sourcePrefix;
    }

    public void setSourcePrefix(String sourcePrefix) {
        this.sourcePrefix = sourcePrefix;
    }

    public String getUniqueKey() {
        return safe(type).toLowerCase()
                + "|" + safe(line1).toLowerCase()
                + "|" + safe(line2).toLowerCase()
                + "|" + safe(zip).toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}