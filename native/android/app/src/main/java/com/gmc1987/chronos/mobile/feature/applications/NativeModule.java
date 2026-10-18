package com.gmc1987.chronos.mobile.feature.applications;

/** A server-backed native module shown inside an application hub. */
final class NativeModule {
    static final String RECORDS = "RECORDS";
    static final String LEAVES = "LEAVES";
    static final String OFFICE_REQUESTS = "OFFICE_REQUESTS";
    static final String BUSINESS_TRIPS = "BUSINESS_TRIPS";

    final String title;
    final String detail;
    final String kind;
    final String path;

    NativeModule(String title, String detail, String path) {
        this(title, detail, RECORDS, path);
    }

    NativeModule(String title, String detail, String kind, String path) {
        this.title = title;
        this.detail = detail;
        this.kind = kind;
        this.path = path;
    }
}
