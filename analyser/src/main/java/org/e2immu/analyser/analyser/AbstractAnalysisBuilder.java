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

import org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.log;

public abstract class AbstractAnalysisBuilder implements Analysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAnalysisBuilder.class);

    public final SetOnceMap<AnnotationExpression, Boolean> annotations = new SetOnceMap<>();
    public final SetOnceMap<AnnotationExpression, AnnotationCheck> annotationChecks = new SetOnceMap<>();

    public final VariableProperties properties = new VariableProperties();
    public final String simpleName; // for debugging purposes
    public final Primitives primitives;

    protected AbstractAnalysisBuilder(Primitives primitives, String simpleName) {
        this.simpleName = simpleName;
        this.primitives = primitives;
    }

    public DV getPropertyFromMapDelayWhenAbsent(VariableProperty variableProperty) {
        DV v = properties.getOrDefaultNull(variableProperty);
        if (v == null) return new CausesOfDelay.SimpleSet(location(), variableProperty.causeOfDelay());
        return v;
    }

    public DV getPropertyFromMapNeverDelay(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, variableProperty.valueWhenAbsent());
    }

    public void setProperty(VariableProperty variableProperty, DV i) {
        if (!properties.isDone(variableProperty)) {
            if (i.isDone()) properties.put(variableProperty, i);
        } else {
            DV current = properties.getOrDefaultNull(variableProperty);
            if (i.value() != current.value()) {
                throw new UnsupportedOperationException("Trying to overwrite property " + variableProperty + " with value " + i + ", current value " + current);
            }
        }
    }

    @Override
    public Stream<Map.Entry<AnnotationExpression, AnnotationCheck>> getAnnotationStream() {
        return annotationChecks.stream();
    }

    @Override
    public AnnotationCheck getAnnotation(AnnotationExpression annotationExpression) {
        if (!annotationChecks.isSet(annotationExpression)) {
            throw new UnsupportedOperationException("Cannot find annotation " + annotationExpression.output(Qualification.EMPTY));
        }
        return annotationChecks.get(annotationExpression);
    }

    protected void doNotNull(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions, DV notNull) {
        // not null
        if (notNull.ge(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV)) {
            annotations.put(e2ImmuAnnotationExpressions.notNull1, true);
            annotations.put(e2ImmuAnnotationExpressions.nullable, false);
        } else {
            if (notNull.isDone()) {
                annotations.put(e2ImmuAnnotationExpressions.notNull1, false);
            }
            if (notNull.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
                annotations.put(e2ImmuAnnotationExpressions.notNull, true);
            } else if (notNull.isDone()) {
                annotations.put(e2ImmuAnnotationExpressions.notNull, false);
            }
            // a delay on notNull0 on a non-primitive will get nullable present
            annotations.put(e2ImmuAnnotationExpressions.nullable, notNull.equals(MultiLevel.NULLABLE_DV));
        }
    }

    protected void doImmutableContainer(E2ImmuAnnotationExpressions e2, DV immutable, boolean betterThanFormal) {
        DV container = getProperty(VariableProperty.CONTAINER);
        String eventualFieldNames;
        boolean isType = this instanceof TypeAnalysis;
        boolean isInterface = isType && ((TypeAnalysisImpl.Builder) this).typeInfo.isInterface();
        boolean eventual = isType && ((TypeAnalysis) this).isEventual();
        if (eventual) {
            eventualFieldNames = ((TypeAnalysis) this).markLabel();
        } else {
            eventualFieldNames = "";
        }
        Map<Class<?>, Map<String, Object>> map = GenerateAnnotationsImmutable.generate(immutable, container,
                isType, isInterface, eventualFieldNames, betterThanFormal);
        for (Map.Entry<Class<?>, Map<String, Object>> entry : map.entrySet()) {
            List<Expression> list;
            if (entry.getValue() == GenerateAnnotationsImmutable.TRUE) {
                list = List.of();
            } else {
                list = entry.getValue().entrySet().stream().map(e -> new MemberValuePair(e.getKey(),
                        ConstantExpression.create(primitives, e.getValue()))).collect(Collectors.toList());
            }
            AnnotationExpression expression = new AnnotationExpressionImpl(e2.immutableAnnotation(entry.getKey()), list);
            annotations.put(expression, true);
        }
    }

    protected void doIndependent(E2ImmuAnnotationExpressions e2, DV independent, DV formallyIndependent, DV immutable) {
        AnnotationExpression expression;

        if (independent.equals(formallyIndependent)) {
            // no annotation needed
            return;
        }
        if (MultiLevel.isAtLeastE2Immutable(immutable)) {
            return; // no annotation needed, @Immutable series will be there
        }
        if (independent.equals(MultiLevel.DEPENDENT_DV)) {
            return; // default value
        }
        if (independent.equals(MultiLevel.INDEPENDENT_DV)) {
            expression = e2.independent;
        } else if (independent.equals(MultiLevel.INDEPENDENT_1_DV)) {
            expression = e2.independent1;
        } else {
            int level = MultiLevel.level(independent) + 1;
            expression = new AnnotationExpressionImpl(e2.independent1.typeInfo(),
                    List.of(new MemberValuePair("level", new IntConstant(primitives, level))));
        }
        annotations.put(expression, true);
    }

    /**
     * Copy contracted annotations into properties.
     *
     * @param analyserIdentification      which analyser is calling? some small choices to make
     * @param acceptVerifyAsContracted    accept annotations with AnnotationMode.VERIFY as contracted. This is e.g.
     *                                    the case for methods that have AnalyserMode.CONTRACTED
     * @param annotations                 the annotations to copy
     * @param e2ImmuAnnotationExpressions the full list of e2immu annotations
     * @return error or warning messages
     */
    public Messages fromAnnotationsIntoProperties(
            Analyser.AnalyserIdentification analyserIdentification,
            boolean acceptVerifyAsContracted,
            Collection<AnnotationExpression> annotations,
            E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        MultiLevel.Level levelImmutable = MultiLevel.Level.ABSENT;
        DV notNull = null;
        boolean container = false;
        MultiLevel.Level levelIndependent = MultiLevel.Level.ABSENT;
        Messages messages = new Messages();

        AnnotationExpression only = null;
        AnnotationExpression mark = null;
        AnnotationExpression testMark = null;

        VariableProperty modified = analyserIdentification == Analyser.AnalyserIdentification.FIELD ||
                analyserIdentification == Analyser.AnalyserIdentification.PARAMETER ? VariableProperty.MODIFIED_VARIABLE
                : VariableProperty.MODIFIED_METHOD;

        for (AnnotationExpression annotationExpression : annotations) {
            AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
            if (parameters != null && (parameters.contract()
                    || acceptVerifyAsContracted && !parameters.absent()

                    // exception: @Container on a parameter is always restrictive/contracted
                    || analyserIdentification == Analyser.AnalyserIdentification.PARAMETER &&
                    e2ImmuAnnotationExpressions.container.typeInfo() == annotationExpression.typeInfo())) {
                DV trueFalse = parameters.absent() ? Level.FALSE_DV : Level.TRUE_DV;
                DV falseTrue = !parameters.absent() ? Level.FALSE_DV : Level.TRUE_DV;

                TypeInfo t = annotationExpression.typeInfo();
                if (e2ImmuAnnotationExpressions.e1Immutable.typeInfo() == t) {
                    levelImmutable = MultiLevel.Level.IMMUTABLE_1.max(levelImmutable);
                } else if (e2ImmuAnnotationExpressions.mutableModifiesArguments.typeInfo() == t) {
                    levelImmutable = MultiLevel.Level.ABSENT;
                    container = false;
                } else if (e2ImmuAnnotationExpressions.e2Immutable.typeInfo() == t) {
                    levelImmutable = MultiLevel.Level.IMMUTABLE_2.max(levelImmutable);
                    levelIndependent = MultiLevel.Level.INDEPENDENT_1.max(levelIndependent);
                } else if (e2ImmuAnnotationExpressions.e2Container.typeInfo() == t) {
                    levelImmutable = MultiLevel.Level.IMMUTABLE_2.max(levelImmutable);
                    levelIndependent = MultiLevel.Level.INDEPENDENT_1.max(levelIndependent);
                    container = true;
                } else if (e2ImmuAnnotationExpressions.e1Container.typeInfo() == t) {
                    levelImmutable = MultiLevel.Level.IMMUTABLE_1.max(levelImmutable);
                    container = true;
                } else if (e2ImmuAnnotationExpressions.eRContainer.typeInfo() == t) {
                    levelImmutable = MultiLevel.Level.IMMUTABLE_R;
                    levelIndependent = MultiLevel.Level.INDEPENDENT_R;
                    container = true;
                } else if (e2ImmuAnnotationExpressions.beforeMark.typeInfo() == t) {
                    if (parameters.contract()) setProperty(VariableProperty.IMMUTABLE_BEFORE_CONTRACTED, trueFalse);
                } else if (e2ImmuAnnotationExpressions.container.typeInfo() == t) {
                    container = true;
                } else if (e2ImmuAnnotationExpressions.nullable.typeInfo() == t) {
                    notNull = MultiLevel.NULLABLE_DV;
                } else if (e2ImmuAnnotationExpressions.notNull.typeInfo() == t) {
                    notNull = MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                } else if (e2ImmuAnnotationExpressions.notNull1.typeInfo() == t) {
                    notNull = MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV;
                } else if (e2ImmuAnnotationExpressions.notModified.typeInfo() == t) {
                    setProperty(modified, falseTrue);
                } else if (e2ImmuAnnotationExpressions.modified.typeInfo() == t) {
                    setProperty(modified, trueFalse);
                } else if (e2ImmuAnnotationExpressions.effectivelyFinal.typeInfo() == t) {
                    setProperty(VariableProperty.FINAL, trueFalse);
                } else if (e2ImmuAnnotationExpressions.variableField.typeInfo() == t) {
                    setProperty(VariableProperty.FINAL, falseTrue);
                } else if (e2ImmuAnnotationExpressions.constant.typeInfo() == t) {
                    setProperty(VariableProperty.CONSTANT, trueFalse);
                } else if (e2ImmuAnnotationExpressions.extensionClass.typeInfo() == t) {
                    setProperty(VariableProperty.EXTENSION_CLASS, trueFalse);
                } else if (e2ImmuAnnotationExpressions.fluent.typeInfo() == t) {
                    setProperty(VariableProperty.FLUENT, trueFalse);
                } else if (e2ImmuAnnotationExpressions.finalizer.typeInfo() == t) {
                    setProperty(VariableProperty.FINALIZER, trueFalse);
                } else if (e2ImmuAnnotationExpressions.identity.typeInfo() == t) {
                    setProperty(VariableProperty.IDENTITY, trueFalse);
                } else if (e2ImmuAnnotationExpressions.ignoreModifications.typeInfo() == t) {
                    setProperty(VariableProperty.IGNORE_MODIFICATIONS, trueFalse);
                } else if (e2ImmuAnnotationExpressions.independent.typeInfo() == t) {
                    levelIndependent = MultiLevel.Level.INDEPENDENT_R;
                } else if (e2ImmuAnnotationExpressions.dependent.typeInfo() == t) {
                    setProperty(VariableProperty.INDEPENDENT, MultiLevel.DEPENDENT_DV);
                } else if (e2ImmuAnnotationExpressions.independent1.typeInfo() == t) {
                    levelIndependent = MultiLevel.Level.INDEPENDENT_1;
                } else if (e2ImmuAnnotationExpressions.mark.typeInfo() == t) {
                    mark = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.testMark.typeInfo() == t) {
                    testMark = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.only.typeInfo() == t) {
                    only = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.singleton.typeInfo() == t) {
                    setProperty(VariableProperty.SINGLETON, trueFalse);
                } else if (e2ImmuAnnotationExpressions.utilityClass.typeInfo() == t) {
                    setProperty(VariableProperty.UTILITY_CLASS, trueFalse);
                    levelImmutable = MultiLevel.Level.IMMUTABLE_2.max(levelImmutable);
                    levelIndependent = MultiLevel.Level.INDEPENDENT_1.max(levelIndependent);
                } else if (e2ImmuAnnotationExpressions.linked.typeInfo() == t) {
                    log(org.e2immu.analyser.util.Logger.LogTarget.ANALYSER, "Ignoring informative annotation @Linked");
                } else if (e2ImmuAnnotationExpressions.linked1.typeInfo() == t) {
                    log(org.e2immu.analyser.util.Logger.LogTarget.ANALYSER, "Ignoring informative annotation @Linked1");
                } else if (e2ImmuAnnotationExpressions.allowsInterrupt.typeInfo() != t) {
                    // @AllowsInterrupt caught earlier on in the code, can be ignored here
                    throw new UnsupportedOperationException("? " + t.fullyQualifiedName);
                }
            }
        }
        if (levelIndependent != MultiLevel.Level.ABSENT) {
            DV value = MultiLevel.composeIndependent(MultiLevel.Effective.EFFECTIVE, levelIndependent);
            setProperty(VariableProperty.INDEPENDENT, value);
        }
        if (container) {
            setProperty(VariableProperty.CONTAINER, Level.TRUE_DV);
            if (levelImmutable == MultiLevel.Level.ABSENT) {
                setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE_DV);
            }
        }
        if (levelImmutable != MultiLevel.Level.ABSENT) {
            DV value = MultiLevel.effectivelyImmutable(levelImmutable.level);
            setProperty(VariableProperty.IMMUTABLE, value);
        }
        if (notNull != null) {
            setProperty(analyserIdentification.notNull, notNull);
        }
        if (mark != null && only == null) {
            String markValue = mark.extract("value", "");
            writeEventual(markValue, true, null, null);
        } else if (only != null) {
            String markValue = mark == null ? null : mark.extract("value", "");
            String before = only.extract("before", "");
            String after = only.extract("after", "");
            boolean isAfter = before.isEmpty();
            String onlyMark = isAfter ? after : before;
            if (markValue != null && !onlyMark.equals(markValue)) {
                LOGGER.warn("Have both @Only and @Mark, with different values? {} vs {}", onlyMark, markValue);
            }
            if (markValue == null) {
                LOGGER.warn("No mark value on {}", location());
            } else {
                writeEventual(markValue, false, isAfter, null);
            }
        } else if (testMark != null) {
            String markValue = testMark.extract("value", "");
            boolean before = testMark.extract("before", false);
            writeEventual(markValue, false, null, before);
        }
        return messages;
    }

    protected void writeEventual(String value, boolean mark, Boolean isAfter, Boolean test) {
        throw new UnsupportedOperationException();
    }
}
