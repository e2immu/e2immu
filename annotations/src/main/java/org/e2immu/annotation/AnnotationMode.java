package org.e2immu.annotation;

public enum AnnotationMode {
    DEFENSIVE, // @NotModified, @NotNull, @Independent, @Container, @Final

    OFFENSIVE, // @Modified, @Nullable, @Dependent, @ModifiesArguments, @Variable
}
