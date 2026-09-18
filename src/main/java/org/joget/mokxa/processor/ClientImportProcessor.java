package org.joget.mokxa.processor;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;

import org.joget.mokxa.model.ClientImportRow;
import org.joget.mokxa.model.DuplicateMergeResult;
import org.joget.mokxa.model.ImportAddress;
import org.joget.mokxa.model.ImportEmail;
import org.joget.mokxa.model.ImportIssue;
import org.joget.mokxa.model.ImportOptions;
import org.joget.mokxa.model.ImportPhone;
import org.joget.mokxa.model.ImportResult;

import org.joget.mokxa.util.DebugLogger;
import org.joget.mokxa.util.ImportUtil;
import org.joget.mokxa.util.JogetFormUtil;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class ClientImportProcessor {

    // =========================================================================
    // LMS FORMS
    // =========================================================================

    private static final String CLIENT_FORM = "lms_client";
    private static final String CLIENT_TABLE = "lms_client";

    private static final String PHONE_FORM = "lms_phone";
    private static final String PHONE_TABLE = "lms_phone";

    private static final String EMAIL_FORM = "email_address";
    private static final String EMAIL_TABLE = "email_address";

    private static final String ADDRESS_FORM = "lms_address";
    private static final String ADDRESS_TABLE = "lms_address";

    private static final String SOCIAL_FORM = "lms_con_social_map";
    private static final String SOCIAL_TABLE = "lms_con_social_map";

    private static final String CLIENT_MAP_FORM = "lms_client_map";
    private static final String CLIENT_MAP_TABLE = "lms_client_map";

    private static final String COUNTRY_CODE_FORM = "lms_country_code";
    private static final String COUNTRY_CODE_TABLE = "lms_country_code";

    private static final String COUNTRY_FORM = "lms_country";
    private static final String COUNTRY_TABLE = "lms_country";

    private static final String STATE_FORM = "lms_state";
    private static final String STATE_TABLE = "lms_state";

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    private final Map properties;
    private final ImportOptions options;

    private final FormDataDao formDataDao;
    private final TransactionTemplate transactionTemplate;

    private String clientIdPrefix;
    private int clientIdPad;

    private String defaultPhoneType;
    private String defaultEmailType;

    private String clientIdGenerator;
    private String clientIdFormat;

    // =========================================================================
    // RUN RESULT
    // =========================================================================

    private ImportResult result;

    // =========================================================================
    // CACHE
    // =========================================================================

    private List<String> countryCodes = new ArrayList<>();

    private Map<String, List<String>> organizationCache = new HashMap<>();
    private Map<String, ClientImportRow> companyIndex = new HashMap<>();

    private Map<String, List<String>> individualNameCache = new HashMap<>();
    private Map<String, Set<String>> individualPhoneCache = new HashMap<>();
    private Map<String, Set<String>> individualEmailCache = new HashMap<>();

    private Map<String, String> countryAliases = new HashMap<>();
    private Map<String, List<String>> stateAliases = new HashMap<>();

    private Map<String, String> countryByName = new HashMap<>();
    private Map<String, List<String>> stateByKey = new HashMap<>();

    private Set<Integer> handledRows = new HashSet<>();
    private Set<Integer> processingRows = new HashSet<>();

    private Set<String> successfulFingerprints = new HashSet<>();
    private Map<String, FingerprintInfo> successfulFingerprintInfo = new HashMap<>();
    private final ClientIntegrationProcessor integrationProcessor;

    public ClientImportProcessor(Map properties, ImportOptions options) {

        this.properties = properties;
        this.options = options;

        this.formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        this.transactionTemplate = (TransactionTemplate) AppUtil.getApplicationContext().getBean("transactionTemplate");

        this.clientIdPrefix = JogetFormUtil.property(properties, "clientIdPrefix", "TEST");
        this.clientIdPad = intProperty(properties, "clientIdPad", 4);
        this.clientIdGenerator = JogetFormUtil.property(properties, "clientIdGenerator", "client");
        this.clientIdFormat = JogetFormUtil.property(properties, "clientIdFormat", "CL?");

        this.defaultPhoneType = JogetFormUtil.property(properties, "defaultPhoneType", "Other");
        this.defaultEmailType = JogetFormUtil.property(properties, "defaultEmailType", "Other");
        this.integrationProcessor = new ClientIntegrationProcessor(properties);
    }

    // =========================================================================
    // MAIN PROCESS
    // =========================================================================

    public ImportResult process(List<ClientImportRow> rows) throws Exception {

        result = new ImportResult();

        result.setRows(rows);
        result.setSourceRows(rows.size());
        result.setRunId(createRunId());

        DebugLogger.info(getClass().getName(), "Preparing LMS import caches");

        loadCaches(rows);

        DebugLogger.info(getClass().getName(),
                "Caches ready | countryCodes=" + countryCodes.size() +
                        " organizations=" + organizationCache.size() +
                        " companyIndex=" + companyIndex.size() +
                        " individualNames=" + individualNameCache.size()
        );

        int totalRows = rows.size();

        for (int i = 0; i < totalRows; i++) {

            ClientImportRow row = rows.get(i);

            if (!handledRows.contains(row.getRowNumber())) {
                processSourceRow(row);
            }

            updateProgressIfNeeded(i + 1, totalRows, row);
        }

        finishResult();

        JogetFormUtil.updateProgress(
                properties, result.getStatus(), totalRows, totalRows,
                result.getRowsSucceeded(), result.getRowsFailed(),
                result.getClientsMatched() + result.getSourceDuplicates(),
                "Client import processing completed"
        );

        return result;
    }

    private void processSourceRow(ClientImportRow row) {

        if (successfulFingerprints.contains(row.getFingerprint())) {
            markSourceDuplicate(row);
            return;
        }

        try {

            if (row.isCompany()) {
                processCompanyRow(row);
                return;
            }

            if (row.isPerson()) {
                processPersonRow(row);
                return;
            }

            failRow(
                    row,
                    "UNSUPPORTED_TYPE",
                    "Type",
                    row.getType(),
                    "Unsupported source record type: " + row.getType(),
                    "Correct the Type to Person or Company and re-import."
            );

        } catch (Exception e) {

            if (!row.isFailed()) {

                failRow(
                        row,
                        "ROW_FAILED",
                        row.isCompany() ? "Organization" : "Individual",
                        row.getClientDisplayName(),
                        rootMessage(e),
                        "Correct the source value or configuration and re-import this row."
                );
            }
        }
    }

    // =========================================================================
    // COMPANY PROCESS
    // =========================================================================

    private String processCompanyRow(ClientImportRow row) throws Exception {

        int rowNumber = row.getRowNumber();

        if (handledRows.contains(rowNumber)) return row.getClientId();

        if (processingRows.contains(rowNumber)) {
            throw new Exception("Recursive Company row processing detected at Excel row " + rowNumber);
        }

        if (successfulFingerprints.contains(row.getFingerprint())) {
            markSourceDuplicate(row);
            return "";
        }

        processingRows.add(rowNumber);

        final ClientImportRow currentRow = row;

        try {

            String clientId = transactionTemplate.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {
                        return importCompany(currentRow);
                    } catch (Exception e) {
                        throw new ImportRuntimeException(e);
                    }
                }
            });

            clientId = clientId == null ? "" : clientId;

            if (!clientId.isEmpty()) {
                boolean alreadyHadWarnings = row.hasIssues();
                integrationProcessor.process(row, clientId, result);

                if (!alreadyHadWarnings && row.hasIssues()) {
                    result.incrementRowsWithWarnings();
                }
            }

            return clientId;

        } catch (ImportRuntimeException e) {

            failRow(
                    row,
                    "ROW_FAILED",
                    "Organization",
                    row.getOrganizationName(),
                    rootMessage(e),
                    "Correct the source value or configuration and re-import this row."
            );

            throw unwrap(e);

        } catch (RuntimeException e) {

            failRow(
                    row,
                    "ROW_FAILED",
                    "Organization",
                    row.getOrganizationName(),
                    rootMessage(e),
                    "Correct the source value or configuration and re-import this row."
            );

            throw e;

        } finally {
            processingRows.remove(rowNumber);
        }
    }

    private String importCompany(ClientImportRow row) throws Exception {

        String organizationName = ImportUtil.collapseSpaces(row.getOrganizationName());

        validateOrganizationName(organizationName);

        normalizeRowChildren(row);

        String organizationKey = ImportUtil.normalizeOrganizationName(organizationName);

        if (organizationKey.isEmpty()) {
            organizationKey = ImportUtil.lower(organizationName);
        }

        String clientId = uniqueOrganizationId(organizationKey);
        boolean created = false;

        ClientSnapshot before = null;

        if (clientId.isEmpty()) {

            clientId = createClient("O", organizationName, "", "", "");
            created = true;

        } else {
            before = safeClientSnapshot(clientId);
        }

        row.setClientId(clientId);

        if (created || options.isEnrichExistingClients()) {

            upsertPhones(clientId, row.getPhones());
            upsertEmails(clientId, row.getEmails());
            upsertAddresses(row, clientId, row.getAddresses());
            upsertSocial(row, clientId);

            ensurePrimaries(clientId);
        }

        if (created) {

            addMulti(organizationCache, organizationKey, clientId);

        } else {

            ClientSnapshot after = safeClientSnapshot(clientId);

            addDuplicateMerge(
                    row,
                    clientId,
                    "NORMALIZED_ORGANIZATION_NAME",
                    "",
                    before,
                    after
            );
        }

        markSuccessfulRow(row, clientId, created, true);

        return clientId;
    }

    // =========================================================================
    // PERSON PROCESS
    // =========================================================================

    private String processPersonRow(ClientImportRow row) throws Exception {

        if (handledRows.contains(row.getRowNumber())) {
            return row.getClientId();
        }

        if (successfulFingerprints.contains(row.getFingerprint())) {
            markSourceDuplicate(row);
            return "";
        }

        /*
         * Keep organization resolution outside the Individual transaction.
         *
         * This matches the BeanShell behavior.
         * A referenced Company row can be created successfully even if
         * the Individual itself later fails.
         */
        String organizationId = resolveOrganizationForPerson(row);
        row.setOrganizationId(organizationId);

        final ClientImportRow currentRow = row;

        try {

            String clientId = transactionTemplate.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {
                        return importPerson(currentRow);
                    } catch (Exception e) {
                        throw new ImportRuntimeException(e);
                    }
                }
            });

            clientId = clientId == null ? "" : clientId;

            if (!clientId.isEmpty()) {
                boolean alreadyHadWarnings = row.hasIssues();
                integrationProcessor.process(row, clientId, result);

                if (!alreadyHadWarnings && row.hasIssues()) {
                    result.incrementRowsWithWarnings();
                }
            }

            return clientId;

        } catch (ImportRuntimeException e) {

            failRow(
                    row,
                    "ROW_FAILED",
                    "Individual",
                    row.getClientDisplayName(),
                    rootMessage(e),
                    "Correct the mandatory source data or duplicate ambiguity and re-import this row."
            );

            throw unwrap(e);

        } catch (RuntimeException e) {

            failRow(
                    row,
                    "ROW_FAILED",
                    "Individual",
                    row.getClientDisplayName(),
                    rootMessage(e),
                    "Correct the mandatory source data or duplicate ambiguity and re-import this row."
            );

            throw e;
        }
    }

    private String importPerson(ClientImportRow row) throws Exception {

        String firstName = ImportUtil.collapseSpaces(row.getFirstName());
        String middleName = ImportUtil.collapseSpaces(row.getMiddleName());
        String lastName = ImportUtil.collapseSpaces(row.getLastName());

        validateIndividualName(firstName, lastName);

        normalizeRowChildren(row);

        String clientId = findDuplicateIndividual(
                firstName,
                lastName,
                row.getPhones(),
                row.getEmails()
        );

        boolean created = false;
        String matchBasis = "";
        ClientSnapshot before = null;

        if (clientId.isEmpty()) {

            String clientName = ImportUtil.buildIndividualClientName(
                    firstName,
                    middleName,
                    lastName
            );

            clientId = createClient(
                    "I",
                    clientName,
                    firstName,
                    middleName,
                    lastName
            );

            created = true;

        } else {

            matchBasis = individualMatchBasis(
                    clientId,
                    row.getPhones(),
                    row.getEmails()
            );

            before = safeClientSnapshot(clientId);
        }

        row.setClientId(clientId);

        syncIndividualClientName(
                clientId,
                firstName,
                middleName,
                lastName
        );

        if (created || options.isEnrichExistingClients()) {

            upsertPhones(clientId, row.getPhones());
            upsertEmails(clientId, row.getEmails());
            upsertAddresses(row, clientId, row.getAddresses());
            upsertSocial(row, clientId);

            if (!ImportUtil.isEmpty(row.getOrganizationId())) {
                ensureClientMap(clientId, row.getOrganizationId());
            }

            ensurePrimaries(clientId);
        }

        if (!created) {

            ClientSnapshot after = safeClientSnapshot(clientId);

            addDuplicateMerge(
                    row,
                    clientId,
                    matchBasis,
                    "",
                    before,
                    after
            );
        }

        cacheIndividualResult(
                clientId,
                firstName,
                lastName,
                row.getPhones(),
                row.getEmails()
        );

        markSuccessfulRow(row, clientId, created, false);

        return clientId;
    }

    // =========================================================================
    // NAME VALIDATION
    // =========================================================================

    private void validateOrganizationName(String organizationName) throws Exception {

        if (!ImportUtil.isEmpty(organizationName)) return;

        if (options.isAddInvalidClientNames()) return;

        throw new Exception("Organization Name is required.");
    }

    private void validateIndividualName(String firstName, String lastName) throws Exception {

        if (options.isAddInvalidClientNames()) return;

        if (ImportUtil.isEmpty(firstName)) {
            throw new Exception("First Name is required.");
        }

        if (ImportUtil.isEmpty(lastName)) {
            throw new Exception("Last Name is required.");
        }
    }

    // =========================================================================
    // CHILD NORMALIZATION
    // =========================================================================

    private void normalizeRowChildren(ClientImportRow row) {

        row.setPhones(normalizePhones(row, row.getPhones()));
        row.setEmails(normalizeEmails(row, row.getEmails()));

        resolveAddresses(row, row.getAddresses());
    }

    // =========================================================================
    // EMAIL NORMALIZATION
    // =========================================================================

    private List<ImportEmail> normalizeEmails(ClientImportRow row, List<ImportEmail> rawEmails) {

        List<ImportEmail> emails = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        boolean primaryAssigned = false;

        for (ImportEmail rawEmail : rawEmails) {

            String email = ImportUtil.normalizeEmail(rawEmail.getEmail());

            if (ImportUtil.isEmpty(email)) continue;

            boolean valid = ImportUtil.validEmail(email);

            if (!valid) {

                addIssue(
                        row,
                        "LOW",
                        "EMAIL_INVALID",
                        rawEmail.getSourceField(),
                        rawEmail.getSourceValue(),
                        "Email was not imported because it is not a valid email address.",
                        "Review and correct the email manually if needed."
                );

                if (!options.isAddInvalidEmails()) continue;
            }

            if (seen.contains(email)) continue;

            seen.add(email);

            ImportEmail item = new ImportEmail();

            item.setType(
                    ImportUtil.isEmpty(rawEmail.getType())
                            ? defaultEmailType
                            : rawEmail.getType()
            );

            item.setEmail(email);
            item.setPrimary(!primaryAssigned);

            item.setSourceField(rawEmail.getSourceField());
            item.setSourceValue(rawEmail.getSourceValue());

            primaryAssigned = true;

            emails.add(item);
        }

        return emails;
    }

    // =========================================================================
    // PHONE NORMALIZATION
    // =========================================================================

    private List<ImportPhone> normalizePhones(ClientImportRow row, List<ImportPhone> rawPhones) {

        Map<String, ImportPhone> dedupe = new LinkedHashMap<>();
        Set<String> primaryKeys = new HashSet<>();

        for (ImportPhone rawPhone : rawPhones) {

            List<String> values = ImportUtil.splitPhoneValues(rawPhone.getPhone());

            for (String rawValue : values) {

                ImportPhone parsed = ImportUtil.parsePhone(rawValue, countryCodes);

                if (parsed == null) {

                    boolean primaryField = "Phone Number Primary".equalsIgnoreCase(
                            rawPhone.getSourceField()
                    );

                    String code = primaryField
                            ? "PRIMARY_PHONE_UNRESOLVED"
                            : "PHONE_UNRESOLVED";

                    String message = primaryField
                            ? "Primary phone was not imported because it could not be resolved."
                            : "Phone was not imported because its country code/number could not be resolved.";

                    addIssue(
                            row,
                            "LOW",
                            code,
                            rawPhone.getSourceField(),
                            rawValue,
                            message,
                            "Review the phone manually if needed."
                    );

                    if (!options.isAddInvalidPhoneNumbers()) continue;

                    parsed = new ImportPhone();

                    parsed.setCountryCode("");
                    parsed.setPhone(rawValue);
                    parsed.setExtension("");
                }

                parsed.setType(
                        ImportUtil.isEmpty(rawPhone.getType())
                                ? defaultPhoneType
                                : rawPhone.getType()
                );

                parsed.setMobile(
                        ImportUtil.isEmpty(rawPhone.getMobile())
                                ? "No"
                                : rawPhone.getMobile()
                );

                parsed.setSourceField(rawPhone.getSourceField());
                parsed.setSourceValue(rawValue);

                String key = parsed.getUniqueKey();

                ImportPhone existing = dedupe.get(key);

                if (existing == null) {

                    parsed.setPrimary(false);
                    dedupe.put(key, parsed);

                } else {

                    if ("Other".equalsIgnoreCase(existing.getType()) &&
                            !"Other".equalsIgnoreCase(parsed.getType())) {

                        existing.setType(parsed.getType());
                    }

                    if ("Yes".equalsIgnoreCase(parsed.getMobile())) {

                        existing.setMobile("Yes");
                        existing.setType(parsed.getType());
                    }

                    if (ImportUtil.isEmpty(existing.getExtension()) &&
                            !ImportUtil.isEmpty(parsed.getExtension())) {

                        existing.setExtension(parsed.getExtension());
                    }
                }

                if ("Phone Number Primary".equalsIgnoreCase(rawPhone.getSourceField())) {
                    primaryKeys.add(key);
                }
            }
        }

        List<ImportPhone> phones = new ArrayList<>(dedupe.values());

        boolean primaryAssigned = false;

        for (ImportPhone phone : phones) {

            String key = phone.getUniqueKey();

            if (!primaryAssigned && primaryKeys.contains(key)) {

                phone.setPrimary(true);
                primaryAssigned = true;

            } else {
                phone.setPrimary(false);
            }
        }

        if (!primaryAssigned && !phones.isEmpty()) {
            phones.get(0).setPrimary(true);
        }

        return phones;
    }

    // =========================================================================
    // ADDRESS RESOLUTION
    // =========================================================================

    private void resolveAddresses(ClientImportRow row, List<ImportAddress> addresses) {

        for (ImportAddress address : addresses) {

            String countryHint = quickCountryIso(address.getRawCountry());
            String zip = ImportUtil.value(address.getZip());

            if (countryHint.isEmpty()) {

                if (zip.matches("^[0-9]{5}(?:-[0-9]{4})?$")) {
                    countryHint = "US";
                } else if (zip.matches("(?i)^[A-Z][0-9][A-Z]\\s?[0-9][A-Z][0-9]$")) {
                    countryHint = "CA";
                }
            }

            String state = resolveStateCode(row, address, countryHint);
            String country = resolveCountryIso(row, address, state, countryHint);

            address.setCity(
                    ImportUtil.collapseSpaces(address.getRawCity())
            );

            address.setState(state);
            address.setCountry(country);

            boolean unresolved = state.isEmpty() || country.isEmpty();

            preserveAddressText(address, unresolved);
        }
    }

    private String quickCountryIso(String raw) {

        String value = ImportUtil.collapseSpaces(raw);

        if (value.isEmpty()) return "";

        String upper = ImportUtil.upper(value);

        if (upper.matches("^[A-Z]{2}$")) return upper;

        String alias = countryAliases.get(value.toLowerCase());

        if (alias != null) return alias;

        String country = countryByName.get(value.toLowerCase());

        return country == null ? "" : country;
    }

    private String resolveStateCode(ClientImportRow row, ImportAddress address, String countryHint) {

        String raw = ImportUtil.collapseSpaces(address.getRawState());
        String field = address.getSourcePrefix() + " State";

        if (raw.isEmpty()) {

            addIssue(
                    row,
                    "MEDIUM",
                    "ADDRESS_STATE_MISSING",
                    field,
                    "",
                    "State is required by LMS but source did not provide one.",
                    "Review the imported address and select the correct state."
            );

            return "";
        }

        String upper = ImportUtil.upper(raw);

        if (upper.matches("^[A-Z]{2}-[A-Z0-9]{1,4}$")) {
            return upper;
        }

        List<String> aliases = stateAliases.get(raw.toLowerCase());

        if (aliases != null && !aliases.isEmpty()) {

            if (!countryHint.isEmpty()) {

                for (String code : aliases) {
                    if (code.startsWith(countryHint + "-")) return code;
                }
            }

            if (aliases.size() == 1) {
                return aliases.get(0);
            }

            addIssue(
                    row,
                    "MEDIUM",
                    "ADDRESS_STATE_AMBIGUOUS",
                    field,
                    raw,
                    "More than one ISO state code matches this abbreviation/name: " + aliases,
                    "Review the address and select the correct state code."
            );

            return "";
        }

        List<String> candidates = stateByKey.get(raw.toLowerCase());

        if (candidates != null && !candidates.isEmpty()) {

            if (!countryHint.isEmpty()) {

                for (String code : candidates) {
                    if (code.startsWith(countryHint + "-")) return code;
                }
            }

            if (candidates.size() == 1) {
                return candidates.get(0);
            }

            addIssue(
                    row,
                    "MEDIUM",
                    "ADDRESS_STATE_AMBIGUOUS",
                    field,
                    raw,
                    "More than one ISO state code matches this value: " + candidates,
                    "Review the address and select the correct state code."
            );

            return "";
        }

        if (upper.matches("^[A-Z0-9]{1,4}$") && !countryHint.isEmpty()) {
            return countryHint + "-" + upper;
        }

        addIssue(
                row,
                "MEDIUM",
                "ADDRESS_STATE_UNRESOLVED",
                field,
                raw,
                "State could not be resolved to an ISO 3166-2 code.",
                "Review the address and select the correct state."
        );

        return "";
    }

    private String resolveCountryIso(ClientImportRow row, ImportAddress address,
                                     String stateCode, String fallbackHint) {

        String raw = ImportUtil.collapseSpaces(address.getRawCountry());
        String field = address.getSourcePrefix() + " Country";

        if (!stateCode.isEmpty() && stateCode.contains("-")) {
            return stateCode.substring(0, stateCode.indexOf('-'));
        }

        String iso = quickCountryIso(raw);

        if (!iso.isEmpty()) return iso;
        if (!fallbackHint.isEmpty()) return fallbackHint;

        if (raw.isEmpty()) {

            addIssue(
                    row,
                    "MEDIUM",
                    "ADDRESS_COUNTRY_MISSING",
                    field,
                    "",
                    "Country is required by LMS and could not be inferred from the state.",
                    "Review the imported address and select the country."
            );

        } else {

            addIssue(
                    row,
                    "MEDIUM",
                    "ADDRESS_COUNTRY_UNRESOLVED",
                    field,
                    raw,
                    "Country could not be resolved to an ISO 3166-1 Alpha-2 code.",
                    "Review the imported address and select the correct country."
            );
        }

        return "";
    }

    private void preserveAddressText(ImportAddress address, boolean unresolved) {

        String line1 = ImportUtil.value(address.getLine1());
        String line2 = ImportUtil.value(address.getLine2());

        String location = ImportUtil.joinAddressParts(
                address.getRawCity(),
                address.getRawState(),
                address.getRawCountry(),
                address.getZip()
        );

        String full = ImportUtil.joinAddressParts(
                line1,
                line2,
                location
        );

        if (line1.isEmpty() && !full.isEmpty()) {
            line1 = full;
        }

        if (unresolved && !location.isEmpty()) {

            if (line2.isEmpty()) {

                line2 = location;

            } else if (!line2.toLowerCase().contains(location.toLowerCase())) {

                line2 = line2 + " | " + location;
            }
        }

        address.setLine1(line1);
        address.setLine2(line2);
    }

    // =========================================================================
    // ORGANIZATION RESOLUTION FOR PERSON
    // =========================================================================

    private String resolveOrganizationForPerson(ClientImportRow row) {

        final String company = ImportUtil.collapseSpaces(row.getOrganizationName());

        if (company.isEmpty()) return "";

        String normalizedKey = ImportUtil.normalizeOrganizationName(company);
        final String key = normalizedKey.isEmpty()
                ? ImportUtil.lower(company)
                : normalizedKey;

        try {

            String existing = uniqueOrganizationId(key);

            if (!existing.isEmpty()) {
                return existing;
            }

        } catch (Exception e) {

            addIssue(
                    row,
                    "MEDIUM",
                    "ORGANIZATION_AMBIGUOUS",
                    "Company Name",
                    company,
                    e.getMessage(),
                    "Review the organization and link the individual manually."
            );

            return "";
        }

        ClientImportRow companyRow = companyIndex.get(key);

        if (companyRow != null) {

            try {

                String id = processCompanyRow(companyRow);

                if (!id.isEmpty()) return id;

                return uniqueOrganizationId(key);

            } catch (Exception e) {

                addIssue(
                        row,
                        "MEDIUM",
                        "ASSOCIATED_ORGANIZATION_FAILED",
                        "Company Name",
                        company,
                        "Associated source Company row failed: " + rootMessage(e),
                        "Review the Company row and link this individual after correction."
                );

                return "";
            }
        }

        if (options.isCreateOrganizationFromPersonOnly()) {

            try {

                return transactionTemplate.execute(new TransactionCallback<String>() {

                    @Override
                    public String doInTransaction(TransactionStatus status) {

                        try {

                            validateOrganizationName(company);

                            String id = createClient(
                                    "O",
                                    company,
                                    "",
                                    "",
                                    ""
                            );

                            addMulti(organizationCache, key, id);

                            result.incrementClientsCreated();
                            result.incrementOrganizationsCreated();

                            return id;

                        } catch (Exception e) {
                            throw new ImportRuntimeException(e);
                        }
                    }
                });

            } catch (Exception e) {

                addIssue(
                        row,
                        "MEDIUM",
                        "ORGANIZATION_CREATE_FAILED",
                        "Company Name",
                        company,
                        rootMessage(e),
                        "Review and create/link the organization manually."
                );

                return "";
            }
        }

        addIssue(
                row,
                "MEDIUM",
                "ORGANIZATION_UNRESOLVED",
                "Company Name",
                company,
                "No existing Organization or explicit source Company row matched this value.",
                "Review the Company Name and link/create the organization manually if appropriate."
        );

        return "";
    }

    // =========================================================================
    // INDIVIDUAL DUPLICATE
    // =========================================================================

    private String findDuplicateIndividual(String firstName, String lastName,
                                           List<ImportPhone> phones,
                                           List<ImportEmail> emails) throws Exception {

        Set<String> incomingPhones = new HashSet<>();
        Set<String> incomingEmails = new HashSet<>();

        for (ImportPhone phone : phones) {

            String key = normalizedPhoneKey(phone);

            if (!key.isEmpty()) {
                incomingPhones.add(key);
            }
        }

        for (ImportEmail email : emails) {

            String value = ImportUtil.normalizeEmail(email.getEmail());

            if (!value.isEmpty()) {
                incomingEmails.add(value);
            }
        }

        /*
         * IMPORTANT BUSINESS RULE
         *
         * If no valid phone and no valid email exist,
         * do NOT match using only First Name + Last Name.
         */
        if (incomingPhones.isEmpty() && incomingEmails.isEmpty()) {
            return "";
        }

        String nameKey = ImportUtil.individualNameKey(firstName, lastName);
        List<String> ids = individualNameCache.get(nameKey);

        if (ids == null || ids.isEmpty()) {
            return "";
        }

        List<String> matched = new ArrayList<>();

        for (String clientId : ids) {

            boolean overlap = false;

            Set<String> dbPhones = individualPhoneCache.get(clientId);
            Set<String> dbEmails = individualEmailCache.get(clientId);

            if (dbPhones != null) {

                for (String incoming : incomingPhones) {

                    if (dbPhones.contains(incoming)) {
                        overlap = true;
                        break;
                    }
                }
            }

            if (!overlap && dbEmails != null) {

                for (String incoming : incomingEmails) {

                    if (dbEmails.contains(incoming)) {
                        overlap = true;
                        break;
                    }
                }
            }

            if (overlap) {
                matched.add(clientId);
            }
        }

        if (matched.size() > 1) {

            throw new Exception(
                    "Ambiguous Individual duplicate for " +
                            firstName + " " + lastName +
                            ". Multiple existing LMS clients match phone/email: " +
                            matched
            );
        }

        return matched.size() == 1
                ? matched.get(0)
                : "";
    }

    private String individualMatchBasis(String clientId,
                                        List<ImportPhone> phones,
                                        List<ImportEmail> emails) {

        boolean phoneMatch = false;
        boolean emailMatch = false;

        Set<String> dbPhones = individualPhoneCache.get(clientId);
        Set<String> dbEmails = individualEmailCache.get(clientId);

        if (dbPhones != null) {

            for (ImportPhone phone : phones) {

                String key = normalizedPhoneKey(phone);

                if (!key.isEmpty() && dbPhones.contains(key)) {
                    phoneMatch = true;
                    break;
                }
            }
        }

        if (dbEmails != null) {

            for (ImportEmail email : emails) {

                String value = ImportUtil.normalizeEmail(email.getEmail());

                if (!value.isEmpty() && dbEmails.contains(value)) {
                    emailMatch = true;
                    break;
                }
            }
        }

        if (phoneMatch && emailMatch) {
            return "FIRST_LAST_PHONE_EMAIL";
        }

        if (phoneMatch) {
            return "FIRST_LAST_PHONE";
        }

        if (emailMatch) {
            return "FIRST_LAST_EMAIL";
        }

        return "FIRST_LAST_PHONE_OR_EMAIL";
    }

    // =========================================================================
    // CLIENT CREATE
    // =========================================================================

    private String createClient(String clientType, String clientName,
                                String firstName, String middleName,
                                String lastName) {

        String id = nextClientId();

        FormRow row = JogetFormUtil.createRow(id);

        row.setProperty("client_type", clientType);
        row.setProperty("client_name", clientName);

        row.setProperty("first_name", firstName);
        row.setProperty("middle_name", middleName);
        row.setProperty("last_name", lastName);

        row.setProperty("payment_terms", "NET_30");
        row.setProperty("deactivate_client", "No");
        row.setProperty("qb_auto_sync", "false");

        row.setProperty("name_id", clientName + "_" + id);
        row.setProperty("btn_ctr", "O".equals(clientType) ? "1" : "");

        JogetFormUtil.save(
                CLIENT_FORM,
                CLIENT_TABLE,
                row
        );

        return id;
    }

    private void syncIndividualClientName(String clientId, String firstName,
                                          String middleName, String lastName) {

        String fullName = ImportUtil.buildIndividualClientName(
                firstName,
                middleName,
                lastName
        );

        if (fullName.isEmpty()) return;

        FormRow row = formDataDao.load(
                CLIENT_FORM,
                CLIENT_TABLE,
                clientId
        );

        if (row == null) return;

        if (!"I".equalsIgnoreCase(row.getProperty("client_type"))) {
            return;
        }

        row.setProperty("client_name", fullName);
        row.setProperty("name_id", fullName + "_" + clientId);

        updateExisting(
                row,
                CLIENT_FORM,
                CLIENT_TABLE
        );
    }

    // =========================================================================
    // PHONE UPSERT
    // =========================================================================

    private void upsertPhones(String clientId, List<ImportPhone> phones) {

        boolean primaryExists = hasPrimaryPhone(clientId);

        for (ImportPhone phone : phones) {

            if (ImportUtil.isEmpty(phone.getPhone())) {
                continue;
            }

            boolean wantsPrimary = phone.isPrimary() && !primaryExists;

            FormRow existing = findPhoneForClient(
                    clientId,
                    phone
            );

            if (existing != null) {

                boolean changed = false;

                if (wantsPrimary &&
                        !"Primary".equals(existing.getProperty("contact_priority"))) {

                    existing.setProperty(
                            "contact_priority",
                            "Primary"
                    );

                    primaryExists = true;
                    changed = true;
                }

                if (ImportUtil.isEmpty(existing.getProperty("business_extension")) &&
                        !ImportUtil.isEmpty(phone.getExtension())) {

                    existing.setProperty(
                            "business_extension",
                            phone.getExtension()
                    );

                    changed = true;
                }

                if (changed) {

                    updateExisting(
                            existing,
                            PHONE_FORM,
                            PHONE_TABLE
                    );
                }

                result.incrementPhonesSkippedExisting();
                continue;
            }

            String id = ImportUtil.uuid();

            FormRow row = JogetFormUtil.createRow(id);

            row.setProperty("fk", clientId);
            row.setProperty("contact_type", phone.getType());

            row.setProperty(
                    "country_code",
                    phone.getCountryCode()
            );

            row.setProperty(
                    "phone_no",
                    phone.getPhone()
            );

            row.setProperty(
                    "business_extension",
                    phone.getExtension()
            );

            row.setProperty(
                    "is_mobile",
                    phone.getMobile()
            );

            row.setProperty(
                    "contact_priority",
                    wantsPrimary ? "Primary" : "No"
            );

            row.setProperty("row_uuid", id);

            JogetFormUtil.save(
                    PHONE_FORM,
                    PHONE_TABLE,
                    row
            );

            if (wantsPrimary) {
                primaryExists = true;
            }

            result.incrementPhonesCreated();
        }
    }

    private FormRow findPhoneForClient(String clientId, ImportPhone phone) {

        FormRowSet rows = findByField(
                PHONE_FORM,
                PHONE_TABLE,
                "fk",
                clientId
        );

        String incomingKey = normalizedPhoneKey(phone);

        for (FormRow row : rows) {

            String dbKey = normalizedPhoneKey(
                    row.getProperty("country_code"),
                    row.getProperty("phone_no")
            );

            if (incomingKey.equals(dbKey)) {
                return row;
            }
        }

        return null;
    }

    private boolean hasPrimaryPhone(String clientId) {

        return findOneByTwoFields(
                PHONE_FORM,
                PHONE_TABLE,
                "fk",
                clientId,
                "contact_priority",
                "Primary"
        ) != null;
    }

    // =========================================================================
    // EMAIL UPSERT
    // =========================================================================

    private void upsertEmails(String clientId, List<ImportEmail> emails) {

        boolean primaryExists = hasPrimaryEmail(clientId);

        for (ImportEmail email : emails) {

            String normalized = ImportUtil.normalizeEmail(
                    email.getEmail()
            );

            if (normalized.isEmpty()) continue;

            boolean wantsPrimary = email.isPrimary() && !primaryExists;

            FormRow existing = findEmailForClient(
                    clientId,
                    normalized
            );

            if (existing != null) {

                if (wantsPrimary &&
                        !"Primary".equals(existing.getProperty("email_priority"))) {

                    existing.setProperty(
                            "email_priority",
                            "Primary"
                    );

                    updateExisting(
                            existing,
                            EMAIL_FORM,
                            EMAIL_TABLE
                    );

                    primaryExists = true;
                }

                result.incrementEmailsSkippedExisting();
                continue;
            }

            String id = ImportUtil.uuid();

            FormRow row = JogetFormUtil.createRow(id);

            row.setProperty("parent_id", clientId);
            row.setProperty("email_type", email.getType());
            row.setProperty("email_id", normalized);

            row.setProperty(
                    "email_priority",
                    wantsPrimary ? "Primary" : "---"
            );

            row.setProperty("row_uuid", id);

            JogetFormUtil.save(
                    EMAIL_FORM,
                    EMAIL_TABLE,
                    row
            );

            if (wantsPrimary) {
                primaryExists = true;
            }

            result.incrementEmailsCreated();
        }
    }

    private FormRow findEmailForClient(String clientId, String email) {

        FormRowSet rows = findByField(
                EMAIL_FORM,
                EMAIL_TABLE,
                "parent_id",
                clientId
        );

        for (FormRow row : rows) {

            String existing = ImportUtil.normalizeEmail(
                    row.getProperty("email_id")
            );

            if (email.equals(existing)) {
                return row;
            }
        }

        return null;
    }

    private boolean hasPrimaryEmail(String clientId) {

        return findOneByTwoFields(
                EMAIL_FORM,
                EMAIL_TABLE,
                "parent_id",
                clientId,
                "email_priority",
                "Primary"
        ) != null;
    }

    // =========================================================================
    // ADDRESS UPSERT
    // =========================================================================

    private void upsertAddresses(ClientImportRow sourceRow, String clientId,
                                 List<ImportAddress> addresses) {

        boolean primaryExists = hasPrimaryAddress(clientId);

        for (ImportAddress address : addresses) {

            boolean unresolved =
                    ImportUtil.isEmpty(address.getState()) ||
                            ImportUtil.isEmpty(address.getCountry());

            if (unresolved && !options.isAddInvalidAddresses()) {
                continue;
            }

            boolean wantsPrimary =
                    address.isPrimary() &&
                            !primaryExists;

            FormRow existing = findAddressForClient(
                    clientId,
                    address
            );

            if (existing != null) {

                String line1 =
                        ImportUtil.isEmpty(existing.getProperty("address_line_1"))
                                ? address.getLine1()
                                : existing.getProperty("address_line_1");

                String line2 =
                        ImportUtil.isEmpty(existing.getProperty("address_line_2"))
                                ? address.getLine2()
                                : existing.getProperty("address_line_2");

                String city =
                        ImportUtil.isEmpty(address.getCity())
                                ? ImportUtil.value(existing.getProperty("city"))
                                : address.getCity();

                String state =
                        ImportUtil.isEmpty(address.getState())
                                ? ImportUtil.value(existing.getProperty("state"))
                                : address.getState();

                String country =
                        ImportUtil.isEmpty(address.getCountry())
                                ? ImportUtil.value(existing.getProperty("country"))
                                : address.getCountry();

                String priority = ImportUtil.value(
                        existing.getProperty("address_priority")
                );

                if (wantsPrimary &&
                        !"Primary".equals(priority)) {

                    priority = "Primary";
                }

                if (priority.isEmpty()) {
                    priority = "No";
                }

                existing.setProperty(
                        "address_line_1",
                        line1
                );

                existing.setProperty(
                        "address_line_2",
                        line2
                );

                existing.setProperty(
                        "city",
                        city
                );

                existing.setProperty(
                        "state",
                        state
                );

                existing.setProperty(
                        "country",
                        country
                );

                existing.setProperty(
                        "address_priority",
                        priority
                );

                updateExisting(
                        existing,
                        ADDRESS_FORM,
                        ADDRESS_TABLE
                );

                if ("Primary".equals(priority)) {
                    primaryExists = true;
                }

                result.incrementAddressesSkippedExisting();

                continue;
            }

            String id = ImportUtil.uuid();

            FormRow row = JogetFormUtil.createRow(id);

            row.setProperty("fk", clientId);
            row.setProperty("address_type", address.getType());

            row.setProperty(
                    "address_line_1",
                    address.getLine1()
            );

            row.setProperty(
                    "address_line_2",
                    address.getLine2()
            );

            row.setProperty(
                    "city",
                    address.getCity()
            );

            row.setProperty(
                    "state",
                    address.getState()
            );

            row.setProperty(
                    "zipcode",
                    address.getZip()
            );

            row.setProperty(
                    "country",
                    address.getCountry()
            );

            row.setProperty(
                    "address_priority",
                    wantsPrimary ? "Primary" : "No"
            );

            row.setProperty("row_uuid", id);

            JogetFormUtil.save(
                    ADDRESS_FORM,
                    ADDRESS_TABLE,
                    row
            );

            if (wantsPrimary) {
                primaryExists = true;
            }

            result.incrementAddressesCreated();
        }
    }

    private FormRow findAddressForClient(String clientId, ImportAddress address) {

        FormRowSet rows = findByField(
                ADDRESS_FORM,
                ADDRESS_TABLE,
                "fk",
                clientId
        );

        List<FormRow> fallbackMatches = new ArrayList<>();

        for (FormRow row : rows) {

            boolean sameType = equalsIgnoreCase(
                    row.getProperty("address_type"),
                    address.getType()
            );

            boolean sameLine1 = equalsIgnoreCase(
                    row.getProperty("address_line_1"),
                    address.getLine1()
            );

            boolean sameLine2 = equalsIgnoreCase(
                    row.getProperty("address_line_2"),
                    address.getLine2()
            );

            boolean sameZip = equalsIgnoreCase(
                    row.getProperty("zipcode"),
                    address.getZip()
            );

            if (sameType &&
                    sameLine1 &&
                    sameLine2 &&
                    sameZip) {

                return row;
            }

            if (!ImportUtil.isEmpty(address.getLine1()) &&
                    sameType &&
                    sameLine1 &&
                    sameZip) {

                fallbackMatches.add(row);
            }
        }

        return fallbackMatches.size() == 1
                ? fallbackMatches.get(0)
                : null;
    }

    private boolean hasPrimaryAddress(String clientId) {

        return findOneByTwoFields(
                ADDRESS_FORM,
                ADDRESS_TABLE,
                "fk",
                clientId,
                "address_priority",
                "Primary"
        ) != null;
    }

    // =========================================================================
    // SOCIAL
    // =========================================================================

    private void upsertSocial(ClientImportRow sourceRow, String clientId) {

        String raw = sourceRow.getWebsite();

        if (ImportUtil.isEmpty(raw)) return;

        String url = ImportUtil.normalizeUrl(raw);
        String platform = ImportUtil.detectSocialPlatform(url);

        if (platform.isEmpty()) {

            addIssue(
                    sourceRow,
                    "LOW",
                    "WEB_NOT_IMPORTED",
                    "Web",
                    raw,
                    "Web value is not a recognized social profile and no generic website field is configured.",
                    "Review and add the website manually if useful."
            );

            return;
        }

        if (socialExists(clientId, url)) {
            return;
        }

        String display = url
                .replaceFirst("(?i)^https?://", "")
                .replaceFirst("(?i)^www\\.", "")
                .replaceFirst("/$", "");

        String hyperlink =
                "<a href=\"" +
                        ImportUtil.htmlEscape(url) +
                        "\" target=\"_blank\" style=\"color:#1a73e8;text-decoration:underline;\">" +
                        ImportUtil.htmlEscape(display) +
                        "</a>";

        FormRow row = JogetFormUtil.createRow(
                ImportUtil.uuid()
        );

        row.setProperty("fk", clientId);
        row.setProperty("platform", platform);
        row.setProperty("platform_name", "");
        row.setProperty("profile_link", url);
        row.setProperty("hyperlink", hyperlink);

        JogetFormUtil.save(
                SOCIAL_FORM,
                SOCIAL_TABLE,
                row
        );

        result.incrementSocialCreated();
    }

    private boolean socialExists(String clientId, String url) {

        FormRowSet rows = findByField(
                SOCIAL_FORM,
                SOCIAL_TABLE,
                "fk",
                clientId
        );

        for (FormRow row : rows) {

            if (ImportUtil.lower(url).equals(
                    ImportUtil.lower(row.getProperty("profile_link"))
            )) {
                return true;
            }
        }

        return false;
    }

    // =========================================================================
    // INDIVIDUAL ORGANIZATION MAP
    // =========================================================================

    private void ensureClientMap(String individualId, String organizationId) {

        if (ImportUtil.isEmpty(individualId) ||
                ImportUtil.isEmpty(organizationId)) {

            return;
        }

        if (clientMapExists(individualId, organizationId)) {
            return;
        }

        FormRow row = JogetFormUtil.createRow(
                ImportUtil.uuid()
        );

        row.setProperty(
                "individual",
                individualId
        );

        row.setProperty(
                "organization",
                organizationId
        );

        row.setProperty("title", "");
        row.setProperty("client_type", "O");

        JogetFormUtil.save(
                CLIENT_MAP_FORM,
                CLIENT_MAP_TABLE,
                row
        );

        result.incrementMapsCreated();
    }

    private boolean clientMapExists(String individualId, String organizationId) {

        return findOneByTwoFields(
                CLIENT_MAP_FORM,
                CLIENT_MAP_TABLE,
                "individual",
                individualId,
                "organization",
                organizationId
        ) != null;
    }

    // =========================================================================
    // PRIMARY SAFETY
    // =========================================================================

    private void ensurePrimaries(String clientId) {

        ensurePrimaryPhone(clientId);
        ensurePrimaryEmail(clientId);
        ensurePrimaryAddress(clientId);
    }

    private void ensurePrimaryPhone(String clientId) {

        if (hasPrimaryPhone(clientId)) return;

        FormRowSet rows = findByField(
                PHONE_FORM,
                PHONE_TABLE,
                "fk",
                clientId
        );

        if (rows.isEmpty()) return;

        FormRow selected = null;

        for (FormRow row : rows) {

            if ("Billing".equalsIgnoreCase(
                    row.getProperty("contact_type")
            )) {

                selected = row;
                break;
            }
        }

        if (selected == null) {
            selected = rows.get(0);
        }

        selected.setProperty(
                "contact_priority",
                "Primary"
        );

        updateExisting(
                selected,
                PHONE_FORM,
                PHONE_TABLE
        );
    }

    private void ensurePrimaryEmail(String clientId) {

        if (hasPrimaryEmail(clientId)) return;

        FormRowSet rows = findByField(
                EMAIL_FORM,
                EMAIL_TABLE,
                "parent_id",
                clientId
        );

        if (rows.isEmpty()) return;

        FormRow selected = null;

        for (FormRow row : rows) {

            if ("Billing".equalsIgnoreCase(
                    row.getProperty("email_type")
            )) {

                selected = row;
                break;
            }
        }

        if (selected == null) {
            selected = rows.get(0);
        }

        selected.setProperty(
                "email_priority",
                "Primary"
        );

        updateExisting(
                selected,
                EMAIL_FORM,
                EMAIL_TABLE
        );
    }

    private void ensurePrimaryAddress(String clientId) {

        if (hasPrimaryAddress(clientId)) return;

        FormRowSet rows = findByField(
                ADDRESS_FORM,
                ADDRESS_TABLE,
                "fk",
                clientId
        );

        if (rows.isEmpty()) return;

        FormRow selected = null;

        for (FormRow row : rows) {

            if ("Billing".equalsIgnoreCase(
                    row.getProperty("address_type")
            )) {

                selected = row;
                break;
            }
        }

        if (selected == null) {
            selected = rows.get(0);
        }

        selected.setProperty(
                "address_priority",
                "Primary"
        );

        updateExisting(
                selected,
                ADDRESS_FORM,
                ADDRESS_TABLE
        );
    }

    // =========================================================================
    // CACHE LOAD
    // =========================================================================

    private void loadCaches(List<ClientImportRow> rows) {

        countryCodes = loadCountryCodes();

        if (!countryCodes.contains("+1")) {
            countryCodes.add("+1");
        }

        ImportUtil.sortCountryCodes(countryCodes);

        loadOrganizationCache();
        loadLocationData();
        loadIndividualDuplicateCache();

        companyIndex = buildCompanyIndex(rows);

        countryAliases = ImportUtil.buildCountryAliases();
        stateAliases = ImportUtil.buildStateAliases();

    }

    private List<String> loadCountryCodes() {

        List<String> codes = new ArrayList<>();

        FormRowSet rows = findAll(
                COUNTRY_CODE_FORM,
                COUNTRY_CODE_TABLE
        );

        for (FormRow row : rows) {

            String code = ImportUtil.value(
                    row.getProperty("country_code")
            );

            if (!code.isEmpty() &&
                    !codes.contains(code)) {

                codes.add(code);
            }
        }

        return codes;
    }

    private void loadOrganizationCache() {

        organizationCache.clear();

        FormRowSet rows = findByField(
                CLIENT_FORM,
                CLIENT_TABLE,
                "client_type",
                "O"
        );

        for (FormRow row : rows) {

            String key = ImportUtil.normalizeOrganizationName(
                    row.getProperty("client_name")
            );

            if (!key.isEmpty()) {
                addMulti(
                        organizationCache,
                        key,
                        row.getId()
                );
            }
        }
    }

    private void loadIndividualDuplicateCache() {

        individualNameCache.clear();
        individualPhoneCache.clear();
        individualEmailCache.clear();

        FormRowSet clients = findByField(
                CLIENT_FORM,
                CLIENT_TABLE,
                "client_type",
                "I"
        );

        for (FormRow row : clients) {

            String key = ImportUtil.individualNameKey(
                    row.getProperty("first_name"),
                    row.getProperty("last_name")
            );

            addMulti(
                    individualNameCache,
                    key,
                    row.getId()
            );
        }

        FormRowSet phones = findAll(
                PHONE_FORM,
                PHONE_TABLE
        );

        for (FormRow row : phones) {

            String clientId = ImportUtil.value(
                    row.getProperty("fk")
            );

            String key = normalizedPhoneKey(
                    row.getProperty("country_code"),
                    row.getProperty("phone_no")
            );

            addSetValue(
                    individualPhoneCache,
                    clientId,
                    key
            );
        }

        FormRowSet emails = findAll(
                EMAIL_FORM,
                EMAIL_TABLE
        );

        for (FormRow row : emails) {

            String clientId = ImportUtil.value(
                    row.getProperty("parent_id")
            );

            String email = ImportUtil.normalizeEmail(
                    row.getProperty("email_id")
            );

            addSetValue(
                    individualEmailCache,
                    clientId,
                    email
            );
        }
    }

    private void loadLocationData() {

        countryByName.clear();
        stateByKey.clear();

        FormRowSet countries = findAll(
                COUNTRY_FORM,
                COUNTRY_TABLE
        );

        for (FormRow row : countries) {

            String name = firstValue(
                    row,
                    "country_name",
                    "name"
            );

            String iso = ImportUtil.upper(
                    firstValue(
                            row,
                            "country_code",
                            "iso2",
                            "iso_code",
                            "country_iso2",
                            "code"
                    )
            );

            String id = ImportUtil.upper(
                    row.getId()
            );

            if (!iso.matches("^[A-Z]{2}$") &&
                    id.matches("^[A-Z]{2}$")) {

                iso = id;
            }

            if (iso.matches("^[A-Z]{2}$") &&
                    !ImportUtil.isEmpty(name)) {

                countryByName.put(
                        ImportUtil.lower(name),
                        iso
                );
            }
        }

        FormRowSet states = findAll(
                STATE_FORM,
                STATE_TABLE
        );

        for (FormRow row : states) {

            String name = firstValue(
                    row,
                    "state_name",
                    "name"
            );

            String code = ImportUtil.upper(
                    firstValue(
                            row,
                            "state_code",
                            "iso_code",
                            "state_iso_code",
                            "code"
                    )
            );

            String country = ImportUtil.collapseSpaces(
                    firstValue(
                            row,
                            "country_code",
                            "country",
                            "country_iso2",
                            "country_id"
                    )
            );

            String id = ImportUtil.upper(
                    row.getId()
            );

            if (!code.matches("^[A-Z]{2}-[A-Z0-9]{1,4}$") &&
                    id.matches("^[A-Z]{2}-[A-Z0-9]{1,4}$")) {

                code = id;
            }

            if (!code.matches("^[A-Z]{2}-[A-Z0-9]{1,4}$") &&
                    code.matches("^[A-Z0-9]{1,4}$") &&
                    !country.isEmpty()) {

                String countryIso = ImportUtil.upper(country);

                if (!countryIso.matches("^[A-Z]{2}$")) {

                    String byName = countryByName.get(
                            ImportUtil.lower(country)
                    );

                    if (byName != null) {
                        countryIso = byName;
                    }
                }

                if (countryIso.matches("^[A-Z]{2}$")) {
                    code = countryIso + "-" + code;
                }
            }

            if (code.matches("^[A-Z]{2}-[A-Z0-9]{1,4}$")) {

                addStateCandidate(
                        name,
                        code
                );

                addStateCandidate(
                        code,
                        code
                );

                addStateCandidate(
                        code.substring(
                                code.indexOf('-') + 1
                        ),
                        code
                );
            }
        }
    }

    private Map<String, ClientImportRow> buildCompanyIndex(List<ClientImportRow> rows) {

        Map<String, ClientImportRow> index = new HashMap<>();

        for (ClientImportRow row : rows) {

            if (!row.isCompany()) continue;

            String name = ImportUtil.collapseSpaces(
                    row.getOrganizationName()
            );

            String key = ImportUtil.normalizeOrganizationName(
                    name
            );

            if (!key.isEmpty() &&
                    !index.containsKey(key)) {

                index.put(
                        key,
                        row
                );
            }
        }

        return index;
    }

    // =========================================================================
    // CLIENT ID
    // =========================================================================



    private synchronized String nextClientId() {

        AppDefinition originalAppDef = null;

        try {
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

            originalAppDef = AppUtil.getCurrentAppDefinition();

            AppDefinition lmsAppDef = appService.getAppDefinition(
                    "lms",
                    AppDefinition.VERSION_LATEST
            );

            if (lmsAppDef == null) {
                throw new RuntimeException("LMS app definition not found");
            }

            AppUtil.setCurrentAppDefinition(lmsAppDef);

            return AppUtil.idGenerator(
                    clientIdGenerator,
                    clientIdFormat,
                    false,
                    getClass().getName()
            );

        } finally {
            AppUtil.setCurrentAppDefinition(originalAppDef);
        }
    }

    // =========================================================================
    // ORGANIZATION DUPLICATE
    // =========================================================================

    private String uniqueOrganizationId(String key) throws Exception {

        if (ImportUtil.isEmpty(key)) {
            return "";
        }

        List<String> ids = organizationCache.get(key);

        if (ids == null || ids.isEmpty()) {
            return "";
        }

        if (ids.size() > 1) {

            throw new Exception(
                    "Ambiguous organization duplicate. Normalized key [" +
                            key +
                            "] maps to multiple LMS clients: " +
                            ids
            );
        }

        return ids.get(0);
    }

    // =========================================================================
    // CACHE UPDATE AFTER SUCCESS
    // =========================================================================

    private void cacheIndividualResult(String clientId,
                                       String firstName,
                                       String lastName,
                                       List<ImportPhone> phones,
                                       List<ImportEmail> emails) {

        addMulti(
                individualNameCache,
                ImportUtil.individualNameKey(
                        firstName,
                        lastName
                ),
                clientId
        );

        for (ImportPhone phone : phones) {

            addSetValue(
                    individualPhoneCache,
                    clientId,
                    normalizedPhoneKey(phone)
            );
        }

        for (ImportEmail email : emails) {

            addSetValue(
                    individualEmailCache,
                    clientId,
                    ImportUtil.normalizeEmail(
                            email.getEmail()
                    )
            );
        }
    }

    // =========================================================================
    // DUPLICATE REPORT
    // =========================================================================

    private void addDuplicateMerge(ClientImportRow row,
                                   String clientId,
                                   String matchBasis,
                                   String originalExcelRow,
                                   ClientSnapshot before,
                                   ClientSnapshot after) {

        DuplicateMergeResult duplicate = new DuplicateMergeResult();

        duplicate.setExcelRow(
                row.getRowNumber()
        );

        duplicate.setOriginalExcelRow(
                originalExcelRow
        );

        duplicate.setType(
                row.getType()
        );

        duplicate.setClientId(
                clientId
        );

        duplicate.setOrganizationId(
                row.getOrganizationId()
        );

        duplicate.setClientNameBefore(
                before == null
                        ? ""
                        : before.clientName
        );

        duplicate.setClientNameAfter(
                after == null
                        ? ""
                        : after.clientName
        );

        duplicate.setMatchBasis(
                matchBasis
        );

        duplicate.setSourceName(
                row.getClientDisplayName()
        );

        duplicate.setSourceCompany(
                row.getOrganizationName()
        );

        duplicate.setPhonesBefore(
                before == null
                        ? ""
                        : ImportUtil.join(before.phones, " || ")
        );

        duplicate.setPhonesIncoming(
                incomingPhonesText(row.getPhones())
        );

        duplicate.setPhonesAfter(
                after == null
                        ? ""
                        : ImportUtil.join(after.phones, " || ")
        );

        duplicate.setEmailsBefore(
                before == null
                        ? ""
                        : ImportUtil.join(before.emails, " || ")
        );

        duplicate.setEmailsIncoming(
                incomingEmailsText(row.getEmails())
        );

        duplicate.setEmailsAfter(
                after == null
                        ? ""
                        : ImportUtil.join(after.emails, " || ")
        );

        duplicate.setAddressesBefore(
                before == null
                        ? ""
                        : ImportUtil.join(before.addresses, " || ")
        );

        duplicate.setAddressesIncoming(
                incomingAddressesText(row.getAddresses())
        );

        duplicate.setAddressesAfter(
                after == null
                        ? ""
                        : ImportUtil.join(after.addresses, " || ")
        );

        duplicate.setPhonesAddedOrChanged(
                before == null || after == null
                        ? ""
                        : ImportUtil.join(
                        ImportUtil.difference(
                                after.phones,
                                before.phones
                        ),
                        " || "
                )
        );

        duplicate.setEmailsAddedOrChanged(
                before == null || after == null
                        ? ""
                        : ImportUtil.join(
                        ImportUtil.difference(
                                after.emails,
                                before.emails
                        ),
                        " || "
                )
        );

        duplicate.setAddressesAddedOrChanged(
                before == null || after == null
                        ? ""
                        : ImportUtil.join(
                        ImportUtil.difference(
                                after.addresses,
                                before.addresses
                        ),
                        " || "
                )
        );

        duplicate.setResult(
                "MERGED_INTO_EXISTING_CLIENT"
        );

        result.addDuplicateRow(
                duplicate
        );
    }

    private ClientSnapshot safeClientSnapshot(String clientId) {

        try {

            return loadClientSnapshot(
                    clientId
            );

        } catch (Exception e) {

            DebugLogger.warn(
                    getClass().getName(),
                    "REPORT_SNAPSHOT clientId=" +
                            clientId +
                            " reason=" +
                            ImportUtil.safeLog(e.getMessage())
            );

            return new ClientSnapshot(
                    clientId
            );
        }
    }

    private ClientSnapshot loadClientSnapshot(String clientId) {

        ClientSnapshot snapshot = new ClientSnapshot(
                clientId
        );

        FormRow client = formDataDao.load(
                CLIENT_FORM,
                CLIENT_TABLE,
                clientId
        );

        if (client != null) {

            snapshot.clientType = ImportUtil.value(
                    client.getProperty("client_type")
            );

            snapshot.clientName = ImportUtil.value(
                    client.getProperty("client_name")
            );
        }

        FormRowSet phones = findByField(
                PHONE_FORM,
                PHONE_TABLE,
                "fk",
                clientId
        );

        for (FormRow row : phones) {

            String value = ImportUtil.collapseSpaces(
                    ImportUtil.value(row.getProperty("country_code")) +
                            " " +
                            ImportUtil.value(row.getProperty("phone_no"))
            );

            String extension = ImportUtil.value(
                    row.getProperty("business_extension")
            );

            if (!extension.isEmpty()) {
                value += " ext " + extension;
            }

            value +=
                    " [" +
                            ImportUtil.value(row.getProperty("contact_type")) +
                            "; " +
                            ImportUtil.value(row.getProperty("contact_priority")) +
                            "]";

            snapshot.phones.add(
                    value
            );
        }

        FormRowSet emails = findByField(
                EMAIL_FORM,
                EMAIL_TABLE,
                "parent_id",
                clientId
        );

        for (FormRow row : emails) {

            snapshot.emails.add(
                    ImportUtil.value(row.getProperty("email_id")) +
                            " [" +
                            ImportUtil.value(row.getProperty("email_type")) +
                            "; " +
                            ImportUtil.value(row.getProperty("email_priority")) +
                            "]"
            );
        }

        FormRowSet addresses = findByField(
                ADDRESS_FORM,
                ADDRESS_TABLE,
                "fk",
                clientId
        );

        for (FormRow row : addresses) {

            snapshot.addresses.add(
                    ImportUtil.value(row.getProperty("address_type")) +
                            " | " +
                            ImportUtil.value(row.getProperty("address_line_1")) +
                            " | " +
                            ImportUtil.value(row.getProperty("address_line_2")) +
                            " | " +
                            ImportUtil.value(row.getProperty("city")) +
                            " | " +
                            ImportUtil.value(row.getProperty("state")) +
                            " | " +
                            ImportUtil.value(row.getProperty("zipcode")) +
                            " | " +
                            ImportUtil.value(row.getProperty("country")) +
                            " [" +
                            ImportUtil.value(row.getProperty("address_priority")) +
                            "]"
            );
        }

        return snapshot;
    }

    // =========================================================================
    // SUCCESS / FAILED
    // =========================================================================

    private void markSuccessfulRow(ClientImportRow row,
                                   String clientId,
                                   boolean created,
                                   boolean organization) {

        row.setClientId(
                clientId
        );

        handledRows.add(
                row.getRowNumber()
        );

        successfulFingerprints.add(
                row.getFingerprint()
        );

        FingerprintInfo info = new FingerprintInfo();

        info.rowNumber = row.getRowNumber();
        info.clientId = clientId;

        successfulFingerprintInfo.put(
                row.getFingerprint(),
                info
        );

        if (created) {

            result.incrementClientsCreated();

            if (organization) {
                result.incrementOrganizationsCreated();
            } else {
                result.incrementIndividualsCreated();
            }

            row.setStatus("CREATED");

        } else {

            result.incrementClientsMatched();

            if (organization) {
                result.incrementOrganizationsMatched();
            } else {
                result.incrementIndividualsMatched();
            }

            row.setStatus("MATCHED_EXISTING");
        }

        if (row.hasIssues()) {
            result.incrementRowsWithWarnings();
        }

        result.incrementRowsSucceeded();

        logRow(row);
    }

    private void markSourceDuplicate(ClientImportRow row) {

        row.setStatus(
                "SOURCE_DUPLICATE"
        );

        FingerprintInfo info = successfulFingerprintInfo.get(
                row.getFingerprint()
        );

        if (info != null) {
            row.setClientId(info.clientId);
        }

        handledRows.add(
                row.getRowNumber()
        );

        result.incrementSourceDuplicates();

        logRow(row);
    }

    private void failRow(ClientImportRow row,
                         String code,
                         String field,
                         String value,
                         String message,
                         String action) {

        if (row.isFailed()) return;

        row.setStatus("FAILED");
        row.setError(message);

        handledRows.add(
                row.getRowNumber()
        );

        result.incrementRowsFailed();
        result.addFailedRow(row);

        addIssue(
                row,
                "HIGH",
                code,
                field,
                value,
                message,
                action
        );

        logRow(row);
    }

    private void addIssue(ClientImportRow row,
                          String risk,
                          String code,
                          String field,
                          String value,
                          String message,
                          String action) {

        ImportIssue issue = new ImportIssue(
                risk,
                code,
                row.getRowNumber(),
                row.getType(),
                row.getClientId(),
                field,
                value,
                message,
                action
        );

        row.addIssue(issue);
        result.addIssue(issue);
    }

    // =========================================================================
    // PROGRESS
    // =========================================================================

    private void updateProgressIfNeeded(int processed,
                                        int total,
                                        ClientImportRow row) {

        int interval = options.getProgressUpdateInterval();

        if (processed != total &&
                processed % interval != 0) {

            return;
        }

        String message =
                "Processed " +
                        processed +
                        " of " +
                        total +
                        " source rows";

        if (row != null) {

            message +=
                    " | Excel row " +
                            row.getRowNumber() +
                            " | " +
                            row.getStatus();
        }

        try {

            JogetFormUtil.updateProgress(
                    properties,
                    "PROCESSING",
                    processed,
                    total,
                    result.getRowsSucceeded(),
                    result.getRowsFailed(),
                    result.getClientsMatched() + result.getSourceDuplicates(),
                    message
            );

        } catch (Exception e) {

            DebugLogger.warn(
                    getClass().getName(),
                    "Unable to update import progress: " +
                            ImportUtil.safeLog(e.getMessage())
            );
        }
    }

    // =========================================================================
    // FINAL RESULT
    // =========================================================================

    private void finishResult() {

        if (result.getRowsFailed() > 0) {

            result.setStatus(
                    "COMPLETED_WITH_ERRORS"
            );

        } else if (!result.getIssues().isEmpty()) {

            result.setStatus(
                    "COMPLETED_WITH_WARNINGS"
            );

        } else {

            result.setStatus(
                    "COMPLETED"
            );
        }

        result.buildSummary();
    }

    // =========================================================================
    // FORMDATADAO HELPERS
    // =========================================================================

    private FormRowSet findAll(String formId, String tableName) {

        FormRowSet rows = formDataDao.find(
                formId,
                tableName,
                "",
                new Object[]{},
                "dateCreated",
                false,
                null,
                null
        );

        return rows == null
                ? new FormRowSet()
                : rows;
    }

    private FormRowSet findByField(String formId,
                                   String tableName,
                                   String field,
                                   Object value) {

        String condition =
                " WHERE " +
                        FormUtil.PROPERTY_CUSTOM_PROPERTIES +
                        "." +
                        field +
                        " = ?";

        FormRowSet rows = formDataDao.find(
                formId,
                tableName,
                condition,
                new Object[]{value},
                "dateCreated",
                false,
                null,
                null
        );

        return rows == null
                ? new FormRowSet()
                : rows;
    }

    private FormRow findOneByTwoFields(String formId,
                                       String tableName,
                                       String field1,
                                       Object value1,
                                       String field2,
                                       Object value2) {

        String condition =
                " WHERE " +
                        FormUtil.PROPERTY_CUSTOM_PROPERTIES +
                        "." +
                        field1 +
                        " = ?" +
                        " AND " +
                        FormUtil.PROPERTY_CUSTOM_PROPERTIES +
                        "." +
                        field2 +
                        " = ?";

        FormRowSet rows = formDataDao.find(
                formId,
                tableName,
                condition,
                new Object[]{
                        value1,
                        value2
                },
                null,
                null,
                0,
                1
        );

        return rows == null || rows.isEmpty()
                ? null
                : rows.get(0);
    }

    private void updateExisting(FormRow row,
                                String formId,
                                String tableName) {

        row.setDateModified(
                new Date()
        );

        row.setProperty(
                "modifiedBy",
                JogetFormUtil.getCurrentUsername()
        );

        row.setProperty(
                "modifiedByName",
                JogetFormUtil.getCurrentUserFullName()
        );

        JogetFormUtil.save(
                formId,
                tableName,
                row
        );
    }

    // =========================================================================
    // CACHE HELPERS
    // =========================================================================

    private void addMulti(Map<String, List<String>> map,
                          String key,
                          String value) {

        if (ImportUtil.isEmpty(key) ||
                ImportUtil.isEmpty(value)) {

            return;
        }

        List<String> values = map.computeIfAbsent(
                key,
                k -> new ArrayList<>()
        );

        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private void addSetValue(Map<String, Set<String>> map,
                             String key,
                             String value) {

        if (ImportUtil.isEmpty(key) ||
                ImportUtil.isEmpty(value)) {

            return;
        }

        Set<String> values = map.computeIfAbsent(
                key,
                k -> new HashSet<>()
        );

        values.add(value);
    }

    private void addStateCandidate(String key, String code) {

        if (ImportUtil.isEmpty(key) ||
                ImportUtil.isEmpty(code)) {

            return;
        }

        String normalized = ImportUtil.lower(
                ImportUtil.collapseSpaces(key)
        );

        List<String> values = stateByKey.computeIfAbsent(
                normalized,
                k -> new ArrayList<>()
        );

        if (!values.contains(code)) {
            values.add(code);
        }
    }

    // =========================================================================
    // PHONE UNIQUE KEY
    // =========================================================================

    private String normalizedPhoneKey(ImportPhone phone) {

        if (phone == null) return "";

        return normalizedPhoneKey(
                phone.getCountryCode(),
                phone.getPhone()
        );
    }

    private String normalizedPhoneKey(String countryCode, String phone) {

        String code = ImportUtil.value(
                countryCode
        ).replaceAll("\\s+", "");

        String number = ImportUtil.value(
                phone
        ).replaceAll("[\\s()\\-]", "");

        if (number.isEmpty()) return "";

        /*
         * IMPORTANT FIX:
         *
         * Phone uniqueness now includes country code.
         */
        return code + "|" + number;
    }

    // =========================================================================
    // REPORT TEXT HELPERS
    // =========================================================================

    private String incomingPhonesText(List<ImportPhone> phones) {

        List<String> values = new ArrayList<>();

        for (ImportPhone phone : phones) {

            String value = ImportUtil.collapseSpaces(
                    ImportUtil.value(phone.getCountryCode()) +
                            " " +
                            ImportUtil.value(phone.getPhone())
            );

            if (!ImportUtil.isEmpty(phone.getExtension())) {
                value += " ext " + phone.getExtension();
            }

            value +=
                    " [" +
                            phone.getType() +
                            "; " +
                            (phone.isPrimary() ? "Primary" : "No") +
                            "]";

            values.add(value);
        }

        return ImportUtil.join(
                values,
                " || "
        );
    }

    private String incomingEmailsText(List<ImportEmail> emails) {

        List<String> values = new ArrayList<>();

        for (ImportEmail email : emails) {

            values.add(
                    email.getEmail() +
                            " [" +
                            email.getType() +
                            "; " +
                            (email.isPrimary() ? "Primary" : "---") +
                            "]"
            );
        }

        return ImportUtil.join(
                values,
                " || "
        );
    }

    private String incomingAddressesText(List<ImportAddress> addresses) {

        List<String> values = new ArrayList<>();

        for (ImportAddress address : addresses) {

            values.add(
                    ImportUtil.value(address.getType()) +
                            " | " +
                            ImportUtil.value(address.getLine1()) +
                            " | " +
                            ImportUtil.value(address.getLine2()) +
                            " | " +
                            ImportUtil.value(address.getRawCity()) +
                            " | " +
                            ImportUtil.value(address.getRawState()) +
                            " | " +
                            ImportUtil.value(address.getZip()) +
                            " | " +
                            ImportUtil.value(address.getRawCountry())
            );
        }

        return ImportUtil.join(
                values,
                " || "
        );
    }

    // =========================================================================
    // SMALL HELPERS
    // =========================================================================

    private String firstValue(FormRow row, String... names) {

        for (String name : names) {

            String value = ImportUtil.value(
                    row.getProperty(name)
            );

            if (!value.isEmpty()) {
                return value;
            }

            value = ImportUtil.value(
                    row.getProperty("c_" + name)
            );

            if (!value.isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private boolean equalsIgnoreCase(String a, String b) {

        return ImportUtil.lower(a).equals(
                ImportUtil.lower(b)
        );
    }

    private String createRunId() {

        return new java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS"
        ).format(new Date()) +
                "_" +
                ImportUtil.uuid().substring(0, 8);
    }

    private int intProperty(Map properties,
                            String key,
                            int defaultValue) {

        try {

            return Integer.parseInt(
                    JogetFormUtil.property(
                            properties,
                            key,
                            String.valueOf(defaultValue)
                    )
            );

        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String rootMessage(Throwable throwable) {

        if (throwable == null) {
            return "Unknown import error.";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        if (ImportUtil.isEmpty(message)) {
            return current.getClass().getSimpleName();
        }

        return ImportUtil.safeLog(message);
    }

    private Exception unwrap(ImportRuntimeException e) {

        if (e.getCause() instanceof Exception) {
            return (Exception) e.getCause();
        }

        return new Exception(
                e.getMessage(),
                e
        );
    }

    private void logRow(ClientImportRow row) {

        String message =
                "excelRow=" + row.getRowNumber() +
                        " type=" + ImportUtil.safeLog(row.getType()) +
                        " status=" + ImportUtil.safeLog(row.getStatus()) +
                        " clientId=" + ImportUtil.safeLog(row.getClientId()) +
                        " organizationId=" + ImportUtil.safeLog(row.getOrganizationId()) +
                        " issues=" + row.getIssueCount() +
                        " error=" + ImportUtil.safeLog(row.getError());

        if (row.isFailed()) {

            DebugLogger.warn(
                    getClass().getName(),
                    "ROW | " + message
            );

        } else {

            DebugLogger.info(
                    getClass().getName(),
                    "ROW | " + message
            );
        }
    }

    // =========================================================================
    // INTERNAL CLASSES
    // =========================================================================

    private static class ClientSnapshot {

        String clientId;
        String clientType = "";
        String clientName = "";

        List<String> phones = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        List<String> addresses = new ArrayList<>();

        ClientSnapshot(String clientId) {
            this.clientId = clientId;
        }
    }

    private static class FingerprintInfo {

        int rowNumber;
        String clientId;
    }

    private static class ImportRuntimeException extends RuntimeException {

        ImportRuntimeException(Throwable cause) {

            super(
                    cause == null
                            ? null
                            : cause.getMessage(),
                    cause
            );
        }
    }
}