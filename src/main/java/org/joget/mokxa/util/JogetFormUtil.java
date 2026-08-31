package org.joget.mokxa.util;

import java.util.Date;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.workflow.util.WorkflowUtil;

public final class JogetFormUtil {

    private JogetFormUtil() {
    }

    // =========================================================================
    // FORM DATA DAO
    // =========================================================================

    public static FormDataDao getFormDataDao() {
        return (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    // =========================================================================
    // CURRENT USER
    // =========================================================================

    public static String getCurrentUsername() {

        try {
            String username = WorkflowUtil.getCurrentUsername();
            if (!ImportUtil.isEmpty(username)) return username;
        } catch (Exception ignore) {
        }

        return "system";
    }

    public static String getCurrentUserFullName() {

        try {
            String name = WorkflowUtil.getCurrentUserFullName();
            if (!ImportUtil.isEmpty(name)) return name;
        } catch (Exception ignore) {
        }

        return getCurrentUsername();
    }

    // =========================================================================
    // CREATE ROW
    // =========================================================================

    public static FormRow createRow(String id) {

        Date now = new Date();
        String username = getCurrentUsername();
        String userFullName = getCurrentUserFullName();

        FormRow row = new FormRow();

        row.setId(ImportUtil.isEmpty(id) ? ImportUtil.uuid() : id);
        row.setDateCreated(now);
        row.setDateModified(now);

        row.put("createdBy", username);
        row.put("modifiedBy", username);
        row.put("createdByName", userFullName);
        row.put("modifiedByName", userFullName);

        return row;
    }

    public static FormRow createRow(String id, Map<String, String> values) {

        FormRow row = createRow(id);

        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() != null) row.setProperty(entry.getKey(), ImportUtil.value(entry.getValue()));
            }
        }

