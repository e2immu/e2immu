package org.e2immu.analyser.parser.external.testexample;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.UndeclaredThrowableException;

public class External_6 extends XMLFilterImpl {
    private static final DocumentBuilder documentBuilder = createDocumentBuilder();

    private Node currentNode;
    private Document document;

    private static DocumentBuilder createDocumentBuilder() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        if (document == null) {
            try {
                document = documentBuilder.newDocument();
            } catch (Exception e) {
                throw new SAXException(e);
            }
        } else {
            if (document.getDocumentElement() != null) {
                document.removeChild(document.getDocumentElement());
            }
        }
        currentNode = document;
    }

    public Node getCurrentNode() {
        return currentNode;
    }
}
