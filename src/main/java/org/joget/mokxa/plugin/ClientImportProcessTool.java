package org.joget.mokxa.plugin;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.mokxa.adapter.CaretImportAdapter;
import org.joget.mokxa.adapter.ImportAdapter;
import org.joget.mokxa.model.ClientImportRow;
import org.joget.mokxa.model.ImportOptions;
import org.joget.mokxa.model.ImportResult;
import org.joget.mokxa.processor.ClientImportProcessor;
import org.joget.mokxa.report.ImportReportGenerator;
import org.joget.mokxa.util.DebugLogger;
import org.joget.mokxa.util.ImportFileUtil;
import org.joget.mokxa.util.JogetFormUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

public class ClientImportProcessTool extends DefaultApplicationPlugin {

    @Override
    public String getName() {
        return "LMS Client Import Tool";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Imports clients from supported external systems into LMS with duplicate handling, enrichment, progress tracking and reporting.";
    }

    @Override
    public String getLabel() {
        return "LMS - Client Import Tool";
    }

    @Override
    public Object execute(Map properties) {

        DebugLogger.init(properties);
        DebugLogger.info(getClassName(), "Client import execution started");

        try {

            String source = value(properties.get("source"));
            if (isEmpty(source)) throw new IllegalArgumentException("Import source is required.");

            ImportOptions options = ImportOptions.fromProperties(properties);

            JogetFormUtil.updateProgress(
                    properties, "PROCESSING",
                    0, 0, 0, 0, 0,
                    "Preparing " + source + " client import"
            );

            File inputFile = ImportFileUtil.resolveUploadedFile(properties);

            if (inputFile == null || !inputFile.exists()) {
                throw new IllegalArgumentException("Uploaded import file could not be resolved.");
            }

            if (inputFile.length() == 0) {
                throw new IllegalArgumentException("Uploaded import file is empty.");
            }

            DebugLogger.info(
                    getClassName(),
                    "Input file=" + inputFile.getName() + " bytes=" + inputFile.length()
            );

            ImportAdapter adapter = getAdapter(source);

            List<ClientImportRow> rows = adapter.read(inputFile, options);

            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("Import file contains no client records.");
            }

            DebugLogger.info(getClassName(), "Source rows prepared=" + rows.size());

            JogetFormUtil.updateProgress(
                    properties, "PROCESSING",
                    0, rows.size(), 0, 0, 0,
                    "Source file loaded. Starting client import."
            );

            ClientImportProcessor processor = new ClientImportProcessor(properties, options);
            ImportResult result = processor.process(rows);

            ImportReportGenerator reportGenerator = new ImportReportGenerator(properties);
            reportGenerator.generateAndStore(result, adapter);

            DebugLogger.info(getClassName(), "Client import completed | " + result.getSummary());

            return result.getSummary();

        } catch (Exception e) {

            DebugLogger.error(getClassName(), e, "Client import execution failed");

            try {
                JogetFormUtil.markImportFailed(properties, safeMessage(e));
            } catch (Exception statusError) {
                DebugLogger.error(getClassName(), statusError, "Unable to update failed import status");
            }

            return null;

        } finally {

            DebugLogger.info(getClassName(), "Client import execution completed");
            DebugLogger.clear();
        }
    }

    private ImportAdapter getAdapter(String source) {

        if ("caret".equalsIgnoreCase(source)) return new CaretImportAdapter();

        // Future:
        // if ("clio".equalsIgnoreCase(source)) return new ClioImportAdapter();
        // if ("practicePanther".equalsIgnoreCase(source)) return new PracticePantherImportAdapter();

        throw new IllegalArgumentException("Unsupported import source: " + source);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Exception e) {

        if (e == null) return "Unknown import error.";

        String message = e.getMessage();

        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }

        return message.replace('\r', ' ').replace('\n', ' ').trim();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
                getClassName(),
                "/properties/ClientImportProcessTool.json",
                null,
                true,
                null
        );
    }
}