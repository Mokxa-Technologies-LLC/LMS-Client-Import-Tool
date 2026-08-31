package org.joget.mokxa.util;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joget.mokxa.model.ImportPhone;

public final class ImportUtil {

    private ImportUtil() {
    }

    // =========================================================================
    // STRING HELPERS
    // =========================================================================

    public static String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String lower(String value) {
        return value(value).toLowerCase();
    }

    public static String upper(String value) {
        return value(value).toUpperCase();
    }

    public static String collapseSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public static String safeLog(String value) {
        return collapseSpaces(value(value).replace('\r', ' ').replace('\n', ' '));
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String nowText() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    public static String leftPadNumber(int value, int pad) {
        String number = String.valueOf(value);
        StringBuilder result = new StringBuilder();

        for (int i = number.length(); i < pad; i++) result.append('0');

        return result.append(number).toString();
    }

    public static String sanitizeFileName(String value) {
        String result = value(value);

        if (result.isEmpty()) result = "file";

        result = result.replaceAll("[^a-zA-Z0-9._-]", "_");

        while (result.contains("__")) result = result.replace("__", "_");

        return result;
    }

    public static String stripTrailingSlash(String value) {
        String result = value(value);

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    // =========================================================================
    // HASHING
    // =========================================================================

    public static String sha256(String text) throws Exception {

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
    // CLIENT / NAME HELPERS
    // =========================================================================

    public static String buildIndividualClientName(String firstName, String middleName, String lastName) {
        return collapseSpaces(value(firstName) + " " + value(middleName) + " " + value(lastName));
    }

    public static String individualNameKey(String firstName, String lastName) {
        return lower(collapseSpaces(firstName)) + "\u001F" + lower(collapseSpaces(lastName));
    }

    public static String normalizeOrganizationName(String name) {

        if (name == null) return "";

        String result = name.toLowerCase().trim();

        result = result.replace("&", " and ");
        result = result.replaceAll("[\\.,\"'\\(\\)\\-_/]", " ");

        result = result.replaceAll(
                "\\bpvt\\b|\\bpriv\\b|\\bprivate\\b|\\bltd\\b|\\blimited\\b|" +
                        "\\bco\\b|\\bcompany\\b|\\bcorp\\b|\\bcorporation\\b|" +
                        "\\binc\\b|\\bincorporated\\b|\\bllp\\b|\\bllc\\b|\\band\\b",
                ""
        );

        result = result.replaceAll("\\s+", "");

        if (result.isEmpty()) result = collapseSpaces(name).toLowerCase();

        return result;
    }

    // =========================================================================
    // EMAIL
    // =========================================================================

    public static String normalizeEmail(String email) {
        return value(email).toLowerCase();
    }

    public static boolean validEmail(String email) {
        return !isEmpty(email) && email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    // =========================================================================
    // PHONE
    // =========================================================================

    public static String countryDigits(String code) {
        return value(code).replaceAll("[^0-9]", "");
    }

    public static String scientificToPlain(String raw) {

        String value = value(raw);

        if (value.matches("^[+-]?[0-9]+(?:\\.[0-9]+)?[eE][+-]?[0-9]+$")) {
            try {
                return new BigDecimal(value).toPlainString();
            } catch (Exception ignore) {
            }
        }

        return value;
    }

    public static String extractExtension(String raw) {

        if (raw == null) return "";

        Matcher matcher = Pattern.compile(
                "(?i)(?:ext(?:ension)?\\.?|x)\\s*[:#-]?\\s*([0-9]{1,10})\\s*$"
        ).matcher(raw.trim());

        return matcher.find() ? matcher.group(1) : "";
    }

    public static String removeExtension(String raw) {

        if (raw == null) return "";

        return raw.replaceFirst(
                "(?i)\\s*(?:ext(?:ension)?\\.?|x)\\s*[:#-]?\\s*[0-9]{1,10}\\s*$",
                ""
        ).trim();
    }

    public static List<String> splitPhoneValues(String raw) {

        List<String> values = new ArrayList<>();

        if (isEmpty(raw)) return values;

        String[] parts = raw.split("\\s*(?:/|;|\\||\\band\\b)\\s*");

        for (String part : parts) {
            String value = value(part);
            if (!value.isEmpty()) values.add(value);
        }

        return values;
    }

    public static ImportPhone parsePhone(String raw, List<String> countryCodes) {

        String original = value(raw);

        if (original.isEmpty()) return null;

        original = scientificToPlain(original);

        String extension = extractExtension(original);
        String base = removeExtension(original);
        boolean hasPlus = base.trim().startsWith("+");
        String digits = base.replaceAll("[^0-9]", "");

        if (digits.isEmpty()) return null;

        String countryCode = "";
        String phone = digits;

        if (hasPlus) {

            for (String code : countryCodes) {

                String countryDigits = countryDigits(code);

                if (!countryDigits.isEmpty() && digits.startsWith(countryDigits) && digits.length() > countryDigits.length()) {
                    countryCode = code.startsWith("+") ? code : "+" + code;
                    phone = digits.substring(countryDigits.length());
                    break;
                }
            }

        } else if (digits.length() == 10) {

            countryCode = "+1";
            phone = digits;

        } else if (digits.length() == 11 && digits.startsWith("1")) {

            countryCode = "+1";
            phone = digits.substring(1);

        } else if (digits.length() > 10) {

            for (String code : countryCodes) {

                String countryDigits = countryDigits(code);

                if (countryDigits.isEmpty() || "1".equals(countryDigits)) continue;

                if (digits.startsWith(countryDigits)) {

                    String remaining = digits.substring(countryDigits.length());

                    if (remaining.length() >= 7 && remaining.length() <= 13) {
                        countryCode = code.startsWith("+") ? code : "+" + code;
                        phone = remaining;
                        break;
                    }
                }
            }
        }

        if (countryCode.isEmpty()) return null;

        if ("+1".equals(countryCode)) {
            if (phone.length() != 10) return null;
        } else {
            if (phone.length() < 7 || phone.length() > 13) return null;
        }

        ImportPhone result = new ImportPhone();

        result.setCountryCode(countryCode);
        result.setPhone(phone);
        result.setExtension(extension);

        return result;
    }

    public static void sortCountryCodes(List<String> countryCodes) {

        countryCodes.sort((a, b) -> {
            int aLength = countryDigits(a).length();
            int bLength = countryDigits(b).length();
            return Integer.compare(bLength, aLength);
        });
    }

    // =========================================================================
    // ADDRESS
    // =========================================================================

    public static String joinAddressParts(String... parts) {

        StringBuilder result = new StringBuilder();

        if (parts == null) return "";

        for (String part : parts) {

            String value = collapseSpaces(part);

            if (value.isEmpty()) continue;

            if (result.length() > 0) result.append(", ");

            result.append(value);
        }

        return result.toString();
    }

    // =========================================================================
    // COUNTRY ALIASES
    // =========================================================================

    public static Map<String, String> buildCountryAliases() {

        Map<String, String> aliases = new HashMap<>();

        putCountryAlias(aliases, "US", "USA,United States,United States of America,U.S.,U.S.A.");
        putCountryAlias(aliases, "CA", "Canada");
        putCountryAlias(aliases, "IN", "India");
        putCountryAlias(aliases, "GB", "UK,U.K.,United Kingdom,Great Britain");
        putCountryAlias(aliases, "AU", "Australia");
        putCountryAlias(aliases, "NZ", "New Zealand");
        putCountryAlias(aliases, "AE", "UAE,U.A.E.,United Arab Emirates");
        putCountryAlias(aliases, "SA", "KSA,Saudi Arabia,Kingdom of Saudi Arabia");

        return aliases;
    }

    private static void putCountryAlias(Map<String, String> aliases, String iso, String csv) {

        String code = upper(iso);

        if (code.isEmpty()) return;

        aliases.put(code.toLowerCase(), code);

        String[] values = csv.split(",");

        for (String value : values) {

            String alias = collapseSpaces(value);

            if (!alias.isEmpty()) aliases.put(alias.toLowerCase(), code);
        }
    }

    // =========================================================================
    // STATE ALIASES
    // =========================================================================

    public static Map<String, List<String>> buildStateAliases() {

        Map<String, List<String>> aliases = new HashMap<>();

        String us =
                "US-AL=AL,Alabama|US-AK=AK,Alaska|US-AZ=AZ,Arizona|US-AR=AR,Arkansas|" +
                        "US-CA=CA,California|US-CO=CO,Colorado|US-CT=CT,Connecticut|US-DE=DE,Delaware|" +
                        "US-FL=FL,Florida|US-GA=GA,Georgia|US-HI=HI,Hawaii|US-ID=ID,Idaho|" +
                        "US-IL=IL,Illinois|US-IN=IN,Indiana|US-IA=IA,Iowa|US-KS=KS,Kansas|" +
                        "US-KY=KY,Kentucky|US-LA=LA,Louisiana|US-ME=ME,Maine|US-MD=MD,Maryland|" +
                        "US-MA=MA,Massachusetts|US-MI=MI,Michigan|US-MN=MN,Minnesota|" +
                        "US-MS=MS,Mississippi|US-MO=MO,Missouri|US-MT=MT,Montana|US-NE=NE,Nebraska|" +
                        "US-NV=NV,Nevada|US-NH=NH,New Hampshire|US-NJ=NJ,New Jersey|" +
                        "US-NM=NM,New Mexico|US-NY=NY,New York|US-NC=NC,North Carolina|" +
                        "US-ND=ND,North Dakota|US-OH=OH,Ohio|US-OK=OK,Oklahoma|US-OR=OR,Oregon|" +
                        "US-PA=PA,Pennsylvania|US-RI=RI,Rhode Island|US-SC=SC,South Carolina|" +
                        "US-SD=SD,South Dakota|US-TN=TN,Tennessee|US-TX=TX,Texas|US-UT=UT,Utah|" +
                        "US-VT=VT,Vermont|US-VA=VA,Virginia|US-WA=WA,Washington|" +
                        "US-WV=WV,West Virginia|US-WI=WI,Wisconsin|US-WY=WY,Wyoming|" +
                        "US-DC=DC,District of Columbia";

        String ca =
                "CA-AB=AB,Alberta|CA-BC=BC,British Columbia|CA-MB=MB,Manitoba|" +
                        "CA-NB=NB,New Brunswick|CA-NL=NL,Newfoundland and Labrador|" +
                        "CA-NS=NS,Nova Scotia|CA-NT=NT,Northwest Territories|CA-NU=NU,Nunavut|" +
                        "CA-ON=ON,Ontario|CA-PE=PE,Prince Edward Island|CA-QC=QC,Quebec|" +
                        "CA-SK=SK,Saskatchewan|CA-YT=YT,Yukon";

        String in =
                "IN-AP=AP,Andhra Pradesh|IN-AR=AR,Arunachal Pradesh|IN-AS=AS,Assam|" +
                        "IN-BR=BR,Bihar|IN-CG=CG,Chhattisgarh|IN-GA=GA,Goa|IN-GJ=GJ,Gujarat|" +
                        "IN-HR=HR,Haryana|IN-HP=HP,Himachal Pradesh|IN-JH=JH,Jharkhand|" +
                        "IN-KA=KA,Karnataka|IN-KL=KL,Kerala|IN-MP=MP,Madhya Pradesh|" +
                        "IN-MH=MH,Maharashtra|IN-MN=MN,Manipur|IN-ML=ML,Meghalaya|" +
                        "IN-MZ=MZ,Mizoram|IN-NL=NL,Nagaland|IN-OD=OD,Odisha,Orissa|" +
                        "IN-PB=PB,Punjab|IN-RJ=RJ,Rajasthan|IN-SK=SK,Sikkim|" +
                        "IN-TN=TN,Tamil Nadu|IN-TS=TS,Telangana|IN-TR=TR,Tripura|" +
                        "IN-UP=UP,Uttar Pradesh|IN-UK=UK,Uttarakhand|IN-WB=WB,West Bengal|" +
                        "IN-AN=AN,Andaman and Nicobar Islands|IN-CH=CH,Chandigarh|" +
                        "IN-DL=DL,Delhi|IN-JK=JK,Jammu and Kashmir|IN-LA=LA,Ladakh|" +
                        "IN-LD=LD,Lakshadweep|IN-PY=PY,Puducherry";

        String au =
                "AU-NSW=NSW,New South Wales|AU-VIC=VIC,Victoria|AU-QLD=QLD,Queensland|" +
                        "AU-SA=SA,South Australia|AU-WA=WA,Western Australia|AU-TAS=TAS,Tasmania|" +
                        "AU-NT=NT,Northern Territory|AU-ACT=ACT,Australian Capital Territory";

        String gb =
                "GB-ENG=ENG,England|GB-SCT=SCT,Scotland|GB-WLS=WLS,Wales|" +
                        "GB-NIR=NIR,Northern Ireland";

        String[] entries = (us + "|" + ca + "|" + in + "|" + au + "|" + gb).split("\\|");

        for (String entry : entries) {

            String[] parts = entry.split("=", 2);

            if (parts.length != 2) continue;

            String code = parts[0];
            String[] names = parts[1].split(",");

            addStateAlias(aliases, code, code);

            for (String name : names) {
                addStateAlias(aliases, name, code);
            }
        }

        return aliases;
    }

    private static void addStateAlias(Map<String, List<String>> aliases, String value, String code) {

        String key = lower(collapseSpaces(value));

        if (key.isEmpty() || isEmpty(code)) return;

        List<String> values = aliases.computeIfAbsent(key, k -> new ArrayList<>());

        if (!values.contains(code)) values.add(code);
    }

    // =========================================================================
    // URL / SOCIAL
    // =========================================================================

    public static String normalizeUrl(String raw) {

        String url = value(raw);

        if (url.isEmpty()) return "";

        if (!url.matches("(?i)^https?://.*")) url = "https://" + url;

        return url;
    }

    public static String detectSocialPlatform(String url) {

        String value = lower(url);

        if (value.contains("linkedin.com")) return "LinkedIn";
        if (value.contains("facebook.com")) return "Facebook";
        if (value.contains("instagram.com")) return "Instagram";
        if (value.contains("twitter.com") || value.contains("x.com")) return "Twitter";
        if (value.contains("reddit.com")) return "Reddit";

        return "";
    }

    public static String htmlEscape(String value) {
        return value(value).replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // =========================================================================
    // REPORT HELPERS
    // =========================================================================

    public static String urlEncodeSegment(String value) throws Exception {
        return URLEncoder.encode(value(value), "UTF-8").replace("+", "%20");
    }

    public static String join(List<String> values, String separator) {

        if (values == null || values.isEmpty()) return "";

        StringBuilder result = new StringBuilder();

        for (String value : values) {

            String current = value(value);

            if (current.isEmpty()) continue;

            if (result.length() > 0) result.append(separator);

            result.append(current);
        }

        return result.toString();
    }

    public static List<String> difference(List<String> after, List<String> before) {

        List<String> result = new ArrayList<>();
        List<String> existing = before == null ? new ArrayList<>() : before;

        if (after == null) return result;

        for (String value : after) {
            if (!existing.contains(value)) result.add(value);
        }

        return result;
    }
}