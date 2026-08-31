package org.joget.mokxa.processor;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.joget.commons.util.SecurityUtil;
import org.json.JSONObject;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;

import org.joget.mokxa.model.ClientImportRow;
import org.joget.mokxa.model.ClientIntegrationOptions;
import org.joget.mokxa.model.ImportIssue;
import org.joget.mokxa.model.ImportResult;
import org.joget.mokxa.util.DebugLogger;
import org.joget.mokxa.util.ImportUtil;
import org.joget.mokxa.util.JogetFormUtil;

public class ClientIntegrationProcessor {

    private static final String CLIENT_FORM = "lms_client";
    private static final String CLIENT_TABLE = "lms_client";
    private static final String TEAMS_ID_FIELD = "ms_teams_id";

    private static final String SP_FORM = "sharePointMeta";
    private static final String SP_TABLE = "lms_sharepoint_meta";

    private static final String MS_SETTINGS_FORM = "lms_ms_settings";
    private static final String MS_SETTINGS_TABLE = "lms_ms_settings";
    private static final String MS_SETTINGS_ID = "MS_GLOBAL_SETTINGS";

    private final Map properties;
    private final FormDataDao formDataDao;
    private final WorkflowManager workflowManager;

    public ClientIntegrationProcessor(Map properties) {
        this.properties = properties;
        this.formDataDao = JogetFormUtil.getFormDataDao();
        this.workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
    }

    public void process(ClientImportRow row, String clientId, ImportResult result) {
        if (row == null || ImportUtil.isEmpty(clientId)) return;

        ClientIntegrationOptions integration = row.getIntegrations();
        if (integration == null) return;
        processTeams(row, clientId, integration, result);
        processQuickBooks(row, clientId, integration, result);
    }

    // =========================================================================
    // TEAMS / SHAREPOINT
    // =========================================================================

    private void processTeams(ClientImportRow row, String clientId, ClientIntegrationOptions integration, ImportResult result) {
        String mode = normalizeMode(integration.getTeamsSyncMode());

        if (mode.isEmpty() || "NONE".equals(mode)) return;

        try {
            if ("AUTOMATIC".equals(mode)) {
                startTeamsProcess(clientId);
                return;
            }

            if ("MANUAL".equals(mode)) {
                mapManualTeams(row, clientId, integration);
                return;
            }

            addIssue(row, result, "MEDIUM", "TEAMS_SYNC_MODE_INVALID", "Teams Sync Mode",
                    integration.getTeamsSyncMode(), "Teams Sync Mode must be Automatic, Manual or blank.",
                    "Correct the Teams Sync Mode and retry the integration.");

        } catch (Exception e) {
            addIssue(row, result, "MEDIUM", "TEAMS_SYNC_FAILED", "Teams Sync Mode",
                    integration.getTeamsSyncMode(), rootMessage(e),
                    "Retry Teams/SharePoint synchronization for this client.");

            DebugLogger.error(getClass().getName(), e, "Teams sync failed for client=" + clientId);
        }
    }

    private void startTeamsProcess(String clientId) throws Exception {
        String processDefId = JogetFormUtil.property(properties, "teamsSyncProcessDefId",
                "lms#latest#teamsIntegrationProcess");

        Map vars = new HashMap();
        vars.put("clientId", clientId);
        vars.put("module", "CONTACT");

        WorkflowProcessResult processResult = workflowManager.processStart(processDefId, vars);

        String processId = "";
        if (processResult != null && processResult.getProcess() != null) {
            processId = processResult.getProcess().getInstanceId();
        }

        DebugLogger.info(getClass().getName(),
                "Teams integration process started | clientId=" + clientId + " processId=" + processId);
    }

