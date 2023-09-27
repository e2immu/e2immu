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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.context.impl.EvaluationResultImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LocalVariableCreation extends BaseExpression implements Expression {
    /*
    the huge majority of instances will have a single declaration, consisting of an LVC identifier, a
    local variable reference (with assignment expression) and its associated identifier.
     */
    public final Identifier lvrIdentifier;
    public final LocalVariableReference localVariableReference;

    /*
    but occasionally we encounter more declarations, or a var-args.
     */
    public final List<Declaration> moreDeclarations;
    public final boolean isVar;

    public boolean hasSingleDeclaration() {
        return moreDeclarations.isEmpty();
    }

    public record Declaration(Identifier identifier, LocalVariableReference localVariableReference)
            implements Comparable<Declaration> {
        public Declaration(Identifier identifier, LocalVariable localVariable, Expression expression) {
            this(identifier, new LocalVariableReference(localVariable, expression));
        }

        public LocalVariableReference localVariableReference() {
            return localVariableReference;
        }

        public Declaration translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
            LocalVariableReference translated = translateLvr(localVariableReference, inspectionProvider, translationMap);
            if (translated == localVariableReference) return this;
            return new Declaration(identifier, translated);
        }

        @Override
        public int compareTo(Declaration o) {
            return localVariableReference.compareTo(o.localVariableReference);
        }
    }

    public LocalVariableCreation(Identifier identifier,
                                 Identifier lvrIdentifier,
                                 LocalVariableReference localVariableReference,
                                 List<Declaration> moreDeclarations,
                                 boolean isVar) {
        super(identifier, localVariableReference.assignmentExpression.getComplexity() + moreDeclarations.
                stream().mapToInt(d -> d.localVariableReference.assignmentExpression.getComplexity()).sum());
        this.lvrIdentifier = lvrIdentifier;
        this.localVariableReference = localVariableReference;
        this.moreDeclarations = moreDeclarations;
        this.isVar = isVar;
    }

    public LocalVariableCreation(Identifier identifier,
                                 Identifier lvrIdentifier,
                                 LocalVariableReference localVariableReference) {
        this(identifier, lvrIdentifier, localVariableReference, List.of(), false);
    }

    public LocalVariableCreation(Identifier identifier, Identifier lvrIdentifier, LocalVariable localVariable) {
        this(identifier, lvrIdentifier,
                new LocalVariableReference(localVariable, EmptyExpression.EMPTY_EXPRESSION), List.of(), false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariableCreation that = (LocalVariableCreation) o;
        return localVariableReference.equals(that.localVariableReference)
                && moreDeclarations.equals(that.moreDeclarations)
                && isVar == that.isVar;
    }

    @Override
    public int hashCode() {
        return Objects.hash(localVariableReference, moreDeclarations, isVar);
    }

    public static LocalVariableReference translateLvr(LocalVariableReference lvr,
                                                      InspectionProvider inspectionProvider,
                                                      TranslationMap translationMap) {
        LocalVariable tlv = translationMap.translateLocalVariable(lvr.variable);
        Expression tex = lvr.assignmentExpression.isEmpty() ? lvr.assignmentExpression
                : lvr.assignmentExpression.translate(inspectionProvider, translationMap);
        if (tlv == lvr.variable && tex == lvr.assignmentExpression) return lvr;
        if (lvr.variable.parameterizedType().isBoxedExcludingVoid()
                && lvr.assignmentExpression.isNullConstant()
                && tlv.parameterizedType().isPrimitiveExcludingVoid()
                && tex.isNullConstant()) {
            // special case: Integer v = null, with type change to int v = null; ... change null to 0
            Expression nullValue = ConstantExpression.nullValue(inspectionProvider.getPrimitives(),
                    lvr.assignmentExpression.getIdentifier(),
                    tlv.parameterizedType().typeInfo);
            return new LocalVariableReference(tlv, nullValue);
        }
        return new LocalVariableReference(tlv, tex);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;
        LocalVariableReference tLvr = translateLvr(localVariableReference, inspectionProvider, translationMap);
        List<Declaration> translatedDeclarations = moreDeclarations.stream()
                .map(d -> d.translate(inspectionProvider, translationMap))
                .collect(TranslationCollectors.toList(moreDeclarations));
        if (tLvr == localVariableReference && translatedDeclarations == moreDeclarations) return this;
        return new LocalVariableCreation(identifier, lvrIdentifier, tLvr, translatedDeclarations, isVar);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_LOCAL_VAR_CREATION;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        if (v instanceof LocalVariableCreation lvc) {
            int c = localVariableReference.compareTo(lvc.localVariableReference);
            if (c != 0) return c;
            return ListUtil.compare(moreDeclarations, lvc.moreDeclarations);
        }
        throw new ExpressionComparator.InternalError();
    }


    @Override
    public ParameterizedType returnType() {
        return ParameterizedType.TYPE_OF_EMPTY_EXPRESSION;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        LocalVariable lv0 = localVariableReference.variable;
        // annotations
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(lv0.annotations().stream()
                        .map(ae -> ae.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA)));
        if (!outputBuilder.isEmpty()) {
            outputBuilder.add(Space.ONE);
        }

        // modifiers
        OutputBuilder mods = new OutputBuilder()
                .add(lv0.modifiers().stream()
                        .map(s -> new OutputBuilder().add(s.keyword))
                        .collect(OutputBuilder.joining(Space.ONE)));
        if (!mods.isEmpty()) {
            mods.add(Space.ONE);
        }
        outputBuilder.add(mods);

        // var or type
        if (isVar) {
            outputBuilder.add(Keyword.VAR);
        } else {
            outputBuilder.add(lv0.parameterizedType().output(qualification));
        }

        // declarations
        outputBuilder.add(Space.ONE);
        OutputBuilder first = new OutputBuilder().add(new Text(localVariableReference.simpleName()));
        if (!localVariableReference.assignmentExpression.isEmpty()) {
            first.add(Symbol.assignment("=")).add(localVariableReference.assignmentExpression.output(qualification));
        }
        Stream<OutputBuilder> rest = moreDeclarations.stream().map(d -> {
            OutputBuilder ob = new OutputBuilder().add(new Text(d.localVariableReference.simpleName()));
            if (!d.localVariableReference.assignmentExpression.isEmpty()) {
                ob.add(Symbol.assignment("="))
                        .add(d.localVariableReference.assignmentExpression.output(qualification));
            }
            return ob;
        });
        outputBuilder.add(Stream.concat(Stream.of(first), rest).collect(OutputBuilder.joining(Symbol.COMMA)));
        return outputBuilder;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        Stream<Map.Entry<TypeInfo, Boolean>> s1 = localVariableReference.assignmentExpression.typesReferenced().stream();
        Stream<Map.Entry<TypeInfo, Boolean>> s2 = localVariableReference.parameterizedType.typesReferenced(true).stream();
        Stream<Map.Entry<TypeInfo, Boolean>> s3 = moreDeclarations.stream()
                .flatMap(d -> d.localVariableReference.assignmentExpression.typesReferenced().stream());
        Stream<Map.Entry<TypeInfo, Boolean>> s4 = moreDeclarations.stream()
                .flatMap(d -> d.localVariableReference.parameterizedType().typesReferenced(true).stream());
        return Stream.concat(Stream.concat(s1, s2), Stream.concat(s3, s4)).collect(UpgradableBooleanMap.collector());
    }

    @Override
    public List<? extends Element> subElements() {
        Stream<Element> s1 = localVariableReference.assignmentExpression.isEmpty() ? Stream.of()
                : Stream.of(localVariableReference.assignmentExpression);
        Stream<Element> s2 = moreDeclarations.stream().map(d -> (Element) d.localVariableReference.assignmentExpression)
                .filter(e -> e != EmptyExpression.EMPTY_EXPRESSION);
        return Stream.concat(s1, s2).toList();
    }

    @Override
    public List<LocalVariableReference> newLocalVariables() {
        return Stream.concat(Stream.of(localVariableReference),
                moreDeclarations.stream().map(Declaration::localVariableReference)).toList();
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.isOnlySort()) {
            LocalVariableReference first;
            if (localVariableReference.assignmentExpression.isEmpty()) {
                first = localVariableReference;
            } else {
                Expression evaluated = localVariableReference.assignmentExpression
                        .evaluate(context, forwardEvaluationInfo).getExpression();
                first = new LocalVariableReference(localVariableReference.variable,
                        evaluated);
            }
            List<Declaration> evaluatedDeclarations = moreDeclarations.stream()
                    .map(d -> d.localVariableReference.assignmentExpression.isEmpty() ? d :
                            new Declaration(d.identifier, d.localVariableReference.variable,
                                    d.localVariableReference.assignmentExpression
                                            .evaluate(context, forwardEvaluationInfo).getExpression()))
                    .toList();
            LocalVariableCreation lvc = new LocalVariableCreation(identifier, lvrIdentifier, first,
                    evaluatedDeclarations, isVar);
            return new EvaluationResultImpl.Builder(context).setExpression(lvc).build();
        }


        EvaluationResult result;
        if (localVariableReference.assignmentExpression.isEmpty()) {
            result = new EvaluationResultImpl.Builder(context).setExpression(localVariableReference.assignmentExpression).build();
        } else {
            Assignment assignment = new Assignment(context.getPrimitives(),
                    new VariableExpression(lvrIdentifier, localVariableReference),
                    localVariableReference.assignmentExpression);
            result = assignment.evaluate(context, forwardEvaluationInfo);
        }
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context).compose(result);
        for (Declaration declaration : moreDeclarations) {
            EvaluationResult assigned;
            if (declaration.localVariableReference.assignmentExpression.isEmpty()) {
                assigned = new EvaluationResultImpl.Builder(context)
                        .setExpression(declaration.localVariableReference.assignmentExpression)
                        .build();
            } else {
                Assignment assignment = new Assignment(context.getPrimitives(),
                        new VariableExpression(declaration.identifier, declaration.localVariableReference),
                        declaration.localVariableReference.assignmentExpression);
                assigned = assignment.evaluate(context, forwardEvaluationInfo);
            }
            builder.compose(assigned);
        }
        assert builder != null || result != null;
        return builder != null ? builder.build() : result;
    }

    @Override
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
        Stream<Variable> s1 = Stream.concat(Stream.of(localVariableReference),
                localVariableReference.assignmentExpression.variables(descendIntoFieldReferences).stream());
        Stream<Variable> s2 = moreDeclarations.stream()
                .flatMap(d -> Stream.concat(Stream.of(d.localVariableReference()),
                        d.localVariableReference.assignmentExpression.variables(descendIntoFieldReferences).stream()));
        return Stream.concat(s1, s2).toList();
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            localVariableReference.assignmentExpression.visit(predicate);
            for (Declaration declaration : moreDeclarations) {
                declaration.localVariableReference.assignmentExpression.visit(predicate);
            }
        }
    }
}
