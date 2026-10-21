package com.loxs.model;

public enum ScanType {
    SQLI("SQLi", "Zaman tabanlı SQL enjeksiyonu", "sqli.txt", true, false),
    LFI("LFI", "Yerel dosya okuma / path traversal", "lfi.txt", true, true),
    CRLF("CRLF", "Header ve yanıt bölme enjeksiyonu", null, false, false),
    XSS("XSS", "Yansıyan XSS kontrolü", "xss.txt", true, false),
    OPEN_REDIRECT("Open Redirect", "Açık yönlendirme kontrolü", "or.txt", true, false);

    private final String label;
    private final String description;
    private final String payloadResource;
    private final boolean payloadRequired;
    private final boolean criteriaRelevant;

    ScanType(String label, String description, String payloadResource,
             boolean payloadRequired, boolean criteriaRelevant) {
        this.label = label;
        this.description = description;
        this.payloadResource = payloadResource;
        this.payloadRequired = payloadRequired;
        this.criteriaRelevant = criteriaRelevant;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String payloadResource() {
        return payloadResource;
    }

    public boolean payloadRequired() {
        return payloadRequired;
    }

    public boolean criteriaRelevant() {
        return criteriaRelevant;
    }

    @Override
    public String toString() {
        return label;
    }
}
