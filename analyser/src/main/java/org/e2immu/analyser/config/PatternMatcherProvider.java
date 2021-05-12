package org.e2immu.analyser.config;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.HasNavigationData;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.TypeAndInspectionProvider;
import org.e2immu.analyser.pattern.PatternMatcher;

@FunctionalInterface
public interface PatternMatcherProvider<T extends HasNavigationData<T>> {
    PatternMatcher<T> newPatternMatcher(TypeAndInspectionProvider typeContext);
}
