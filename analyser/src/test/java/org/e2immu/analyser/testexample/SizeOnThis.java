package org.e2immu.analyser.testexample;

import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

/**
 * the goal of this example is to show that the @Size system can work on `this`,
 * and that is has no knowledge at all on the details of how size is computed.
 *
 */
public class SizeOnThis {

    private final Set<String> strings = new HashSet<>();

    void size$Aspect$Size() {}
    @NotModified
    public int size() {
        return strings.size();
    }

    boolean isEmpty$Value$Size(int size) { return size == 0; }
    @NotModified
    public boolean isEmpty() {
        return strings.isEmpty();
    }

    // annotation is about the object
    boolean clear$Modification$Size(int post, int pre) { return post == 0; }
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    private void clear() {
        strings.clear();
    }

    // this annotation is about the object, not the return value
    boolean add$Modification$Size(int post, int pre) { return pre == 0 ? post == 1: post >= pre && post <= pre+1; }
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    public boolean add(String a) {
        return strings.add(a);
    }

    // this annotation is about the object, not the return value (even though in this particular case,
    // they could be the same.)
    boolean method1$Modification$Size(int post, int pre) { return post >= 1; }
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

    boolean method2$Modification$Size(int post, int pre) { return post >= 1; }
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    public void method2() {
        int n = method1("a");
        if(n >= 1) { // ERROR constant evaluation
            System.out.println("Should always be printed");
        }
    }
}
