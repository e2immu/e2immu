package org.e2immu.analyser.objectflow;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.util.Either;

public class Location {
    public final Either<MethodInfo, FieldInfo> location;

    public Location(MethodInfo methodInfo) {
        location = Either.left(methodInfo);
    }

    public Location(FieldInfo fieldInfo) {
        location = Either.right(fieldInfo);
    }
}
