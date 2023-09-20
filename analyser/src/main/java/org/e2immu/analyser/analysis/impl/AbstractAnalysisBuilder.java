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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutableAndContainer;
import org.e2immu.analyser.analyser.util.GenerateAnnotationsIndependent;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.*;

abstract class AbstractAnalysisBuilder implements Analysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAnalysisBuilder.class);

    /*
    always add key+value the same, so that we can get the expression with parameters using a key that doesn't have them
     */
    public final SetOnceMap<AnnotationExpression, AnnotationExpression> annotations = new SetOnceMap<>();
    public final SetOnceMap<AnnotationExpression, AnnotationCheck> annotationChecks = new SetOnceMap<>();

    public final Properties properties = Properties.writable();
    public final String simpleName; // for debugging purposes
    public final Primitives primitives;

    protected AbstractAnalysisBuilder(Primitives primitives, String simpleName) {
        this.simpleName = simpleName;
        this.primitives = primitives;
    }

    @Override
    public void putAnnotationCheck(AnnotationExpression expression, AnnotationCheck missing) {
        annotationChecks.put(expression, missing);
    }

    @Override
    public AnnotationExpression annotationGetOrDefaultNull(AnnotationExpression expression) {
        return annotations.getOrDefaultNull(expression);
    }

    public DV getPropertyFromMapDelayWhenAbsent(Property property) {
        DV v = properties.getOrDefaultNull(property);
        if (v == null) return DelayFactory.createDelay(location(Stage.INITIAL), property.causeOfDelay());
        return v;
    }

    public DV getPropertyFromMapNeverDelay(Property property) {
        DV v = properties.getOrDefaultNull(property);
        return v == null || v.isDelayed() ? property.falseDv : v;
    }

    public void setProperty(Property property, DV i) {
        properties.put(property, i);
    }

    @Override
    public void setPropertyDelayWhenNotFinal(Property property, CausesOfDelay causes) {
        DV dv = properties.getOrDefaultNull(property);
        if (dv == null || dv.isDelayed()) {
            properties.put(property, causes);
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

    public void addAnnotation(AnnotationExpression annotationExpression) {
        this.annotations.put(annotationExpression, annotationExpression);
    }

    protected void doNotNull(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions, DV notNull, boolean isPrimitive) {
        if (isPrimitive) {
            AnnotationExpression nullable = E2ImmuAnnotationExpressions.create(primitives, NotNull.class, IMPLIED, true);
            addAnnotation(nullable);
            return;
        }
        // content not null
        if (notNull.ge(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV)) {
            AnnotationExpression nn = E2ImmuAnnotationExpressions.create(primitives, NotNull.class, CONTENT, true);
            addAnnotation(nn);
            return;
        }
        if (notNull.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
            addAnnotation(e2ImmuAnnotationExpressions.notNull);
            return;
        }
        if (notNull.isDone()) {
            AnnotationExpression nullable = E2ImmuAnnotationExpressions.create(primitives, Nullable.class, IMPLIED, true);
            addAnnotation(nullable);
            return;
        }
        // a delay
        AnnotationExpression nullable = E2ImmuAnnotationExpressions.create(primitives, Nullable.class, INCONCLUSIVE, true);
        addAnnotation(nullable);
    }

    /*
    Convention:
    - we show the immutability value, adding "implied=true" when not better than formal
    - when eventual, we show @BeforeMark for the before state, after="" for the eventual state (but only
      if better than formal), and no extra info for the after state.
     */

    protected void doImmutableContainer(E2ImmuAnnotationExpressions e2,
                                        DV immutable,
                                        DV container,
                                        boolean immutableBetterThanFormal,
                                        boolean containerBetterThanFormal,
                                        String constantValue,
                                        boolean constantImplied) {
        String eventualFieldNames;
        boolean isType = this instanceof TypeAnalysis;
        boolean showFieldNames = isType && ((TypeAnalysis) this).isEventual()
                || immutable.isDone() && MultiLevel.effective(immutable) == MultiLevel.Effective.EVENTUAL;
        if (showFieldNames) {
            eventualFieldNames = markLabelFromType();
        } else {
            eventualFieldNames = "";
        }
        Map<Class<?>, Map<String, Object>> map = GenerateAnnotationsImmutableAndContainer.generate(immutable, container,
                isType, eventualFieldNames, immutableBetterThanFormal, containerBetterThanFormal, constantValue,
                constantImplied);
        generate(e2, map);
    }

    private boolean generate(E2ImmuAnnotationExpressions e2, Map<Class<?>, Map<String, Object>> map) {
        boolean added = false;
        for (Map.Entry<Class<?>, Map<String, Object>> entry : map.entrySet()) {
            TypeInfo typeInfo = e2.immutableAnnotation(entry.getKey());
            AnnotationExpression expression = AnnotationExpressionImpl.from(primitives, typeInfo, entry.getValue());
            addAnnotation(expression);
            added = true;
        }
        return added;
    }

    protected boolean doIndependent(E2ImmuAnnotationExpressions e2, DV independent, DV formallyIndependent, DV immutable) {
        boolean implied = independent.equals(formallyIndependent)
                || MultiLevel.independentCorrespondsToImmutable(independent, immutable);
        Map<Class<?>, Map<String, Object>> map = GenerateAnnotationsIndependent.map(independent, implied);
        return generate(e2, map);
    }

    private static final int[] INT_ARRAY = {};

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
        Messages messages = new Messages();
        DV notNull = null;
        boolean container = false;
        DV independent = DV.MIN_INT_DV;
        DV linkLevel = null;
        int[] linkHcParameters = null;

        // immutable values
        MultiLevel.Level levelImmutable = MultiLevel.Level.ABSENT;
        AnnotationExpression only = null;
        AnnotationExpression mark = null;
        AnnotationExpression testMark = null;
        String eventual = null;
        boolean isConstant = false;

        Property modified = analyserIdentification == Analyser.AnalyserIdentification.FIELD ||
                analyserIdentification == Analyser.AnalyserIdentification.PARAMETER ? Property.MODIFIED_VARIABLE
                : Property.MODIFIED_METHOD;

        for (AnnotationExpression annotationExpression : annotations) {
            AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
            if (parameters != null && (parameters.contract() || acceptVerifyAsContracted)) {
                DV trueFalse = parameters.absent() ? DV.FALSE_DV : DV.TRUE_DV;
                DV falseTrue = !parameters.absent() ? DV.FALSE_DV : DV.TRUE_DV;

                TypeInfo t = annotationExpression.typeInfo();
                boolean isImmutableContainer = e2ImmuAnnotationExpressions.immutableContainer.typeInfo() == t;
                boolean isImmutable = isImmutableContainer || e2ImmuAnnotationExpressions.immutable.typeInfo() == t;

                if (e2ImmuAnnotationExpressions.finalFields.typeInfo() == t) {
                    // @FinalFields
                    levelImmutable = parameters.absent() ? MultiLevel.Level.ABSENT
                            : MultiLevel.Level.MUTABLE.max(levelImmutable);
                    eventual = isEventual(annotationExpression);

                } else if (isImmutable) {
                    // @Immutable
                    boolean hiddenContent = analyserIdentification.isAbstract ||
                            annotationExpression.extract(HIDDEN_CONTENT, false);
                    MultiLevel.Level immutable = hiddenContent ? MultiLevel.Level.IMMUTABLE_HC : MultiLevel.Level.IMMUTABLE;
                    levelImmutable = parameters.absent() ? MultiLevel.Level.ABSENT : levelImmutable.max(immutable);
                    DV independentHc = hiddenContent ? MultiLevel.INDEPENDENT_HC_DV : MultiLevel.INDEPENDENT_DV;
                    independent = parameters.absent() ? MultiLevel.DEPENDENT_DV : independent.maxIgnoreDelay(independentHc);
                    eventual = isEventual(annotationExpression);
                    String constantValue = annotationExpression.extract(VALUE, "");
                    isConstant = constantValue == null || !constantValue.isEmpty();
                    container = isImmutableContainer;

                } else if (e2ImmuAnnotationExpressions.independent.typeInfo() == t) {
                    // @Independent
                    boolean hc = annotationExpression.extract(HIDDEN_CONTENT, false);
                    independent = parameters.absent() ? MultiLevel.DEPENDENT_DV :
                            hc ? MultiLevel.INDEPENDENT_HC_DV : MultiLevel.INDEPENDENT_DV;
                    if (analyserIdentification == Analyser.AnalyserIdentification.PARAMETER) {
                        linkHcParameters = annotationExpression.extract(HC_PARAMETERS, INT_ARRAY);
                    }
                } else if (e2ImmuAnnotationExpressions.container.typeInfo() == t) {
                    // @Container, allowing absent
                    container = !parameters.absent();
                } else if (e2ImmuAnnotationExpressions.nullable.typeInfo() == t) {
                    // @Nullable, IGNORING absent
                    notNull = MultiLevel.NULLABLE_DV;
                } else if (e2ImmuAnnotationExpressions.notNull.typeInfo() == t) {
                    // @NotNull, allowing absent
                    boolean content = annotationExpression.extract(CONTENT, false);
                    notNull = parameters.absent() ? MultiLevel.NULLABLE_DV :
                            content ? MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV : MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                } else if (e2ImmuAnnotationExpressions.notModified.typeInfo() == t) {
                    // @NotModified
                    setProperty(modified, falseTrue);
                } else if (e2ImmuAnnotationExpressions.modified.typeInfo() == t) {
                    // @Modified
                    setProperty(modified, trueFalse);
                } else if (e2ImmuAnnotationExpressions.effectivelyFinal.typeInfo() == t) {
                    // @Final
                    setProperty(Property.FINAL, trueFalse);
                } else if (e2ImmuAnnotationExpressions.fluent.typeInfo() == t) {
                    // @Fluent
                    setProperty(Property.FLUENT, trueFalse);
                } else if (e2ImmuAnnotationExpressions.getSet.typeInfo() == t) {
                    // @GetSet
                    getSet(annotationExpression.extract(VALUE, null));
                } else if (e2ImmuAnnotationExpressions.identity.typeInfo() == t) {
                    // @Identity
                    setProperty(Property.IDENTITY, trueFalse);
                } else if (e2ImmuAnnotationExpressions.commutable.typeInfo() == t) {
                    addCommutable(annotationExpression);

                    // EVENTUAL ANNOTATIONS
                } else if (e2ImmuAnnotationExpressions.beforeMark.typeInfo() == t) {
                    // @BeforeMark
                    if (parameters.contract()) setProperty(Property.IMMUTABLE_BEFORE_CONTRACTED, trueFalse);
                } else if (e2ImmuAnnotationExpressions.mark.typeInfo() == t) {
                    // @Mark
                    mark = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.testMark.typeInfo() == t) {
                    // @TestMark
                    testMark = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.only.typeInfo() == t) {
                    // @Only
                    only = annotationExpression;

                    // TYPE ANNOTATIONS
                } else if (e2ImmuAnnotationExpressions.singleton.typeInfo() == t) {
                    // @Singleton
                    setProperty(Property.SINGLETON, trueFalse);
                } else if (e2ImmuAnnotationExpressions.utilityClass.typeInfo() == t) {
                    // @UtilityClass
                    setProperty(Property.UTILITY_CLASS, trueFalse);
                    levelImmutable = MultiLevel.Level.IMMUTABLE;
                    independent = MultiLevel.INDEPENDENT_DV;
                } else if (e2ImmuAnnotationExpressions.extensionClass.typeInfo() == t) {
                    // @ExtensionClass
                    setProperty(Property.EXTENSION_CLASS, trueFalse);

                    // RARE ANNOTATIONS
                } else if (e2ImmuAnnotationExpressions.staticSideEffects.typeInfo() == t) {
                    // @StaticSideEffects
                    setProperty(Property.STATIC_SIDE_EFFECTS, trueFalse);
                } else if (e2ImmuAnnotationExpressions.ignoreModifications.typeInfo() == t) {
                    // @IgnoreModifications
                    DV trueFalseMulti = parameters.absent() ? MultiLevel.NOT_IGNORE_MODS_DV : MultiLevel.IGNORE_MODS_DV;
                    setProperty(analyserIdentification.ignoreMods, trueFalseMulti);
                } else if (e2ImmuAnnotationExpressions.finalizer.typeInfo() == t) {
                    // @Finalizer
                    setProperty(Property.FINALIZER, trueFalse);
                } else if (e2ImmuAnnotationExpressions.allowsInterrupt.typeInfo() != t) {
                    // @AllowsInterrupt caught earlier on in the code, can be ignored here
                    throw new UnsupportedOperationException("? not implemented: " + t.fullyQualifiedName);
                }
            }
        }
        if (independent != DV.MIN_INT_DV) {
            setProperty(Property.INDEPENDENT, independent);
        }
        if (container) {
            setProperty(analyserIdentification.container, MultiLevel.CONTAINER_DV);
            if (levelImmutable == MultiLevel.Level.ABSENT) {
                setProperty(analyserIdentification.immutable, MultiLevel.MUTABLE_DV);
            }
        }
        if (levelImmutable != MultiLevel.Level.ABSENT) {
            DV value = eventual != null ? MultiLevel.eventuallyImmutable(levelImmutable.level)
                    : MultiLevel.effectivelyImmutable(MultiLevel.Effective.EFFECTIVE, levelImmutable.level);
            setProperty(analyserIdentification.immutable, value);
            if (eventual != null) {
                writeTypeEventualFields(eventual);
            }
            if (isConstant) {
                setProperty(Property.CONSTANT, DV.TRUE_DV);
            }
        }
        if (notNull != null) {
            setProperty(analyserIdentification.notNull, notNull);
        }
        if (mark != null && only == null) {
            String markValue = mark.extract(VALUE, "");
            writeEventual(markValue, true, null, null);
        } else if (only != null) {
            String before = only.extract(BEFORE, "");
            String after = only.extract(AFTER, "");
            boolean isAfter = before.isEmpty();
            String onlyMark = isAfter ? after : before;
            String markValue = mark == null ? null : mark.extract(VALUE, "");
            if (markValue != null && !onlyMark.equals(markValue)) {
                LOGGER.warn("Have both @Only and @Mark, with different values? {} vs {}", onlyMark, markValue);
            }
            if (onlyMark == null) {
                LOGGER.warn("No mark value on {}", location(Stage.INITIAL));
            } else {
                writeEventual(onlyMark, false, isAfter, null);
            }
        } else if (testMark != null) {
            String markValue = testMark.extract(VALUE, "");
            boolean before = testMark.extract(BEFORE, false);
            boolean test = !before; // default == after==true == before==false
            writeEventual(markValue, false, null, test);
        }
        if (linkHcParameters != null) {
            writeLinkParameters(LinkedVariables.LINK_COMMON_HC, linkHcParameters);
        }
        return messages;
    }

    protected void getSet(String fieldName) {
        throw new UnsupportedOperationException();
    }

    private void addCommutable(AnnotationExpression annotationExpression) {
        String par = annotationExpression.extract(PAR, "");
        String seq = annotationExpression.extract(SEQ, "");
        String multi = annotationExpression.extract(MULTI, "");
        addCommutable(new CommutableData(par, seq, multi));
    }

    protected void addCommutable(CommutableData commutableData) {
        throw new UnsupportedOperationException();
    }

    protected void writeLinkParameters(DV linkLevel, int[] linkParameters) {
        throw new UnsupportedOperationException();
    }

    private String isEventual(AnnotationExpression annotationExpression) {
        String after = annotationExpression.extract(AFTER, "");
        return after == null || after.isBlank() ? null : after.trim();
    }

    protected void writeEventual(String value, boolean mark, Boolean isAfter, Boolean test) {
        throw new UnsupportedOperationException();
    }

    protected void writeTypeEventualFields(String after) {
        throw new UnsupportedOperationException("Not implemented in " + getClass());
    }

    @Override
    public void internalAllDoneCheck() {
        ensureNoDelayedPropertyValues();
    }

    public void ensureNoDelayedPropertyValues() {
        assert properties.delays().isDone() : "Property map: " + properties;
    }

    public void setPropertyIfAbsentOrDelayed(Property property, DV finalValue) {
        DV dv = properties.getOrDefaultNull(property);
        if (dv == null || dv.isDelayed()) properties.put(property, finalValue);
    }
}
