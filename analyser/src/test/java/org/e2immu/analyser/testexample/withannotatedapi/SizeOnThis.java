package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Size;

import java.util.HashSet;
import java.util.Set;

/**
 * the goal of this example is to show that the @Size system can work on `this`,
 * and that is has no knowledge at all on the details of how size is computed.
 *
 */
public class SizeOnThis {

    private final Set<String> strings = new HashSet<>();

    @Size
    @NotModified
    public int size() {
        return strings.size();
    }

    @Size(equals = 0)
    @NotModified
    public boolean isEmpty() {
        return strings.isEmpty();
    }

    // annotation is about the object
    @Size(equals = 0)
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    private void clear() {
        strings.clear();
    }

    // this annotation is about the object, not the return value
    @Size(min = 1)
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    public boolean add(String a) {
        return strings.add(a);
    }

    // this annotation is about the object, not the return value (even though in this particular case,
    // they could be the same.)
    @Size(min = 1)
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    public int method1(String s) {
        clear();
        if (isEmpty()) { // ERROR, constant evaluation
            System.out.println("Should always be printed");
        }
        if (strings.isEmpty()) { // not an error, what do we know about strings? we know about `this`
            System.out.println("Should always be printed");
        }
        this.add(s);
        if(isEmpty()) { // ERROR, constant evaluation
            System.out.println("Will never be printed");
        }
        return size();
    }

    // again the annotation is about the object
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    @Size(min = 1)
    public void method2() {
        int n = method1("a");
        if(n >= 1) { // ERROR constant evaluation
            System.out.println("Should always be printed");
        }
    }
}
