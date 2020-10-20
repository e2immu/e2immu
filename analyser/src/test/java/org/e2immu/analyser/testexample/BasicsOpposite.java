package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Collection;

@MutableModifiesArguments
public class BasicsOpposite {

    @Variable
    @Nullable
    private String string;

    @Nullable
    public String getString() {
        return string;
    }

    @Modified
    public void setString(@Nullable String string) {
        this.string = string;
    }

    @Nullable(type = AnnotationType.VERIFY_ABSENT)
    public void add(@Modified @NotNull Collection<String> collection) {
        collection.add(string);
    }
}