    private void mapManualTeams(ClientImportRow row, String clientId, ClientIntegrationOptions integration) throws Exception {
        String teamsUrl = ImportUtil.value(integration.getTeamsUrl());

        if (teamsUrl.isEmpty()) {
            throw new Exception("Teams URL is required when Teams Sync Mode is Manual.");
        }

        String teamId = extractQueryParameter(teamsUrl, "groupId");

        if (teamId.isEmpty()) {
            throw new Exception("Unable to extract groupId from Teams URL.");
        }

        updateClientField(clientId, TEAMS_ID_FIELD, teamId);

        String sharePointUrl = ImportUtil.value(integration.getSharePointSiteUrl());
        SharePointMeta meta;

        if (!sharePointUrl.isEmpty()) {
            meta = resolveSharePointFromUrl(sharePointUrl);
        } else {
            meta = resolveSharePointFromTeam(teamId);
        }

        saveSharePointMeta(clientId, meta.siteId, meta.driveId);

        DebugLogger.info(getClass().getName(),
                "Manual Teams mapping completed | clientId=" + clientId +
                        " teamId=" + teamId + " siteId=" + meta.siteId + " driveId=" + meta.driveId);
    }

    // =========================================================================
    // QUICKBOOKS
    // =========================================================================

    private void processQuickBooks(ClientImportRow row, String clientId,
                                   ClientIntegrationOptions integration, ImportResult result) {

        String mode = normalizeMode(integration.getQbSyncMode());

        if (mode.isEmpty() || "NONE".equals(mode)) return;

        try {
            if ("MANUAL".equals(mode)) {
                String qbReference = ImportUtil.value(integration.getQbCustomerId());
                if (qbReference.isEmpty()) {
                    throw new Exception("QuickBooks Customer ID or URL is required when QuickBooks Sync Mode is Manual.");
                }

                String qbCustomerId = resolveQbCustomerId(qbReference);
                if (qbCustomerId.isEmpty()) {
                    throw new Exception("Unable to resolve QuickBooks Customer ID from value: " + qbReference);
                }

                String qbField = JogetFormUtil.property(properties, "qbCustomerIdField", "qb_customer_id");
                updateClientField(clientId, qbField, qbCustomerId);
                DebugLogger.info(getClass().getName(),
                        "Manual QB mapping completed | clientId=" + clientId + " qbCustomerId=" + qbCustomerId);

                return;
            }

            if ("AUTOMATIC".equals(mode)) {
                startQuickBooksProcess(clientId,row.isCompany());
                return;
            }

            addIssue(row, result, "MEDIUM", "QB_SYNC_MODE_INVALID", "QuickBooks Sync Mode",
                    integration.getQbSyncMode(), "QuickBooks Sync Mode must be Automatic, Manual or blank.",
                    "Correct the QuickBooks Sync Mode and retry the integration.");

        } catch (Exception e) {
            addIssue(row, result, "MEDIUM", "QB_SYNC_FAILED", "QuickBooks Sync Mode",
                    integration.getQbSyncMode(), rootMessage(e),
                    "Retry QuickBooks synchronization for this client.");

            DebugLogger.error(getClass().getName(), e, "QuickBooks sync failed for client=" + clientId);
        }
    }

    private String resolveQbCustomerId(String input) throws Exception {
        String value = ImportUtil.value(input);
        if (value.isEmpty()) return "";

        if (value.startsWith("http://") || value.startsWith("https://")) {
            String nameId = extractQueryParameter(value, "nameId");
            return ImportUtil.value(nameId);
        }

        return value;
    }

    private void startQuickBooksProcess(String clientId, boolean isCompany) throws Exception {
        String processDefId = JogetFormUtil.property(properties, "qbSyncProcessDefId", "");

        if (processDefId.isEmpty()) {
            throw new Exception("QuickBooks Sync Process Definition ID is not configured.");
        }

        Map vars = new HashMap();
        vars.put("clientId", clientId);
        vars.put("isOrganisation", isCompany? "Y" : "N");
        vars.put("billingApprovalSync","false");

        WorkflowProcessResult processResult = workflowManager.processStart(processDefId, vars);

        String processId = "";
        if (processResult != null && processResult.getProcess() != null) {
            processId = processResult.getProcess().getInstanceId();
        }

        DebugLogger.info(getClass().getName(),
                "QuickBooks integration process started | clientId=" + clientId + " processId=" + processId);
    }

