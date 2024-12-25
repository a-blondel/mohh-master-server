package com.ea.enums;

public enum Certificates {
    MOHH_PSP("MOHH_PSP",
            "CN=pspmoh07.ea.com, OU=Global Online Studio, O=Electronic Arts, Inc., ST=California, C=US",
            "OU=Online Technology Group, O=Electronic Arts, Inc., L=Redwood City, ST=California, C=US, CN=OTG3 Certificate Authority"),
    MOHH2_WII("MOHH2_WII",
            "CN=wiimoh08.ea.com, OU=Global Online Studio, O=Electronic Arts, Inc., ST=California, C=US",
            "OU=Online Technology Group, O=Electronic Arts, Inc., L=Redwood City, ST=California, C=US, CN=OTG3 Certificate Authority"),
    MOHH2_PSP("MOHH2_PSP",
            "CN=pspmoh08.ea.com, OU=Global Online Studio, O=Electronic Arts, Inc., ST=California, C=US",
            "OU=Online Technology Group, O=Electronic Arts, Inc., L=Redwood City, ST=California, C=US, CN=OTG3 Certificate Authority");

    private final String name;
    private final String subject;
    private final String issuer;

    Certificates(String name, String subject, String issuer) {
        this.name = name;
        this.subject = subject;
        this.issuer = issuer;
    }

    public String getName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    public String getIssuer() {
        return issuer;
    }
}
