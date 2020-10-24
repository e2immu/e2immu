package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Collection;

@MutableModifiesArguments
public class BasicsOpposite {

    @Variable
    @Nullable
    @Modified(type = AnnotationType.VERIFY_ABSENT)
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
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

    @Nullable(type = AnnotationType.VERIFY_ABSENT)
    @NotNull(type = AnnotationType.VERIFY_ABSENT)
    @NotModified
    public void add(@Modified @NotNull Collection<String> collection) {
        collection.add(string);
    }
}
