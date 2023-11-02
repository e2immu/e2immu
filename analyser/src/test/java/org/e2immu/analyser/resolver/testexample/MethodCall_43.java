package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.a.CanBeSerialized;
import org.e2immu.analyser.resolver.testexample.a.Serializer;

public class MethodCall_43<E extends CanBeSerialized> extends Serializer<E> {

    public int method() {
        int sum = 0;
        for (int i = 0; i < element.list().size(); i++) {
            sum += element.isX() ? 1 : 0;
        }
        return sum;
    }
}
