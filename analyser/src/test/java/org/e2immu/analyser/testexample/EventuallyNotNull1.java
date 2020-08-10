package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.List;

/**
 * conclusion from this example:
 * <p>
 * The eventual system with @Only @Mark should work in the sense that there is only one modifying method
 *
 * We introduce @NotNull on @Container objects to indicate results, parameters and fields all at the same time
 *
 */

@E1Container
@NotNull1(after = "assigned")
public class EventuallyNotNull1 {

    @NotNull1 // effectively not null eventually content not null, but we cannot mark before or after
    public final String[] strings;

    public EventuallyNotNull1(int n) {
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
}
