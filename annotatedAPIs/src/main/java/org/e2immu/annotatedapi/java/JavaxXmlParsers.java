package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.NotNull;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class JavaxXmlParsers {
    public static final String PACKAGE_NAME = "javax.xml.parsers";

    interface DocumentBuilderFactory$ {

        @NotNull
        DocumentBuilderFactory newInstance();

        @NotNull
        DocumentBuilder newDocumentBuilder();
    }
}
