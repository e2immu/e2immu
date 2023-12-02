package org.e2immu.analyser.parser.external.testexample;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.helpers.XMLFilterImpl;

// difference with External_1: directly uses filter, rather than an intermediary variable 'f'

public class External_2 extends XMLFilterImpl {

    private Document document;

    public External_2(Result result) {
    }

    public static void method(XMLFilter filter, ProcessElement process) throws SAXException {
        final ContentHandler h = filter.getContentHandler();
        final ProcessElement p = process;
        External_2 ext = new External_2(new Result() {
            @Override
            public void report(External_2 e) throws SAXException {
                filter.setContentHandler(h);
                p.process(e.document.getDocumentElement(), filter);
            }
        });
    }

    public interface ProcessElement {
        void process(Element element, XMLFilter filter) throws SAXException;
    }

    public interface Result {
        void report(External_2 ext) throws SAXException;
    }

    public void setDocument(Document document) {
        this.document = document;
    }
}
