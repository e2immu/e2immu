/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.check.CheckImmutable;
import org.e2immu.analyser.analyser.check.CheckIndependent;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.*;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

/**
 * In the type analysis record we state whether this type has "free fields" or not.
 * Nested types will be allowed in two forms:
 * (1) non-private nested types, where (a) all non-private fields must be @E1Immutable,
 * and (b) access to private methods and fields from enclosing to nested and nested to enclosing is restricted
 * to reading fields and calling @NotModified methods in a direct hierarchical line
 * (2) private subtypes, which do not need to satisfy (1a), and which have the one additional freedom compared to (1b) that
 * the enclosing type can access private fields and methods at will as long as the types are in hierarchical line
 * <p>
 * The analyse and check methods are called independently for types and nested types, in an order of dependence determined
 * by the resolver, but guaranteed such that a nested type will always come before its enclosing type.
 * <p>
 * Therefore, at the end of an enclosing type's analysis, we should have decisions on @NotModified of the methods of the
 * enclosing type, and it should be possible to establish whether a nested type only reads fields (does NOT assign) and
 * calls @NotModified private methods.
 * <p>
 * Errors related to those constraints are added to the type making the violation.
 */

public abstract class TypeAnalyser extends AbstractAnalyser {
    public final TypeInfo primaryType;
    public final TypeInfo typeInfo;
    public final TypeInspection typeInspection;
    public final TypeAnalysisImpl.Builder typeAnalysis;
    protected final Messages messages = new Messages();

    public TypeAnalyser(@NotModified TypeInfo typeInfo,
                        TypeInfo primaryType,
                        AnalyserContext analyserContextInput,
                        Analysis.AnalysisMode analysisMode) {
        super("Type " + typeInfo.simpleName, new ExpandableAnalyserContextImpl(analyserContextInput));
        this.typeInfo = typeInfo;
        this.primaryType = primaryType;
        typeInspection = typeInfo.typeInspection.get();

        typeAnalysis = new TypeAnalysisImpl.Builder(analysisMode, analyserContext.getPrimitives(), typeInfo,
                analyserContext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeAnalyser that = (TypeAnalyser) o;
        return typeInfo.equals(that.typeInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo);
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return typeInfo;
    }

    @Override
    public Analysis getAnalysis() {
        return typeAnalysis;
    }

    @Override
    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    public abstract boolean ignorePrivateConstructorsForFieldValue();

    @Override
    public void check() {
        if (typeInfo.typePropertiesAreContracted()) return;

        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        // before we check, we copy the properties into annotations
        log(ANALYSER, "\n******\nAnnotation validation on type {}\n******", typeInfo.fullyQualifiedName);

        check(typeInfo, UtilityClass.class, e2.utilityClass);
        check(typeInfo, ExtensionClass.class, e2.extensionClass);
        check(typeInfo, Container.class, e2.container);
        check(typeInfo, Singleton.class, e2.singleton);

        check(typeInfo, Independent.class, e2.independent);
        check(typeInfo, Dependent.class, e2.dependent);
        CheckIndependent.checkLevel(messages, typeInfo, Independent1.class, e2.independent1, typeAnalysis);

        check(typeInfo, MutableModifiesArguments.class, e2.mutableModifiesArguments);
        CheckImmutable.check(messages, typeInfo, E1Immutable.class, e2.e1Immutable, typeAnalysis, true, false, false);
        CheckImmutable.check(messages, typeInfo, E1Container.class, e2.e1Container, typeAnalysis, true, false, false);
        CheckImmutable.check(messages, typeInfo, E2Immutable.class, e2.e2Immutable, typeAnalysis, true, true, true);
        CheckImmutable.check(messages, typeInfo, E2Container.class, e2.e2Container, typeAnalysis, true, true, false);
        CheckImmutable.check(messages, typeInfo, ERContainer.class, e2.eRContainer, typeAnalysis, true, false, false);

        checkWorseThanSpecifiedInInterfacesImplemented();
    }


    private static final Set<VariableProperty> CHECK_WORSE_THAN_INTERFACES_IMPLEMENTED = Set.of(VariableProperty.IMMUTABLE,
            VariableProperty.INDEPENDENT, VariableProperty.CONTAINER);

    private void checkWorseThanSpecifiedInInterfacesImplemented() {
        for (VariableProperty variableProperty : CHECK_WORSE_THAN_INTERFACES_IMPLEMENTED) {
            int valueFromOverrides = typeAnalysis.maxValueFromInterfacesImplemented(analyserContext, variableProperty);
            int value = typeAnalysis.getProperty(variableProperty);
            if (valueFromOverrides != Level.DELAY && value != Level.DELAY) {
                boolean complain = value < valueFromOverrides;
                if (complain) {
                    messages.add(Message.newMessage(new Location(typeInfo),
                            Message.Label.WORSE_THAN_IMPLEMENTED_INTERFACE, variableProperty.name));
                }
            }
        }
    }


    private void check(TypeInfo typeInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        typeInfo.error(typeAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(typeInfo),
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    @Override
    public void write() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        typeAnalysis.transferPropertiesToAnnotations(e2);
    }

    @Override
    protected String where(String componentName) {
        return typeInfo.fullyQualifiedName + ":" + componentName;
    }

}
