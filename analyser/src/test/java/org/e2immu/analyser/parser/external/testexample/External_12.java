package org.e2immu.analyser.parser.external.testexample;

import org.e2immu.annotation.Modified;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

public class External_12 extends XMLFilterImpl {

    @Modified
    private final StringBuilder sb = new StringBuilder();

    @Modified
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (!sb.isEmpty()) {
            int l = sb.length();
            int s = 0;
            boolean b = false;
            for (; ((l > s) && (sb.charAt(s) <= ' ' || sb.charAt(s) == 'x')); s++) {
                b = b || sb.charAt(s) == 'x';
            }
            sb.delete(0, sb.length());
        }
        super.endElement(uri, localName, qName);
    }
}

