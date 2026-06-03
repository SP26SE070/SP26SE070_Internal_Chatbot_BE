package com.gsp26se114.chatbot_rag_be.service;

/** Stable codes for import validation — localized on the client. */
public final class EmployeeImportErrorCodes {

    public static final String FULL_NAME_REQUIRED = "FULL_NAME_REQUIRED";
    public static final String CONTACT_EMAIL_REQUIRED = "CONTACT_EMAIL_REQUIRED";
    public static final String CONTACT_EMAIL_INVALID = "CONTACT_EMAIL_INVALID";
    public static final String EMAIL_DUPLICATE_IN_FILE = "EMAIL_DUPLICATE_IN_FILE";
    public static final String EMAIL_EXISTS_IN_SYSTEM = "EMAIL_EXISTS_IN_SYSTEM";
    public static final String PHONE_INVALID = "PHONE_INVALID";
    public static final String PHONE_DUPLICATE_IN_FILE = "PHONE_DUPLICATE_IN_FILE";
    public static final String PHONE_EXISTS_IN_SYSTEM = "PHONE_EXISTS_IN_SYSTEM";
    public static final String DOB_INVALID = "DOB_INVALID";
    public static final String DOB_TOO_YOUNG = "DOB_TOO_YOUNG";
    public static final String DOB_TOO_OLD = "DOB_TOO_OLD";
    public static final String ADDRESS_TOO_LONG = "ADDRESS_TOO_LONG";
    public static final String ROLE_REQUIRED = "ROLE_REQUIRED";
    public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";
    public static final String ROLE_FORBIDDEN = "ROLE_FORBIDDEN";
    public static final String DEPT_NOT_FOUND = "DEPT_NOT_FOUND";
    public static final String DEPT_SCOPE_FORBIDDEN = "DEPT_SCOPE_FORBIDDEN";

    private EmployeeImportErrorCodes() {}

    public static String withParam(String code, String param) {
        return param == null || param.isBlank() ? code : code + "|" + param.trim();
    }
}
