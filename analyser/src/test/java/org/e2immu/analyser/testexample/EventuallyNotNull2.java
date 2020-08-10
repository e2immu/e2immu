package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * A type can be @NotNull1 when all its publicly exposed data (return value of methods, fields) are @NotNull1
 * Such a type must in theory not be @E1Immutable (non-exposed fields can change status).
 *
 * In most cases a @NotNull1 type will be @E2Immutable.
 *
 */

@E1Container
public class EventuallyNotNull2 {

    // this @E1Container becomes an eventually @E2Container when we restrict reInitialize to be called exactly once
    @E1Container
    static class EventuallyNotNull1Container {
        @NotNull1(after = "assigned")
        private final String[] strings;

        public EventuallyNotNull1Container(int n) {
            strings = new String[n];
        }

        // NOTE: List is @NotNull1 because we say so in the annotated APIs
        @Modified
        @Mark("assigned")
        public void reInitialize(@NotNull1 @NotModified List<String> source) {
            int i = 0;
            while (i < strings.length) {
                for (String s : source) {
                    if (i >= strings.length) break;
                    strings[i++] = s;
                }
            }
        }

        @NotNull1
        public Stream<String> getStrings() {
            return Arrays.stream(strings);
        }
    }

    @E1Container
    @NotNull2(after = "assigned") // the @NotNull and @NotNull1 are effective, @NotNull2 eventual
    public final EventuallyNotNull1Container[] containers;

    public EventuallyNotNull2(int n, int m) {
        containers = new EventuallyNotNull1Container[m];
        for (int i = 0; i < m; i++) {
            containers[i] = new EventuallyNotNull1Container(n);
        }
    }

    /*
    The precondition ensures completeness
     */
    public void initialize1(List<List<String>> input) {
        if (input.size() < containers.length) throw new UnsupportedOperationException();
        int i = 0;
        for (List<String> list : input) {
            containers[i++].reInitialize(list);
        }
    }
}
