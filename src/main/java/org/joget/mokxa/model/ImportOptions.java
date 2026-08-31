package org.joget.mokxa.model;

import java.util.Map;

public class ImportOptions {

    private boolean addInvalidClientNames;
    private boolean addInvalidPhoneNumbers;
    private boolean addInvalidEmails;
    private boolean addInvalidAddresses;

    private boolean enrichExistingClients;
    private boolean createOrganizationFromPersonOnly;

    private boolean generateSummaryReport;
    private boolean generateFailedReport;
    private boolean generateDuplicateReport;
    private boolean generateUnresolvedReport;

    private int progressUpdateInterval;

    public static ImportOptions fromProperties(Map properties) {

        ImportOptions options = new ImportOptions();

        options.setAddInvalidClientNames(getBoolean(properties, "addInvalidClientNames", false));
        options.setAddInvalidPhoneNumbers(getBoolean(properties, "addInvalidPhoneNumbers", false));
        options.setAddInvalidEmails(getBoolean(properties, "addInvalidEmails", false));
        options.setAddInvalidAddresses(getBoolean(properties, "addInvalidAddresses", true));

        options.setEnrichExistingClients(getBoolean(properties, "enrichExistingClients", true));
        options.setCreateOrganizationFromPersonOnly(getBoolean(properties, "createOrganizationFromPersonOnly", false));

        options.setGenerateSummaryReport(getBoolean(properties, "generateSummaryReport", true));
        options.setGenerateFailedReport(getBoolean(properties, "generateFailedReport", true));
        options.setGenerateDuplicateReport(getBoolean(properties, "generateDuplicateReport", true));
        options.setGenerateUnresolvedReport(getBoolean(properties, "generateUnresolvedReport", true));

        options.setProgressUpdateInterval(getInt(properties, "progressUpdateInterval", 25));

        if (options.getProgressUpdateInterval() <= 0) {
            options.setProgressUpdateInterval(25);
        }

        return options;
    }

    private static boolean getBoolean(Map properties, String key, boolean defaultValue) {

        if (properties == null) return defaultValue;

        Object value = properties.get(key);

        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;

        String text = String.valueOf(value).trim();

        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;

        return defaultValue;
    }

    private static int getInt(Map properties, String key, int defaultValue) {

        if (properties == null) return defaultValue;

        Object value = properties.get(key);
        if (value == null) return defaultValue;

        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean isAddInvalidClientNames() {
        return addInvalidClientNames;
    }

    public void setAddInvalidClientNames(boolean addInvalidClientNames) {
        this.addInvalidClientNames = addInvalidClientNames;
    }

    public boolean isAddInvalidPhoneNumbers() {
        return addInvalidPhoneNumbers;
    }

    public void setAddInvalidPhoneNumbers(boolean addInvalidPhoneNumbers) {
        this.addInvalidPhoneNumbers = addInvalidPhoneNumbers;
    }

    public boolean isAddInvalidEmails() {
        return addInvalidEmails;
    }

    public void setAddInvalidEmails(boolean addInvalidEmails) {
        this.addInvalidEmails = addInvalidEmails;
    }

    public boolean isAddInvalidAddresses() {
        return addInvalidAddresses;
    }

    public void setAddInvalidAddresses(boolean addInvalidAddresses) {
        this.addInvalidAddresses = addInvalidAddresses;
    }

    public boolean isEnrichExistingClients() {
        return enrichExistingClients;
    }

    public void setEnrichExistingClients(boolean enrichExistingClients) {
        this.enrichExistingClients = enrichExistingClients;
    }

    public boolean isCreateOrganizationFromPersonOnly() {
        return createOrganizationFromPersonOnly;
    }

    public void setCreateOrganizationFromPersonOnly(boolean createOrganizationFromPersonOnly) {
        this.createOrganizationFromPersonOnly = createOrganizationFromPersonOnly;
    }

    public boolean isGenerateSummaryReport() {
        return generateSummaryReport;
    }

    public void setGenerateSummaryReport(boolean generateSummaryReport) {
        this.generateSummaryReport = generateSummaryReport;
    }

    public boolean isGenerateFailedReport() {
        return generateFailedReport;
    }

    public void setGenerateFailedReport(boolean generateFailedReport) {
        this.generateFailedReport = generateFailedReport;
    }

    public boolean isGenerateDuplicateReport() {
        return generateDuplicateReport;
    }

    public void setGenerateDuplicateReport(boolean generateDuplicateReport) {
        this.generateDuplicateReport = generateDuplicateReport;
    }

    public boolean isGenerateUnresolvedReport() {
        return generateUnresolvedReport;
    }

    public void setGenerateUnresolvedReport(boolean generateUnresolvedReport) {
        this.generateUnresolvedReport = generateUnresolvedReport;
    }

    public int getProgressUpdateInterval() {
        return progressUpdateInterval;
    }

    public void setProgressUpdateInterval(int progressUpdateInterval) {
        this.progressUpdateInterval = progressUpdateInterval;
    }
}