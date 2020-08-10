package org.e2immu.intellij.highlighter;

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Set;

/*

e2i.settings.colors.attr.independent-m=@Independent method
e2i.settings.colors.attr.-m=Unannotated method

e2i.settings.colors.attr.modified-f=@Modified field
e2i.settings.colors.attr.e2immutable-f=@E2Immutable field
e2i.settings.colors.attr.e2container-f=@E2Container field
e2i.settings.colors.attr.final-f=@Final field
e2i.settings.colors.attr.-f=Unannotated field


 */
// E2Immutable (not a container!)
public class DemoClass {

    private int count; // count = variable field
    // Set, HashSet = container, String = E2Container; strings = modified
    private final Set<String> strings = new HashSet<>();

    // input = non-modified parameter, count = not annotated parameter
    // ImmutableSet = E2Container, Integer = not annotated type
    public DemoClass(ImmutableSet<Integer> input, int count) {
        this.count = count;
        // add = modifying method, toString = nonModifying method; Integer = unannotated type
        input.forEach(i -> strings.add(Integer.toString(i)));
    }

    // destination = modified parameter; addTo = not modified+independent method
    public void addTo(Set<String> destination) {
        destination.addAll(strings);
    }

    // modifying method
    public void setCount(int count) {
        this.count = count;
    }
}
