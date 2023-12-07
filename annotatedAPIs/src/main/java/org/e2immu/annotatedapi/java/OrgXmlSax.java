package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.Modified;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class OrgXmlSax {
    public static final String PACKAGE_NAME = "org.xml.sax";

    interface XMLFilter$ {
        @Modified
        void setParent(XMLReader parent);

        XMLReader getParent();
    }

    interface ContentHandler$ {
        @Modified
        void startDocument();

        @Modified
        void endElement (String uri, String localName, String qName);
    }
}
