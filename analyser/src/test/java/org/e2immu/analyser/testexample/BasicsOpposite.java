package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Collection;

@ModifiesArguments
@Mutable
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

    public void add(@Modified Collection<String> collection) {
        collection.add(string);
    }
}
