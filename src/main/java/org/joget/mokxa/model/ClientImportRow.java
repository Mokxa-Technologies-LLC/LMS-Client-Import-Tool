package org.joget.mokxa.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClientImportRow {

    private int rowNumber;

    private String type;

    private String name;
    private String firstName;
    private String middleName;
    private String lastName;

    private String organizationName;
    private String website;

    private String clientId;
    private String organizationId;

    private String status;
    private String error;
    private String fingerprint;

    private int issueCount;

    private List<ImportPhone> phones = new ArrayList<>();
    private List<ImportEmail> emails = new ArrayList<>();
    private List<ImportAddress> addresses = new ArrayList<>();
    private List<ImportIssue> issues = new ArrayList<>();

    private Map<String, String> originalRow = new LinkedHashMap<>();
    private List<String> originalHeaders = new ArrayList<>();

    private ClientIntegrationOptions integrations = new ClientIntegrationOptions();

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(int issueCount) {
        this.issueCount = issueCount;
    }

    public List<ImportPhone> getPhones() {
        return phones;
    }

    public void setPhones(List<ImportPhone> phones) {
        this.phones = phones == null ? new ArrayList<>() : phones;
    }

    public List<ImportEmail> getEmails() {
        return emails;
    }

    public void setEmails(List<ImportEmail> emails) {
        this.emails = emails == null ? new ArrayList<>() : emails;
    }

    public List<ImportAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<ImportAddress> addresses) {
        this.addresses = addresses == null ? new ArrayList<>() : addresses;
    }

    public List<ImportIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ImportIssue> issues) {
        this.issues = issues == null ? new ArrayList<>() : issues;
    }

    public Map<String, String> getOriginalRow() {
        return originalRow;
    }

    public void setOriginalRow(Map<String, String> originalRow) {
        this.originalRow = originalRow == null ? new LinkedHashMap<>() : originalRow;
    }

    public List<String> getOriginalHeaders() {
        return originalHeaders;
    }

    public void setOriginalHeaders(List<String> originalHeaders) {
        this.originalHeaders = originalHeaders == null ? new ArrayList<>() : originalHeaders;
    }

    public ClientIntegrationOptions getIntegrations() {
        return integrations;
    }

    public void setIntegrations(ClientIntegrationOptions integrations) {
        this.integrations = integrations == null ? new ClientIntegrationOptions() : integrations;
    }

    public void addPhone(ImportPhone phone) {
        if (phone != null) phones.add(phone);
    }

    public void addEmail(ImportEmail email) {
        if (email != null) emails.add(email);
    }

    public void addAddress(ImportAddress address) {
        if (address != null) addresses.add(address);
    }

    public void addIssue(ImportIssue issue) {
        if (issue == null) return;

        issues.add(issue);
        issueCount++;
    }

    public String getClientDisplayName() {

        if ("Company".equalsIgnoreCase(type)) {
            return value(organizationName);
        }

        String fullName = value(firstName) + " " + value(middleName) + " " + value(lastName);
        return fullName.trim().replaceAll("\\s+", " ");
    }

    public boolean isPerson() {
        return "Person".equalsIgnoreCase(type);
    }

    public boolean isCompany() {
        return "Company".equalsIgnoreCase(type);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }

    public boolean hasIssues() {
        return issueCount > 0;
    }

    public String getOriginalValue(String field) {
        String value = originalRow.get(field);
        return value == null ? "" : value.trim();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}