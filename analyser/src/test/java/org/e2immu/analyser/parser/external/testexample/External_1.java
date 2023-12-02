package org.e2immu.analyser.parser.external.testexample;

import org.e2immu.annotation.Independent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;


public class External_1 extends XMLFilterImpl {

    private Document document;

    public External_1(Result result) {
    }

    public static void method(XMLFilter filter, ProcessElement process) throws SAXException {
        final XMLFilter f = filter;
        final ContentHandler h = filter.getContentHandler();
        final ProcessElement p = process;
        External_1 ext = new External_1(new Result() {
            @Override
            public void report(External_1 e) throws SAXException {
                f.setContentHandler(h);
                p.process(e.document.getDocumentElement(), f);
            }
        });
    }

    @Independent
    public interface ProcessElement {
        void process(Element element, XMLFilter filter) throws SAXException;
    }

    @Independent
    public interface Result {
        void report(External_1 ext) throws SAXException;
    }

    public void setDocument(Document document) {
        this.document = document;
    }
}