        return row;
    }

    // =========================================================================
    // INSERT / SAVE
    // =========================================================================

    public static void save(String formDefId, String tableName, FormRow row) {

        if (row == null) return;

        FormRowSet rows = new FormRowSet();
        rows.add(row);

        getFormDataDao().saveOrUpdate(formDefId, tableName, rows);
    }

    public static void save(String formDefId, String tableName, FormRowSet rows) {

        if (rows == null || rows.isEmpty()) return;

        getFormDataDao().saveOrUpdate(formDefId, tableName, rows);
    }

    public static String insert(String formDefId, String tableName, Map<String, String> values) {

        FormRow row = createRow("", values);
        save(formDefId, tableName, row);

        return row.getId();
    }

    public static String insert(String formDefId, String tableName, String id, Map<String, String> values) {

        FormRow row = createRow(id, values);
        save(formDefId, tableName, row);

        return row.getId();
    }

    // =========================================================================
    // LOAD
    // =========================================================================

    public static FormRow load(String formDefId, String tableName, String recordId) {

        if (ImportUtil.isEmpty(recordId)) return null;

        return getFormDataDao().load(formDefId, tableName, recordId);
    }

    public static boolean exists(String formDefId, String tableName, String recordId) {
        return load(formDefId, tableName, recordId) != null;
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    public static void update(String formDefId, String tableName, String recordId, Map<String, String> values) {

        if (ImportUtil.isEmpty(recordId)) return;

        FormRow row = load(formDefId, tableName, recordId);
        if (row == null) return;

        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() != null) row.setProperty(entry.getKey(), ImportUtil.value(entry.getValue()));
            }
        }

        applyModifiedAudit(row);
        save(formDefId, tableName, row);
    }

    public static void updateField(String formDefId, String tableName, String recordId, String field, String value) {

        FormRow row = load(formDefId, tableName, recordId);
        if (row == null) return;

        row.setProperty(field, ImportUtil.value(value));

        applyModifiedAudit(row);
        save(formDefId, tableName, row);
    }

    private static void applyModifiedAudit(FormRow row) {

        row.setDateModified(new Date());
        row.put("modifiedBy", getCurrentUsername());
        row.put("modifiedByName", getCurrentUserFullName());
    }

    // =========================================================================
    // FIND
    // =========================================================================

    public static FormRowSet find(String formDefId, String tableName, String condition, Object[] params) {

        return getFormDataDao().find(
                formDefId,
                tableName,
                condition,
                params,
                null,
                null,
                null,
                null
        );
    }

    public static FormRow findOne(String formDefId, String tableName, String condition, Object[] params) {

        FormRowSet rows = getFormDataDao().find(
                formDefId,
                tableName,
                condition,
                params,
                null,
                null,
                0,
                1
        );

        if (rows == null || rows.isEmpty()) return null;

        return rows.get(0);
    }

    // =========================================================================
    // PROGRESS
    // =========================================================================

    public static void updateProgress(Map properties, String status, int processedRows, int totalRows,
                                      int successRows, int failedRows, int duplicateRows, String message) {

        String formDefId = property(properties, "controlFormId", "client_import");
        String tableName = property(properties, "controlFormTable", "lms_client_import");
        String recordId = resolveControlRecordId(properties);

        if (ImportUtil.isEmpty(recordId)) {
            DebugLogger.warn(JogetFormUtil.class.getName(), "Progress not updated because control record ID is empty");
            return;
        }

        FormRow row = load(formDefId, tableName, recordId);

        if (row == null) {
            row = createRow(recordId);
        }

        int progress = calculateProgress(processedRows, totalRows);

        row.setProperty(property(properties, "statusField", "status"), status);
        row.setProperty(property(properties, "progressField", "progress"), String.valueOf(progress));
        row.setProperty(property(properties, "totalRowsField", "total_rows"), String.valueOf(totalRows));
        row.setProperty(property(properties, "processedRowsField", "processed_rows"), String.valueOf(processedRows));
        row.setProperty(property(properties, "successRowsField", "success_rows"), String.valueOf(successRows));
        row.setProperty(property(properties, "failedRowsField", "failed_rows"), String.valueOf(failedRows));
        row.setProperty(property(properties, "duplicateRowsField", "duplicate_rows"), String.valueOf(duplicateRows));
        row.setProperty(property(properties, "messageField", "current_message"), ImportUtil.value(message));

        applyModifiedAudit(row);
        save(formDefId, tableName, row);
    }

    public static void markImportFailed(Map properties, String message) {

        String formDefId = property(properties, "controlFormId", "client_import");
        String tableName = property(properties, "controlFormTable", "lms_client_import");
        String recordId = resolveControlRecordId(properties);

        if (ImportUtil.isEmpty(recordId)) return;

        FormRow row = load(formDefId, tableName, recordId);

        if (row == null) {
            row = createRow(recordId);
        }

        row.setProperty(property(properties, "statusField", "status"), "FAILED");
        row.setProperty(property(properties, "messageField", "current_message"), ImportUtil.safeLog(message));

        applyModifiedAudit(row);
        save(formDefId, tableName, row);
    }

    public static void markImportCompleted(Map properties, String status, String summary) {

        String formDefId = property(properties, "controlFormId", "client_import");
        String tableName = property(properties, "controlFormTable", "lms_client_import");
        String recordId = resolveControlRecordId(properties);

        if (ImportUtil.isEmpty(recordId)) return;

        FormRow row = load(formDefId, tableName, recordId);
        if (row == null) row = createRow(recordId);

        row.setProperty(property(properties, "statusField", "status"), status);
        row.setProperty(property(properties, "progressField", "progress"), "100");
        row.setProperty(property(properties, "summaryField", "summary"), ImportUtil.value(summary));
        row.setProperty(property(properties, "messageField", "current_message"), "Client import completed");

        applyModifiedAudit(row);
        save(formDefId, tableName, row);
    }

    // =========================================================================
    // REPORT FIELDS
    // =========================================================================

    public static void updateReportFields(Map properties, String fileNames, String reportUrls) {

        String formDefId = property(properties, "controlFormId", "client_import");
        String tableName = property(properties, "controlFormTable", "lms_client_import");
        String recordId = resolveControlRecordId(properties);

        if (ImportUtil.isEmpty(recordId)) return;

        FormRow row = load(formDefId, tableName, recordId);
        if (row == null) row = createRow(recordId);

        row.setProperty(property(properties, "reportFileField", "report_file"), ImportUtil.value(fileNames));
        row.setProperty(property(properties, "reportUrlField", "report_url"), ImportUtil.value(reportUrls));

        applyModifiedAudit(row);
        save(formDefId, tableName, row);
    }

    // =========================================================================
    // CONTROL RECORD
    // =========================================================================

    public static String resolveControlRecordId(Map properties) {

        String recordId = property(properties, "controlRecordId", "");

        if (ImportUtil.isEmpty(recordId)) recordId = property(properties, "recordId", "");

        return recordId;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    public static int calculateProgress(int processedRows, int totalRows) {

        if (totalRows <= 0) return 0;
        if (processedRows <= 0) return 0;
        if (processedRows >= totalRows) return 100;

        return (int) Math.floor((processedRows * 100.0) / totalRows);
    }

    public static String property(Map properties, String name, String defaultValue) {

        if (properties == null) return defaultValue;

        Object value = properties.get(name);

        if (value == null || String.valueOf(value).trim().isEmpty()) return defaultValue;

        return String.valueOf(value).trim();
    }
}