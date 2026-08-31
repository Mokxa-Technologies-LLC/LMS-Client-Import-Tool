package org.joget.mokxa.util;

import java.io.File;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.service.FileUtil;

public final class ImportFileUtil {

    private ImportFileUtil() {}

    public static File resolveUploadedFile(java.util.Map properties) throws Exception {
        String formId = JogetFormUtil.property(properties, "controlFormId", "client_import");
        String tableName = JogetFormUtil.property(properties, "controlFormTable", "lms_client_import");
        String fileField = JogetFormUtil.property(properties, "inputFileField", "import_file");
        String recordId = JogetFormUtil.resolveControlRecordId(properties);

        if (ImportUtil.isEmpty(recordId)) {
            throw new Exception("Import control record ID is empty.");
        }

        FormDataDao formDataDao = JogetFormUtil.getFormDataDao();
        FormRow row = formDataDao.load(formId, tableName, recordId);

        if (row == null) {
            throw new Exception("Import control record not found: " + recordId);
        }

        String storedValue = ImportUtil.value(row.getProperty(fileField));

        if (storedValue.isEmpty()) {
            throw new Exception("No Excel file found in field: " + fileField);
        }

        String fileName = findExcelFile(storedValue);

        if (fileName.isEmpty()) {
            throw new Exception("No valid Excel file found. Only .xlsx or .xls files are supported.");
        }

        File file = FileUtil.getFile(fileName, tableName, recordId);

        if (file == null || !file.exists()) {
            throw new Exception("Uploaded Excel file could not be found: " + fileName);
        }

        if (!file.isFile()) {
            throw new Exception("Resolved Excel path is not a file: " + fileName);
        }

        if (file.length() <= 0) {
            throw new Exception("Uploaded Excel file is empty: " + fileName);
        }

        DebugLogger.info(ImportFileUtil.class.getName(),
                "Resolved input file | recordId=" + recordId +
                        " field=" + fileField +
                        " file=" + fileName +
                        " bytes=" + file.length());

        return file;
    }

    private static String findExcelFile(String storedValue) {
        String[] files = storedValue.split(";");

        for (String file : files) {
            String fileName = cleanFileName(file);
            String lower = fileName.toLowerCase();

            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                return fileName;
            }
        }

        return "";
    }

    private static String cleanFileName(String fileName) {
        String value = ImportUtil.value(fileName);

        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1).trim();
        }

        return value;
    }
}