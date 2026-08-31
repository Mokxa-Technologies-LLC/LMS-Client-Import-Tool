package org.joget.mokxa.adapter;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import org.joget.mokxa.model.*;
import org.joget.mokxa.util.DebugLogger;

public class CaretImportAdapter implements ImportAdapter {

    private final List<String> originalHeaders = new ArrayList<>();

    @Override
    public String getSource() {
        return "caret";
    }

    @Override
    public List<String> getOriginalHeaders() {
        return new ArrayList<>(originalHeaders);
    }

    @Override
    public List<ClientImportRow> read(File file, ImportOptions options) throws Exception {

        if (file == null || !file.exists()) throw new Exception("Caret Excel file does not exist.");

        String name = file.getName().toLowerCase();

        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new Exception("Caret import supports Excel files only (.xlsx or .xls).");
        }

        DebugLogger.info(getClass().getName(), "Reading Caret Excel file=" + file.getName());

        List<ClientImportRow> rows = readExcel(file);

        if (rows.isEmpty()) throw new Exception("Caret Excel file contains no client records.");

        DebugLogger.info(getClass().getName(), "Caret Excel rows loaded=" + rows.size());

        return rows;
    }

    private List<ClientImportRow> readExcel(File file) throws Exception {

        List<ClientImportRow> rows = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file)) {

            if (workbook.getNumberOfSheets() == 0) throw new Exception("Caret Excel file contains no worksheets.");

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new Exception("Caret Excel first worksheet could not be resolved.");

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new Exception("Caret Excel header row is missing.");

            readHeaders(headerRow, formatter, evaluator);
            validateHeaders();

            int lastRow = sheet.getLastRowNum();

            for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {

                Row excelRow = sheet.getRow(rowIndex);
                if (excelRow == null || isEmptyRow(excelRow, formatter, evaluator)) continue;

                Map<String, String> originalRow = readOriginalRow(excelRow, formatter, evaluator);

                ClientImportRow row = mapRow(originalRow, rowIndex + 1);

                row.setOriginalHeaders(new ArrayList<>(originalHeaders));
                row.setOriginalRow(originalRow);
                row.setFingerprint(sha256(buildFingerprint(originalRow)));

                rows.add(row);
            }
        }

        return rows;
    }

    private void readHeaders(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) throws Exception {

        originalHeaders.clear();

        int lastCell = headerRow.getLastCellNum();
        if (lastCell <= 0) throw new Exception("Caret Excel header row is empty.");

        Set<String> duplicateHeaders = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();

        for (int i = 0; i < lastCell; i++) {

            String header = cellValue(headerRow.getCell(i), formatter, evaluator);

            if (i == 0 && !header.isEmpty() && header.charAt(0) == '\uFEFF') {
                header = header.substring(1);
            }

            header = header.trim();

            if (!header.isEmpty() && seen.contains(header)) duplicateHeaders.add(header);
            if (!header.isEmpty()) seen.add(header);

            originalHeaders.add(header);
        }

        if (!duplicateHeaders.isEmpty()) {
            throw new Exception("Caret Excel contains duplicate column headers: " + duplicateHeaders);
        }
    }

    private Map<String, String> readOriginalRow(Row excelRow, DataFormatter formatter, FormulaEvaluator evaluator) {

        Map<String, String> source = new LinkedHashMap<>();

        for (int i = 0; i < originalHeaders.size(); i++) {

            String header = originalHeaders.get(i);
            if (header.isEmpty()) continue;

            String value = cellValue(excelRow.getCell(i), formatter, evaluator);
            source.put(header, value);
        }

        return source;
    }

    private ClientImportRow mapRow(Map<String, String> source, int rowNumber) {

        ClientImportRow row = new ClientImportRow();

        row.setRowNumber(rowNumber);
        row.setType(get(source, "Type"));
        row.setName(collapseSpaces(get(source, "Name")));
        row.setFirstName(collapseSpaces(get(source, "First Name")));
        row.setMiddleName("");
        row.setLastName(collapseSpaces(get(source, "Last Name")));
        row.setWebsite(get(source, "Web"));

        String organizationName = collapseSpaces(get(source, "Company Name"));

        if ("Company".equalsIgnoreCase(row.getType()) && organizationName.isEmpty()) {
            organizationName = collapseSpaces(get(source, "Name"));
        }

        row.setOrganizationName(organizationName);

        mapPhones(row, source);
        mapEmails(row, source);
        mapAddresses(row, source);
        mapIntegrations(row, source);

        return row;
    }

    // =========================================================================
    // PHONE SOURCE MAPPING
    // =========================================================================

    private void mapPhones(ClientImportRow row, Map<String, String> source) {

        addRawPhone(row, get(source, "Business Phone"), "Business", "No", "Business Phone");
        addRawPhone(row, get(source, "Home Phone"), "Home", "No", "Home Phone");
        addRawPhone(row, get(source, "Mobile Phone"), "Mobile", "Yes", "Mobile Phone");
        addRawPhone(row, get(source, "Phone"), "Other", "No", "Phone");
        addRawPhone(row, get(source, "Phone Number Primary"), "Other", "No", "Phone Number Primary");
    }

    private void addRawPhone(ClientImportRow row, String raw, String type, String mobile, String sourceField) {

        if (isEmpty(raw)) return;

        ImportPhone phone = new ImportPhone();

        phone.setType(type);
        phone.setMobile(mobile);
        phone.setPhone(raw);
        phone.setSourceField(sourceField);
        phone.setSourceValue(raw);

        row.addPhone(phone);
    }

    // =========================================================================
    // EMAIL SOURCE MAPPING
    // =========================================================================

    private void mapEmails(ClientImportRow row, Map<String, String> source) {

        String[] columns = {"Email", "Email 2", "Email 3", "Email 4", "Email 5"};

        for (String column : columns) {

            String raw = get(source, column);
            if (isEmpty(raw)) continue;

            ImportEmail email = new ImportEmail();

            email.setType("Other");
            email.setEmail(raw);
            email.setPrimary(false);
            email.setSourceField(column);
            email.setSourceValue(raw);

            row.addEmail(email);
        }
    }

    // =========================================================================
    // ADDRESS SOURCE MAPPING
    // =========================================================================

    private void mapAddresses(ClientImportRow row, Map<String, String> source) {

        String[][] groups = {
                {"Billing 1", "Billing"},
                {"Billing 2", "Billing"},
                {"Work 1", "Work"},
                {"Work 2", "Work"},
                {"Residence 1", "Residence"},
                {"Residence 2", "Residence"},
                {"Other 1", "Other"},
                {"Other 2", "Other"}
        };

        boolean primary = false;
        Set<String> seen = new LinkedHashSet<>();

        for (String[] group : groups) {

            String prefix = group[0];
            String type = group[1];

            String line1 = collapseSpaces(get(source, prefix + " Address"));
            String line2 = collapseSpaces(get(source, prefix + " Address2"));
            String city = collapseSpaces(get(source, prefix + " City"));
            String state = collapseSpaces(get(source, prefix + " State"));
            String zip = collapseSpaces(get(source, prefix + " Zip"));
            String country = collapseSpaces(get(source, prefix + " Country"));

            if (country.isEmpty()) country = collapseSpaces(get(source, "Country"));

            if (line1.isEmpty() && line2.isEmpty() && city.isEmpty() && state.isEmpty() && zip.isEmpty() && country.isEmpty()) {
                continue;
            }

            String key = lower(type) + "|" + lower(line1) + "|" + lower(line2) + "|" +
                    lower(city) + "|" + lower(state) + "|" + lower(zip) + "|" + lower(country);

            if (seen.contains(key)) continue;
            seen.add(key);

            ImportAddress address = new ImportAddress();

            address.setSourcePrefix(prefix);
            address.setType(type);

            address.setLine1(line1);
            address.setLine2(line2);

            address.setRawCity(city);
            address.setRawState(state);
            address.setRawCountry(country);

            address.setCity(city);
            address.setState("");
            address.setCountry("");

            address.setZip(zip);
            address.setPrimary(!primary);

            primary = true;

            row.addAddress(address);
        }
    }

    private void mapIntegrations(ClientImportRow row, Map<String, String> source) {
        ClientIntegrationOptions integration = new ClientIntegrationOptions();

        integration.setTeamsSyncMode(get(source, "Teams Sync Mode"));
        integration.setTeamsUrl(get(source, "Teams URL"));
        integration.setSharePointSiteUrl(get(source, "SharePoint Site URL"));

        integration.setQbSyncMode(get(source, "QuickBooks Sync Mode"));
        integration.setQbCustomerId(get(source, "QuickBooks Customer ID"));

        row.setIntegrations(integration);
    }


    // =========================================================================
    // HEADER VALIDATION
    // =========================================================================

    private void validateHeaders() throws Exception {

        String[] required = {
                "Type",
                "Name",
                "First Name",
                "Last Name",
                "Company Name",
                "Email",
                "Business Phone",
                "Mobile Phone",
                "Phone Number Primary"
        };

        List<String> missing = new ArrayList<>();

        for (String requiredHeader : required) {
            if (!originalHeaders.contains(requiredHeader)) missing.add(requiredHeader);
        }

        if (!missing.isEmpty()) {
            throw new Exception("Invalid Caret Excel file. Missing columns: " + missing);
        }
    }

    // =========================================================================
    // SOURCE FINGERPRINT
    // =========================================================================

    private String buildFingerprint(Map<String, String> row) {

        StringBuilder result = new StringBuilder();

        for (String header : originalHeaders) {

            if (header.isEmpty()) continue;

            result.append(header.toLowerCase());
            result.append('=');
            result.append(collapseSpaces(get(row, header)).toLowerCase());
            result.append('\u001F');
        }

        return result.toString();
    }

    private String sha256(String text) throws Exception {

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(value(text).getBytes("UTF-8"));

        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {

            String hex = Integer.toHexString(b & 0xff);

            if (hex.length() == 1) result.append('0');

            result.append(hex);
        }

        return result.toString();
    }

    // =========================================================================
    // EXCEL HELPERS
    // =========================================================================

    private String cellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {

        if (cell == null) return "";

        try {
            return value(formatter.formatCellValue(cell, evaluator));
        } catch (Exception e) {
            return value(formatter.formatCellValue(cell));
        }
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {

        if (row == null) return true;

        for (int i = 0; i < originalHeaders.size(); i++) {
            if (!cellValue(row.getCell(i), formatter, evaluator).isEmpty()) return false;
        }

        return true;
    }

    // =========================================================================
    // COMMON HELPERS
    // =========================================================================

    private String get(Map<String, String> row, String column) {
        return value(row.get(column));
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String collapseSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String lower(String value) {
        return value(value).toLowerCase();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}