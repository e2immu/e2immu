package org.e2immu.annotation;

public enum AnnotationType {
    /**
     * annotation present in the code, added by hand,
     * to ensure that an error is raised in case the code analyser
     * fails to compute this annotation.
     *
     * This is the default for annotations in Java classes.
     */
    VERIFY,

    /**
     * annotation present in the code, added by hand, to ensure
     * that an error is raised in case the code analyser would
     * compute this annotation.
     *
     * Cannot be used on interfaces.
     */
    VERIFY_ABSENT,

    /**
     * an annotation produced by the code analyser; visible only on code
     * produced by the code analyser
     */
    COMPUTED,

    /**
     * The default value for annotated_api files and interfaces: not computed
     * but set by hand. Also the default for @Once, @Mark, and @Output
     */
    CONTRACT,
}