    // =========================================================================
    // SHAREPOINT GRAPH
    // =========================================================================

    private SharePointMeta resolveSharePointFromTeam(String teamId) throws Exception {
        GraphSettings settings = loadGraphSettings();

        int maxAttempts = intProperty("sharePointMaxAttempts", 10);
        int retryDelayMs = intProperty("sharePointRetryDelayMs", 15000);

        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String token = getAccessToken(settings);

                JSONObject site = graphGet(
                        "https://graph.microsoft.com/v1.0/groups/" + teamId + "/sites/root", token);

                String siteId = site.optString("id", "");

                if (siteId.isEmpty()) throw new Exception("SharePoint Site ID missing for Team ID " + teamId);

                String driveId = resolveDriveId(siteId, token);

                if (!driveId.isEmpty()) return new SharePointMeta(siteId, driveId);

            } catch (Exception e) {
                lastError = e;

                if (attempt < maxAttempts) {
                    DebugLogger.warn(getClass().getName(),
                            "SharePoint not ready | teamId=" + teamId + " attempt=" + attempt + "/" + maxAttempts);

                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new Exception("SharePoint polling interrupted.", interrupted);
                    }
                }
            }
        }

        throw lastError == null
                ? new Exception("Unable to resolve SharePoint metadata for Team ID " + teamId)
                : lastError;
    }

    private SharePointMeta resolveSharePointFromUrl(String sharePointUrl) throws Exception {
        GraphSettings settings = loadGraphSettings();
        String token = getAccessToken(settings);

        URI uri = new URI(sharePointUrl);

        String host = ImportUtil.value(uri.getHost());
        String path = ImportUtil.value(uri.getPath());

        if (host.isEmpty() || path.isEmpty()) {
            throw new Exception("Invalid SharePoint Site URL: " + sharePointUrl);
        }

        String graphUrl = "https://graph.microsoft.com/v1.0/sites/" + host + ":" + path;

        JSONObject site = graphGet(graphUrl, token);
        String siteId = site.optString("id", "");

        if (siteId.isEmpty()) {
            throw new Exception("SharePoint Site ID missing for URL: " + sharePointUrl);
        }

        String driveId = resolveDriveId(siteId, token);

        if (driveId.isEmpty()) {
            throw new Exception("SharePoint Drive ID missing for Site ID: " + siteId);
        }

        return new SharePointMeta(siteId, driveId);
    }

    private String resolveDriveId(String siteId, String token) throws Exception {
        JSONObject drive = graphGet(
                "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drive", token);

        return drive.optString("id", "");
    }

    private GraphSettings loadGraphSettings() throws Exception {
        FormRow row = formDataDao.load(MS_SETTINGS_FORM, MS_SETTINGS_TABLE, MS_SETTINGS_ID);

        if (row == null) {
            throw new Exception("Microsoft Graph settings record not found: " + MS_SETTINGS_ID);
        }

        GraphSettings settings = new GraphSettings();

        settings.tenantId = ImportUtil.value(row.getProperty("graph_tenant_id"));
        settings.clientId = ImportUtil.value(row.getProperty("graph_client_id"));
        settings.clientSecret = SecurityUtil.decrypt(row.getProperty("graph_client_secret"));


        if (settings.tenantId.isEmpty() || settings.clientId.isEmpty() || settings.clientSecret.isEmpty()) {
            throw new Exception("Microsoft Graph settings are incomplete.");
        }

        return settings;
    }

    private String getAccessToken(GraphSettings settings) throws Exception {
        String url = "https://login.microsoftonline.com/" + settings.tenantId + "/oauth2/v2.0/token";

        String body = "client_id=" + URLEncoder.encode(settings.clientId, "UTF-8") +
                "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", "UTF-8") +
                "&client_secret=" + URLEncoder.encode(settings.clientSecret, "UTF-8") +
                "&grant_type=client_credentials";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(new StringEntity(body, "UTF-8"));

            try (CloseableHttpResponse response = client.execute(post)) {
                int code = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

                if (code != 200) {
                    throw new Exception("Microsoft Graph token request failed. HTTP " + code + " Body=" + responseBody);
                }

                String token = new JSONObject(responseBody).optString("access_token", "");
                if (token.isEmpty()) throw new Exception("Microsoft Graph access token missing.");

                return token;
            }
        }
    }

    private JSONObject graphGet(String url, String accessToken) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            get.setHeader("Authorization", "Bearer " + accessToken);
            get.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = client.execute(get)) {
                int code = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");

                if (code != 200) {
                    throw new Exception("Microsoft Graph request failed. HTTP " + code + " URL=" + url + " Body=" + body);
                }

                return new JSONObject(body);
            }
        }
    }

    // =========================================================================
    // DATABASE
    // =========================================================================

    private void updateClientField(String clientId, String field, String value) {
        Map<String, String> values = new HashMap<>();
        values.put(field, value);

        JogetFormUtil.update(CLIENT_FORM, CLIENT_TABLE, clientId, values);
    }

    private void saveSharePointMeta(String clientId, String siteId, String driveId) {
        FormRow existing = formDataDao.load(SP_FORM, SP_TABLE, clientId);

        if (existing == null) {
            FormRow row = JogetFormUtil.createRow(clientId);
            row.setProperty("site_id", siteId);
            row.setProperty("drive_id", driveId);
            JogetFormUtil.save(SP_FORM, SP_TABLE, row);
            return;
        }

        Map<String, String> values = new HashMap<>();
        values.put("site_id", siteId);
        values.put("drive_id", driveId);

        JogetFormUtil.update(SP_FORM, SP_TABLE, clientId, values);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String extractQueryParameter(String url, String name) throws Exception {
        URI uri = new URI(url);
        String query = uri.getRawQuery();

        if (query == null) return "";

        for (String item : query.split("&")) {
            String[] parts = item.split("=", 2);

            if (parts.length == 2 && name.equalsIgnoreCase(URLDecoder.decode(parts[0], "UTF-8"))) {
                return URLDecoder.decode(parts[1], "UTF-8").trim();
            }
        }

        return "";
    }

    private String normalizeMode(String value) {
        String mode = ImportUtil.upper(value);

        if ("AUTO".equals(mode) || "YES".equals(mode) || "TRUE".equals(mode) || "1".equals(mode)) {
            return "AUTOMATIC";
        }

        if ("MANUAL".equals(mode)) return "MANUAL";
        if ("NO".equals(mode) || "FALSE".equals(mode) || "0".equals(mode)) return "NONE";

        return mode;
    }

    private int intProperty(String name, int defaultValue) {
        try {
            return Integer.parseInt(JogetFormUtil.property(properties, name, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void addIssue(ClientImportRow row, ImportResult result, String risk, String code,
                          String field, String value, String message, String action) {

        ImportIssue issue = new ImportIssue(risk, code, row.getRowNumber(), row.getType(),
                row.getClientId(), field, value, message, action);

        row.addIssue(issue);
        result.addIssue(issue);
    }

    private String rootMessage(Throwable e) {
        if (e == null) return "Unknown integration error.";

        Throwable current = e;
        while (current.getCause() != null) current = current.getCause();

        String message = current.getMessage();
        return ImportUtil.isEmpty(message) ? current.getClass().getSimpleName() : ImportUtil.safeLog(message);
    }

    private static class GraphSettings {
        String tenantId;
        String clientId;
        String clientSecret;
    }

    private static class SharePointMeta {
        String siteId;
        String driveId;

        SharePointMeta(String siteId, String driveId) {
            this.siteId = siteId;
            this.driveId = driveId;
        }
    }
}