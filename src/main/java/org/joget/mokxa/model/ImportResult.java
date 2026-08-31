package org.joget.mokxa.model;

import org.joget.commons.util.SecurityUtil;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    private int sourceRows;
    private int rowsSucceeded;
    private int rowsFailed;
    private int rowsWithWarnings;
    private int sourceDuplicates;

    private int clientsCreated;
    private int clientsMatched;

    private int organizationsCreated;
    private int organizationsMatched;

    private int individualsCreated;
    private int individualsMatched;

    private int phonesCreated;
    private int emailsCreated;
    private int addressesCreated;
    private int socialCreated;
    private int mapsCreated;

    private int phonesSkippedExisting;
    private int emailsSkippedExisting;
    private int addressesSkippedExisting;

    private String status;
    private String summary;
    private String runId;

    private List<ClientImportRow> rows = new ArrayList<>();
    private List<ClientImportRow> failedRows = new ArrayList<>();
    private List<ImportIssue> issues = new ArrayList<>();
    private List<DuplicateMergeResult> duplicateRows = new ArrayList<>();

    public int getSourceRows() {
        return sourceRows;
    }

    public void setSourceRows(int sourceRows) {
        this.sourceRows = sourceRows;
    }

    public int getRowsSucceeded() {
        return rowsSucceeded;
    }

    public void setRowsSucceeded(int rowsSucceeded) {
        this.rowsSucceeded = rowsSucceeded;
    }

    public int getRowsFailed() {
        return rowsFailed;
    }

    public void setRowsFailed(int rowsFailed) {
        this.rowsFailed = rowsFailed;
    }

    public int getRowsWithWarnings() {
        return rowsWithWarnings;
    }

    public void setRowsWithWarnings(int rowsWithWarnings) {
        this.rowsWithWarnings = rowsWithWarnings;
    }

    public int getSourceDuplicates() {
        return sourceDuplicates;
    }

    public void setSourceDuplicates(int sourceDuplicates) {
        this.sourceDuplicates = sourceDuplicates;
    }

    public int getClientsCreated() {
        return clientsCreated;
    }

    public void setClientsCreated(int clientsCreated) {
        this.clientsCreated = clientsCreated;
    }

    public int getClientsMatched() {
        return clientsMatched;
    }

    public void setClientsMatched(int clientsMatched) {
        this.clientsMatched = clientsMatched;
    }

    public int getOrganizationsCreated() {
        return organizationsCreated;
    }

    public void setOrganizationsCreated(int organizationsCreated) {
        this.organizationsCreated = organizationsCreated;
    }

    public int getOrganizationsMatched() {
        return organizationsMatched;
    }

    public void setOrganizationsMatched(int organizationsMatched) {
        this.organizationsMatched = organizationsMatched;
    }

    public int getIndividualsCreated() {
        return individualsCreated;
    }

    public void setIndividualsCreated(int individualsCreated) {
        this.individualsCreated = individualsCreated;
    }

    public int getIndividualsMatched() {
        return individualsMatched;
    }

    public void setIndividualsMatched(int individualsMatched) {
        this.individualsMatched = individualsMatched;
    }

    public int getPhonesCreated() {
        return phonesCreated;
    }

    public void setPhonesCreated(int phonesCreated) {
        this.phonesCreated = phonesCreated;
    }

    public int getEmailsCreated() {
        return emailsCreated;
    }

    public void setEmailsCreated(int emailsCreated) {
        this.emailsCreated = emailsCreated;
    }

    public int getAddressesCreated() {
        return addressesCreated;
    }

    public void setAddressesCreated(int addressesCreated) {
        this.addressesCreated = addressesCreated;
    }

    public int getSocialCreated() {
        return socialCreated;
    }

    public void setSocialCreated(int socialCreated) {
        this.socialCreated = socialCreated;
    }

    public int getMapsCreated() {
        return mapsCreated;
    }

    public void setMapsCreated(int mapsCreated) {
        this.mapsCreated = mapsCreated;
    }

    public int getPhonesSkippedExisting() {
        return phonesSkippedExisting;
    }

    public void setPhonesSkippedExisting(int phonesSkippedExisting) {
        this.phonesSkippedExisting = phonesSkippedExisting;
    }

    public int getEmailsSkippedExisting() {
        return emailsSkippedExisting;
    }

    public void setEmailsSkippedExisting(int emailsSkippedExisting) {
        this.emailsSkippedExisting = emailsSkippedExisting;
    }

    public int getAddressesSkippedExisting() {
        return addressesSkippedExisting;
    }

    public void setAddressesSkippedExisting(int addressesSkippedExisting) {
        this.addressesSkippedExisting = addressesSkippedExisting;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary == null ? "" : summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public List<ClientImportRow> getRows() {

        return rows;
    }

    public void setRows(List<ClientImportRow> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    public List<ClientImportRow> getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(List<ClientImportRow> failedRows) {
        this.failedRows = failedRows == null ? new ArrayList<>() : failedRows;
    }

    public List<ImportIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ImportIssue> issues) {
        this.issues = issues == null ? new ArrayList<>() : issues;
    }

    public List<DuplicateMergeResult> getDuplicateRows() {
        return duplicateRows;
    }

    public void setDuplicateRows(List<DuplicateMergeResult> duplicateRows) {
        this.duplicateRows = duplicateRows == null ? new ArrayList<>() : duplicateRows;
    }

    public void addFailedRow(ClientImportRow row) {
        if (row != null) failedRows.add(row);
    }

    public void addIssue(ImportIssue issue) {
        if (issue != null) issues.add(issue);
    }

    public void addDuplicateRow(DuplicateMergeResult duplicate) {
        if (duplicate != null) duplicateRows.add(duplicate);
    }

    public void incrementRowsSucceeded() {
        rowsSucceeded++;
    }

    public void incrementRowsFailed() {
        rowsFailed++;
    }

    public void incrementRowsWithWarnings() {
        rowsWithWarnings++;
    }

    public void incrementSourceDuplicates() {
        sourceDuplicates++;
    }

    public void incrementClientsCreated() {
        clientsCreated++;
    }

    public void incrementClientsMatched() {
        clientsMatched++;
    }

    public void incrementOrganizationsCreated() {
        organizationsCreated++;
    }

    public void incrementOrganizationsMatched() {
        organizationsMatched++;
    }

    public void incrementIndividualsCreated() {
        individualsCreated++;
    }

    public void incrementIndividualsMatched() {
        individualsMatched++;
    }

    public void incrementPhonesCreated() {
        phonesCreated++;
    }

    public void incrementEmailsCreated() {
        emailsCreated++;
    }

    public void incrementAddressesCreated() {
        addressesCreated++;
    }

    public void incrementSocialCreated() {
        socialCreated++;
    }

    public void incrementMapsCreated() {
        mapsCreated++;
    }

    public void incrementPhonesSkippedExisting() {
        phonesSkippedExisting++;
    }

    public void incrementEmailsSkippedExisting() {
        emailsSkippedExisting++;
    }

    public void incrementAddressesSkippedExisting() {
        addressesSkippedExisting++;
    }

    public int getReviewItems() {
        return issues.size();
    }

    public String buildSummary() {

        summary =
                "sourceRows=" + sourceRows +
                        " rowsSucceeded=" + rowsSucceeded +
                        " rowsFailed=" + rowsFailed +
                        " rowsWithWarnings=" + rowsWithWarnings +
                        " sourceDuplicates=" + sourceDuplicates +
                        " clientsCreated=" + clientsCreated +
                        " clientsMatched=" + clientsMatched +
                        " organizationsCreated=" + organizationsCreated +
                        " organizationsMatched=" + organizationsMatched +
                        " individualsCreated=" + individualsCreated +
                        " individualsMatched=" + individualsMatched +
                        " phonesCreated=" + phonesCreated +
                        " emailsCreated=" + emailsCreated +
                        " addressesCreated=" + addressesCreated +
                        " socialCreated=" + socialCreated +
                        " mapsCreated=" + mapsCreated +
                        " reviewItems=" + issues.size();

        return summary;
    }
}