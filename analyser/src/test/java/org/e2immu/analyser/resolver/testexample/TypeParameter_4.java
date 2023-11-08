package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;

public class TypeParameter_4 {
    static class QName implements Serializable {
        String localPart;
        String getLocalPart() {
            return localPart;
        }
    }
    static class JAXBElement<T> implements Serializable {
        T value;
        QName name;
        public T getValue() {
            return value;
        }
        public QName getName() { return name; }
    }

    public String method(Object object, String name) {
        if (object instanceof JAXBElement && name.equalsIgnoreCase(((JAXBElement<?>) object).getName().getLocalPart())) {
            return ((JAXBElement<?>) object).getValue().toString();
        }
        return "?";
    }
}
