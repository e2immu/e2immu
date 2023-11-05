package org.e2immu.analyser.resolver.testexample.a;

public abstract class Serializer<X> {
    protected X element;

    @Override
    public String toString() {
        return element.toString(); // there are no type bounds here
    }
}
