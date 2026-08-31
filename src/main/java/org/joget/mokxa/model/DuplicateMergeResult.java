package org.joget.mokxa.model;

import org.joget.commons.util.TimeZoneUtil;

public class DuplicateMergeResult {

    private int excelRow;
    private String originalExcelRow;

    private String type;
    private String clientId;
    private String organizationId;

    private String clientNameBefore;
    private String clientNameAfter;

    private String matchBasis;

    private String sourceName;
    private String sourceCompany;

    private String phonesBefore;
    private String phonesIncoming;
    private String phonesAddedOrChanged;
    private String phonesAfter;

    private String emailsBefore;
    private String emailsIncoming;
    private String emailsAddedOrChanged;
    private String emailsAfter;

    private String addressesBefore;
    private String addressesIncoming;
    private String addressesAddedOrChanged;
    private String addressesAfter;

    private String result;

    public int getExcelRow() {
        return excelRow;
    }

    public void setExcelRow(int excelRow) {
        this.excelRow = excelRow;
    }

    public String getOriginalExcelRow() {
        return originalExcelRow;
    }

    public void setOriginalExcelRow(String originalExcelRow) {
        this.originalExcelRow = originalExcelRow;
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

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getClientNameBefore() {
        return clientNameBefore;
    }

    public void setClientNameBefore(String clientNameBefore) {
        this.clientNameBefore = clientNameBefore;
    }

    public String getClientNameAfter() {
        return clientNameAfter;
    }

    public void setClientNameAfter(String clientNameAfter) {
        this.clientNameAfter = clientNameAfter;
    }

    public String getMatchBasis() {
        return matchBasis;
    }

    public void setMatchBasis(String matchBasis) {
        this.matchBasis = matchBasis;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceCompany() {
        return sourceCompany;
    }

    public void setSourceCompany(String sourceCompany) {
        this.sourceCompany = sourceCompany;
    }

    public String getPhonesBefore() {
        return phonesBefore;
    }

    public void setPhonesBefore(String phonesBefore) {
        this.phonesBefore = phonesBefore;
    }

    public String getPhonesIncoming() {
        return phonesIncoming;
    }

    public void setPhonesIncoming(String phonesIncoming) {
        this.phonesIncoming = phonesIncoming;
    }

    public String getPhonesAddedOrChanged() {
        return phonesAddedOrChanged;
    }

    public void setPhonesAddedOrChanged(String phonesAddedOrChanged) {
        this.phonesAddedOrChanged = phonesAddedOrChanged;
    }

    public String getPhonesAfter() {
        return phonesAfter;
    }

    public void setPhonesAfter(String phonesAfter) {
        this.phonesAfter = phonesAfter;
    }

    public String getEmailsBefore() {
        return emailsBefore;
    }

    public void setEmailsBefore(String emailsBefore) {
        this.emailsBefore = emailsBefore;
    }

    public String getEmailsIncoming() {
        return emailsIncoming;
    }

    public void setEmailsIncoming(String emailsIncoming) {
        this.emailsIncoming = emailsIncoming;
    }

    public String getEmailsAddedOrChanged() {
        return emailsAddedOrChanged;
    }

    public void setEmailsAddedOrChanged(String emailsAddedOrChanged) {
        this.emailsAddedOrChanged = emailsAddedOrChanged;
    }

    public String getEmailsAfter() {
        return emailsAfter;
    }

    public void setEmailsAfter(String emailsAfter) {
        this.emailsAfter = emailsAfter;
    }

    public String getAddressesBefore() {
        return addressesBefore;
    }

    public void setAddressesBefore(String addressesBefore) {
        this.addressesBefore = addressesBefore;
    }

    public String getAddressesIncoming() {
        return addressesIncoming;
    }

    public void setAddressesIncoming(String addressesIncoming) {
        this.addressesIncoming = addressesIncoming;
    }

    public String getAddressesAddedOrChanged() {
        return addressesAddedOrChanged;
    }

    public void setAddressesAddedOrChanged(String addressesAddedOrChanged) {
        this.addressesAddedOrChanged = addressesAddedOrChanged;
    }

    public String getAddressesAfter() {
        return addressesAfter;
    }

    public void setAddressesAfter(String addressesAfter) {
        this.addressesAfter = addressesAfter;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}