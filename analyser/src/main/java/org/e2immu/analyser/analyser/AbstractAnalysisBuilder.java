/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ContractMark;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public int getPropertyAsIs(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public int internalGetProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public void setProperty(VariableProperty variableProperty, int i) {
        if (!properties.isSet(variableProperty)) {
            if (i != Level.DELAY) properties.put(variableProperty, i);
        } else {
            int current = properties.get(variableProperty);
            if (i != current) {
                throw new UnsupportedOperationException("Trying to overwrite property " + variableProperty + " with value " + i + ", current value " + current);
            }
        }
    }

    public void incrementProperty(VariableProperty variableProperty, int i) {
        if (!properties.isSet(variableProperty)) {
            if (i != Level.DELAY) properties.put(variableProperty, i);
        } else {
            int current = properties.get(variableProperty);
            if (i != current) {
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

    public abstract void transferPropertiesToAnnotations(AnalysisProvider analysisProvider,
                                                         E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions);

    protected void doNotNull(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions, int notNull) {

        // not null
        if (notNull >= MultiLevel.EVENTUALLY_CONTENT2_NOT_NULL) {
            annotations.put(e2ImmuAnnotationExpressions.notNull2, true);
            annotations.put(e2ImmuAnnotationExpressions.nullable, false);
        } else {
            if (notNull != Level.DELAY) annotations.put(e2ImmuAnnotationExpressions.notNull2, false);

            if (notNull >= MultiLevel.EVENTUALLY_CONTENT_NOT_NULL) {
                annotations.put(e2ImmuAnnotationExpressions.notNull1, true);
                annotations.put(e2ImmuAnnotationExpressions.nullable, false);
            } else {
                if (notNull > Level.DELAY) {
                    annotations.put(e2ImmuAnnotationExpressions.notNull1, false);
                }
                if (notNull >= MultiLevel.EVENTUAL) {
                    annotations.put(e2ImmuAnnotationExpressions.notNull, true);
                } else if (notNull > Level.DELAY) {
                    annotations.put(e2ImmuAnnotationExpressions.notNull, false);
                }

                boolean nullablePresent = notNull < MultiLevel.EVENTUAL;
                // a delay on notNull0 on a non-primitive will get nullable present
                annotations.put(e2ImmuAnnotationExpressions.nullable, nullablePresent);
            }
        }
    }

    protected void doNotModified1(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        // @NotModified1
        annotations.put(e2ImmuAnnotationExpressions.notModified1, getProperty(VariableProperty.NOT_MODIFIED_1) == Level.TRUE);
    }

    protected void doImmutableContainer(E2ImmuAnnotationExpressions e2, int immutable, boolean betterThanFormal) {
        int container = getProperty(VariableProperty.CONTAINER);
        String mark;
        boolean isType = this instanceof TypeAnalysis;
        boolean isInterface = isType && ((TypeAnalysisImpl.Builder) this).typeInfo.isInterface();
        boolean eventual = isType && ((TypeAnalysis) this).isEventual();
        if (eventual) {
            mark = ((TypeAnalysis) this).allLabelsRequiredForImmutable();
        } else mark = "";
        Map<Class<?>, Map<String, String>> map = GenerateAnnotationsImmutable.generate(immutable, container, isType, isInterface,
                mark, betterThanFormal);
        for (Map.Entry<Class<?>, Map<String, String>> entry : map.entrySet()) {
            List<Expression> list;
            if (entry.getValue() == GenerateAnnotationsImmutable.TRUE) {
                list = List.of();
            } else {
                list = entry.getValue().entrySet().stream().map(e -> new MemberValuePair(e.getKey(),
                        new StringConstant(primitives, e.getValue()))).collect(Collectors.toList());
            }
            AnnotationExpression expression = new AnnotationExpressionImpl(e2.immutableAnnotation(entry.getKey()), list);
            annotations.put(expression, true);
        }
    }

    protected void doIndependent(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions, int independent, boolean isInterface) {
        if (independent == MultiLevel.FALSE || !isInterface && independent == MultiLevel.DELAY) {
            annotations.put(e2ImmuAnnotationExpressions.independent, false);
            annotations.put(e2ImmuAnnotationExpressions.dependent, true);
            return;
        }
        if (independent <= MultiLevel.FALSE) return;
        annotations.put(e2ImmuAnnotationExpressions.dependent, false);
        if (independent == MultiLevel.EFFECTIVE) {
            annotations.put(e2ImmuAnnotationExpressions.independent, true);
            return;
        }
        boolean eventual = this instanceof TypeAnalysis && ((TypeAnalysis) this).isEventual();
        if (!eventual) throw new UnsupportedOperationException("??");
        String mark = ((TypeAnalysis) this).allLabelsRequiredForImmutable();
        AnnotationExpression ae = new AnnotationExpressionImpl(e2ImmuAnnotationExpressions.independent.typeInfo(),
                List.of(new MemberValuePair("after", new StringConstant(primitives, mark))));
        annotations.put(ae, true);
    }

    public Messages fromAnnotationsIntoProperties(
            boolean isVariable,
            boolean acceptVerify,
            Collection<AnnotationExpression> annotations,
            E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        int immutable = -1;
        int notNull = -1;
        boolean container = false;
        Messages messages = new Messages();

        AnnotationExpression only = null;
        AnnotationExpression mark = null;

        VariableProperty modified = isVariable ? VariableProperty.MODIFIED_VARIABLE : VariableProperty.MODIFIED_METHOD;

        for (AnnotationExpression annotationExpression : annotations) {
            AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
            if (parameters != null && (parameters.contract() || acceptVerify && !parameters.absent())) {
                int trueFalse = parameters.absent() ? Level.FALSE : Level.TRUE;
                int falseTrue = !parameters.absent() ? Level.FALSE : Level.TRUE;

                TypeInfo t = annotationExpression.typeInfo();
                if (e2ImmuAnnotationExpressions.e1Immutable.typeInfo() == t) {
                    immutable = Math.max(0, immutable);
                } else if (e2ImmuAnnotationExpressions.mutableModifiesArguments.typeInfo() == t) {
                    immutable = -1;
                    container = false;
                } else if (e2ImmuAnnotationExpressions.e2Immutable.typeInfo() == t) {
                    immutable = 1;
                } else if (e2ImmuAnnotationExpressions.e2Container.typeInfo() == t) {
                    immutable = 1;
                    container = true;
                } else if (e2ImmuAnnotationExpressions.e1Container.typeInfo() == t) {
                    immutable = Math.max(0, immutable);
                    container = true;
                } else if (e2ImmuAnnotationExpressions.container.typeInfo() == t) {
                    container = true;
                } else if (e2ImmuAnnotationExpressions.nullable.typeInfo() == t) {
                    notNull = MultiLevel.NULLABLE;
                } else if (e2ImmuAnnotationExpressions.notNull.typeInfo() == t) {
                    notNull = MultiLevel.EFFECTIVELY_NOT_NULL;
                } else if (e2ImmuAnnotationExpressions.notNull1.typeInfo() == t) {
                    notNull = MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                } else if (e2ImmuAnnotationExpressions.notNull2.typeInfo() == t) {
                    notNull = MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL;
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
                } else if (e2ImmuAnnotationExpressions.identity.typeInfo() == t) {
                    setProperty(VariableProperty.IDENTITY, trueFalse);
                } else if (e2ImmuAnnotationExpressions.ignoreModifications.typeInfo() == t) {
                    setProperty(VariableProperty.IGNORE_MODIFICATIONS, trueFalse);
                } else if (e2ImmuAnnotationExpressions.independent.typeInfo() == t) {
                    setProperty(VariableProperty.INDEPENDENT, MultiLevel.EFFECTIVE);
                } else if (e2ImmuAnnotationExpressions.dependent.typeInfo() == t) {
                    setProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                } else if (e2ImmuAnnotationExpressions.mark.typeInfo() == t) {
                    mark = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.only.typeInfo() == t) {
                    only = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.singleton.typeInfo() == t) {
                    setProperty(VariableProperty.SINGLETON, trueFalse);
                } else if (e2ImmuAnnotationExpressions.utilityClass.typeInfo() == t) {
                    setProperty(VariableProperty.UTILITY_CLASS, trueFalse);
                } else if (e2ImmuAnnotationExpressions.linked.typeInfo() == t) {
                    setProperty(VariableProperty.LINKED, trueFalse);
                } else if (e2ImmuAnnotationExpressions.notModified1.typeInfo() == t) {
                    setProperty(VariableProperty.NOT_MODIFIED_1, trueFalse);
                } else if (e2ImmuAnnotationExpressions.allowsInterrupt.typeInfo() != t) {
                    throw new UnsupportedOperationException("TODO: " + t.fullyQualifiedName);
                }
            }
        }
        if (container) {
            setProperty(VariableProperty.CONTAINER, Level.TRUE);
        }
        if (immutable >= 0) {
            int value = switch (immutable) {
                case 0 -> MultiLevel.EFFECTIVELY_E1IMMUTABLE;
                case 1 -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                default -> throw new UnsupportedOperationException();
            };
            setProperty(VariableProperty.IMMUTABLE, value);
        }
        if (notNull >= 0) {
            VariableProperty property = isVariable ? VariableProperty.NOT_NULL_VARIABLE : VariableProperty.NOT_NULL_EXPRESSION;
            setProperty(property, notNull);
        }
        if (mark != null && only == null) {
            String markValue = mark.extract("value", "");
            List<Expression> values = safeSplit(markValue).stream().map(ContractMark::new).collect(Collectors.toList());
            ((MethodAnalysisImpl.Builder) this).writeMarkAndOnly(new MethodAnalysis.MarkAndOnly(values, markValue, true, null));
        } else if (only != null) {
            String markValue = mark == null ? null : mark.extract("value", "");
            String before = only.extract("before", "");
            String after = only.extract("after", "");
            boolean isAfter = before.isEmpty();
            String onlyMark = isAfter ? after : before;
            if (markValue != null && !onlyMark.equals(markValue)) {
                LOGGER.warn("Have both @Only and @Mark, with different values? {} vs {}", onlyMark, markValue);
            }
            List<Expression> values = safeSplit(onlyMark).stream().map(ContractMark::new).collect(Collectors.toList());
            ((MethodAnalysisImpl.Builder) this).writeMarkAndOnly(new MethodAnalysis.MarkAndOnly(values, onlyMark, mark != null, isAfter));
        }
        return messages;
    }

    private static List<String> safeSplit(String s) {
        String[] ss = s.split(",\\s*");
        return Arrays.stream(ss).filter(l -> l.trim().isEmpty()).collect(Collectors.toList());
    }

    public Map<VariableProperty, Integer> getProperties(Set<VariableProperty> properties) {
        Map<VariableProperty, Integer> res = new HashMap<>();
        for (VariableProperty property : properties) {
            int value = getProperty(property);
            res.put(property, value);
        }
        return res;
    }

    @Override
    public boolean isBeingAnalysed() {
        return true;
    }
}
