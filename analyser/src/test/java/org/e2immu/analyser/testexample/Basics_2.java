package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Collection;

@MutableModifiesArguments
public class Basics_2 {

    @Variable
    @Nullable
    @Modified(absent = true)
    @NotModified(absent = true)
    private String string;

    @Nullable
    @NotModified
    public String getString() {
        return string;
    }

    @Modified
    public void setString(@Nullable String string) {
        this.string = string;
    }

    @Nullable(absent = true)
    @NotNull(absent = true)
    @NotModified
    public void add(@Modified @NotNull Collection<String> collection) {
        collection.add(string); // expect potential null pointer exception here
    }
}
