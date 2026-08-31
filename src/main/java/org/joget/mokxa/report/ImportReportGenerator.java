package org.joget.mokxa.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.joget.apps.form.service.FileUtil;

import org.joget.mokxa.adapter.ImportAdapter;
import org.joget.mokxa.model.ClientImportRow;
import org.joget.mokxa.model.DuplicateMergeResult;
import org.joget.mokxa.model.ImportIssue;
import org.joget.mokxa.model.ImportOptions;
import org.joget.mokxa.model.ImportResult;
import org.joget.mokxa.util.DebugLogger;
import org.joget.mokxa.util.ImportUtil;
import org.joget.mokxa.util.JogetFormUtil;

public class ImportReportGenerator {

    private final Map properties;
    private final ImportOptions options;

    public ImportReportGenerator(Map properties) {
        this.properties = properties;
        this.options = ImportOptions.fromProperties(properties);
    }

    public void generateAndStore(ImportResult result, ImportAdapter adapter) {

        List<String> reportFiles = new ArrayList<>();

        try {
            String recordId = JogetFormUtil.resolveControlRecordId(properties);
            String tableName = JogetFormUtil.property(properties, "controlFormTable", "lms_client_import");

            if (ImportUtil.isEmpty(recordId)) {
                DebugLogger.warn(getClass().getName(), "Reports not stored because control record ID is empty");
                return;
            }

            String source = ImportUtil.value(adapter.getSource()).toLowerCase();
            if (source.isEmpty()) source = "client";

            String suffix = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());;
            if (ImportUtil.isEmpty(suffix)) suffix = String.valueOf(System.currentTimeMillis());

            if (options.isGenerateSummaryReport()) {
                File file = createSummaryReport(result,  "Import_Summary_" + suffix + ".txt");
                store(file, tableName, recordId);
                reportFiles.add(file.getName());
            }

            if (options.isGenerateFailedReport() && !result.getFailedRows().isEmpty()) {
                File file = createFailedReport(result, adapter,  "Failed_Clients_" + suffix + ".xlsx");
                store(file, tableName, recordId);
                reportFiles.add(file.getName());
            }

            if (options.isGenerateDuplicateReport() && hasDuplicateRows(result)) {
                File file = createDuplicateReport(
                        result,
                        adapter,
                        "Duplicate_Clients_" + suffix + ".xlsx"
                );
                store(file, tableName, recordId);
                reportFiles.add(file.getName());
            }

            if (options.isGenerateUnresolvedReport() && hasReviewIssues(result)) {
                File file = createUnresolvedReport(result, "Review_Items_" + suffix + ".xlsx");
                store(file, tableName, recordId);
                reportFiles.add(file.getName());
            }

            updateReportFileField(reportFiles);

            JogetFormUtil.markImportCompleted(properties, result.getStatus(), result.getSummary());

            DebugLogger.info(getClass().getName(), "Reports generated=" + reportFiles);

        } catch (Exception e) {
            DebugLogger.error(getClass().getName(), e, "Import completed but report generation failed");
        }
    }

    // =========================================================================
    // SUMMARY
    // =========================================================================

    private File createSummaryReport(ImportResult result, String fileName) throws Exception {

        File file = tempFile(fileName);
        String summary = ImportUtil.value(result.getSummary());

        if (summary.isEmpty()) summary = result.buildSummary();

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("LMS Client Import Summary\n");
            writer.write("=========================\n\n");
            writer.write("Run ID: " + result.getRunId() + "\n");
            writer.write("Status: " + result.getStatus() + "\n\n");
            writer.write(summary + "\n");
        }

        return file;
    }

    // =========================================================================
    // FAILED CLIENTS
    // =========================================================================

    private File createFailedReport(ImportResult result, ImportAdapter adapter, String fileName) throws Exception {

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Failed Clients");
            List<String> headers = new ArrayList<>(adapter.getOriginalHeaders());
            headers.add("Import Issue Description");

            writeHeader(sheet, headers, workbook);

            int rowIndex = 1;

            for (ClientImportRow client : result.getFailedRows()) {

                Row excelRow = sheet.createRow(rowIndex++);

                int column = 0;

                for (String header : adapter.getOriginalHeaders()) {
                    setCell(excelRow, column++, client.getOriginalValue(header));
                }

                setCell(excelRow, column, buildIssueDescription(client));
            }

            applyBasicLayout(sheet, headers.size());

            return writeWorkbook(workbook, fileName);
        }
    }

    private String buildIssueDescription(ClientImportRow row) {

        Set<String> messages = new LinkedHashSet<>();

        for (ImportIssue issue : row.getIssues()) {
            if (!ImportUtil.isEmpty(issue.getMessage())) messages.add(issue.getMessage());
        }

        if (messages.isEmpty() && !ImportUtil.isEmpty(row.getError())) {
            messages.add(row.getError());
        }

        return ImportUtil.join(new ArrayList<>(messages), " | ");
    }

    // =========================================================================
    // DUPLICATES
    // =========================================================================

    private File createDuplicateReport(ImportResult result, ImportAdapter adapter, String fileName) throws Exception {

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Duplicate Clients");

            List<String> headers = new ArrayList<>(adapter.getOriginalHeaders());

            headers.add("Duplicate Review Description");
            headers.add("Matched Client ID");
            headers.add("Duplicate Match Basis");

            writeHeader(sheet, headers, workbook);

            Map<Integer, DuplicateMergeResult> duplicateMap = new HashMap<>();

            for (DuplicateMergeResult duplicate : result.getDuplicateRows()) {
                duplicateMap.put(duplicate.getExcelRow(), duplicate);
            }

            int rowIndex = 1;

            for (ClientImportRow client : result.getRows()) {

                DuplicateMergeResult duplicate = duplicateMap.get(client.getRowNumber());
                boolean sourceDuplicate = "SOURCE_DUPLICATE".equalsIgnoreCase(client.getStatus());

                if (duplicate == null && !sourceDuplicate) continue;

                Row excelRow = sheet.createRow(rowIndex++);
                int column = 0;

                // Keep source data exactly as uploaded
                for (String header : adapter.getOriginalHeaders()) {
                    setCell(excelRow, column++, client.getOriginalValue(header));
                }

                if (sourceDuplicate) {
                    setCell(excelRow, column++, "Exact duplicate row found in the uploaded Excel file. Review and modify before re-importing if this should be a separate client.");
                    setCell(excelRow, column++, client.getClientId());
                    setCell(excelRow, column, "EXACT_SOURCE_ROW");
                } else {
                    setCell(excelRow, column++, "Existing LMS client matched this row. Review the source values before re-importing if this should be a separate client.");
                    setCell(excelRow, column++, duplicate.getClientId());
                    setCell(excelRow, column, duplicate.getMatchBasis());
                }
            }

            applyBasicLayout(sheet, headers.size());

            return writeWorkbook(workbook, fileName);
        }
    }

    private Map<String, String> buildMergedRowMap(ImportResult result) {

        Map<String, Set<Integer>> map = new HashMap<>();

        for (DuplicateMergeResult duplicate : result.getDuplicateRows()) {
            addMergedRow(map, duplicate.getClientId(), duplicate.getExcelRow());
        }

        for (ClientImportRow row : result.getRows()) {
            if ("SOURCE_DUPLICATE".equalsIgnoreCase(row.getStatus())) {
                addMergedRow(map, row.getClientId(), row.getRowNumber());
            }
        }

        Map<String, String> output = new HashMap<>();

        for (Map.Entry<String, Set<Integer>> entry : map.entrySet()) {

            List<String> values = new ArrayList<>();

            for (Integer row : entry.getValue()) {
                values.add(String.valueOf(row));
            }

            output.put(entry.getKey(), ImportUtil.join(values, ", "));
        }

        return output;
    }

    private void addMergedRow(Map<String, Set<Integer>> map, String clientId, int excelRow) {

        if (ImportUtil.isEmpty(clientId)) return;

        Set<Integer> rows = map.computeIfAbsent(clientId, k -> new LinkedHashSet<>());
        rows.add(excelRow);
    }

    // =========================================================================
    // UNRESOLVED / REVIEW
    // =========================================================================

    private File createUnresolvedReport(ImportResult result, String fileName) throws Exception {

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Unresolved Details");

            String[] headers = {
                    "Risk",
                    "Excel Row",
                    "Caret Type",
                    "Client ID",
                    "Record Name",
                    "Issue Area",
                    "Source Field",
                    "Source Value",
                    "Issue Code",
                    "Issue Description",
                    "Outcome",
                    "Manual Action"
            };

            writeHeader(sheet, headers, workbook);

            Map<Integer, ClientImportRow> rowsByNumber = new HashMap<>();

            for (ClientImportRow row : result.getRows()) {
                rowsByNumber.put(row.getRowNumber(), row);
            }

            int rowIndex = 1;

            for (ImportIssue issue : result.getIssues()) {

                if (issue.isHighRisk()) continue;

                ClientImportRow client = rowsByNumber.get(issue.getRowNumber());

                String clientId = ImportUtil.value(issue.getClientId());

                if (clientId.isEmpty() && client != null) {
                    clientId = ImportUtil.value(client.getClientId());
                }

                Row row = sheet.createRow(rowIndex++);
                int c = 0;

                setCell(row, c++, issue.getRisk());
                setCell(row, c++, issue.getRowNumber());
                setCell(row, c++, issue.getType());
                setCell(row, c++, clientId);
                setCell(row, c++, client == null ? "" : client.getClientDisplayName());
                setCell(row, c++, issueArea(issue));
                setCell(row, c++, issue.getField());
                setCell(row, c++, issue.getValue());
                setCell(row, c++, issue.getCode());
                setCell(row, c++, issue.getMessage());
                setCell(row, c++, outcome(issue));
                setCell(row, c, issue.getAction());
            }

            applyBasicLayout(sheet, headers.length);

            return writeWorkbook(workbook, fileName);
        }
    }

    private String issueArea(ImportIssue issue) {

        String code = ImportUtil.upper(issue.getCode());

        if (code.contains("PHONE")) return "Phone";
        if (code.contains("EMAIL")) return "Email";
        if (code.contains("ADDRESS")) return "Address";
        if (code.contains("ORGANIZATION")) return "Organization";
        if (code.contains("WEB")) return "Web";

        return ImportUtil.value(issue.getField());
    }

    private String outcome(ImportIssue issue) {

        String code = ImportUtil.upper(issue.getCode());

        if (code.contains("PHONE")) return "PHONE_NOT_ADDED";
        if (code.contains("EMAIL")) return "EMAIL_NOT_ADDED";
        if (code.contains("ADDRESS")) return "ADDRESS_IMPORTED_WITH_LOCATION_REVIEW";
        if (code.contains("ORGANIZATION")) return "INDIVIDUAL_IMPORTED_RELATIONSHIP_NOT_ADDED";
        if (code.contains("WEB")) return "WEB_NOT_ADDED";

        return "REVIEW_REQUIRED";
    }

    // =========================================================================
    // JOGET FILE STORAGE
    // =========================================================================

    private void store(File file, String tableName, String recordId) {

        if (file == null || !file.exists()) return;

        DebugLogger.info(getClass().getName(), "Storing report=" + file.getName());

        FileUtil.storeFile(file, tableName, recordId);
    }

    private void updateReportFileField(List<String> reportFiles) {

        if (reportFiles.isEmpty()) return;

        String formId = JogetFormUtil.property(properties, "controlFormId", "client_import");
        String tableName = JogetFormUtil.property(properties, "controlFormTable", "lms_client_import");
        String recordId = JogetFormUtil.resolveControlRecordId(properties);
        String reportField = JogetFormUtil.property(properties, "reportFileField", "report_file");

        String files = ImportUtil.join(reportFiles, ";");

        JogetFormUtil.updateField(formId, tableName, recordId, reportField, files);
    }

    // =========================================================================
    // EXCEL HELPERS
    // =========================================================================

    private File writeWorkbook(Workbook workbook, String fileName) throws Exception {

        File file = tempFile(fileName);

        try (FileOutputStream out = new FileOutputStream(file)) {
            workbook.write(out);
        }

        return file;
    }

    private void writeHeader(Sheet sheet, List<String> headers, Workbook workbook) {

        Row row = sheet.createRow(0);
        CellStyle style = headerStyle(workbook);

        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(safeExcelValue(headers.get(i)));
            cell.setCellStyle(style);
        }
    }

    private void writeHeader(Sheet sheet, String[] headers, Workbook workbook) {

        Row row = sheet.createRow(0);
        CellStyle style = headerStyle(workbook);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(safeExcelValue(headers[i]));
            cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {

        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);

        return style;
    }

    private void applyBasicLayout(Sheet sheet, int columns) {

        sheet.createFreezePane(0, 1);
        sheet.setDefaultRowHeightInPoints(18);

        for (int i = 0; i < columns; i++) {
            sheet.setColumnWidth(i, 22 * 256);
        }
    }

    private void setCell(Row row, int column, Object value) {

        Cell cell = row.createCell(column);

        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(safeExcelValue(ImportUtil.value(value)));
        }
    }

    private String safeExcelValue(String value) {

        String result = ImportUtil.value(value);

        // Excel cell text maximum is 32,767 characters.
        if (result.length() > 32767) {
            result = result.substring(0, 32767);
        }

        /*
         * Prevent report values from being interpreted as formulas when the
         * generated workbook is opened.
         */
        if (result.startsWith("=") || result.startsWith("+") ||
                result.startsWith("-") || result.startsWith("@")) {

            result = "'" + result;
        }

        return result;
    }

    private File tempFile(String fileName) throws Exception {

        String safeName = ImportUtil.sanitizeFileName(fileName);
        File directory = new File(System.getProperty("java.io.tmpdir"), "lms-client-import-reports");

        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("Unable to create temporary report directory: " + directory.getAbsolutePath());
        }

        return new File(directory, safeName);
    }

    // =========================================================================
    // CHECKS
    // =========================================================================

    private boolean hasDuplicateRows(ImportResult result) {

        if (!result.getDuplicateRows().isEmpty()) return true;

        for (ClientImportRow row : result.getRows()) {
            if ("SOURCE_DUPLICATE".equalsIgnoreCase(row.getStatus())) return true;
        }

        return false;
    }

    private boolean hasReviewIssues(ImportResult result) {

        for (ImportIssue issue : result.getIssues()) {
            if (!issue.isHighRisk()) return true;
        }

        return false;
    }
}