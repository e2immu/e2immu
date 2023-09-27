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

package org.e2immu.analyser.analyser.impl.shallow;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.FieldAnalyserImpl;
import org.e2immu.analyser.analyser.util.AnalyserComponents;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.ValueAndPropertyProxy;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.parser.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ShallowFieldAnalyser extends FieldAnalyserImpl implements FieldAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowFieldAnalyser.class);


    public ShallowFieldAnalyser(FieldInfo fieldInfo, TypeInfo primaryType,
                                TypeAnalysis ownerTypeAnalysis,
                                AnalyserContext analyserContext) {
        super(analyserContext, primaryType, ownerTypeAnalysis, fieldInfo);
    }

    @Override
    public void initialize() {

    }

    @Override
    public AnalyserResult analyse(SharedState sharedState) {
        assert !isUnreachable();
        LOGGER.debug("Analysing field {}", fqn);
        try {
            mainAnalyser();
            fieldAnalysis.internalAllDoneCheck();
            analyserResultBuilder.setAnalysisStatus(AnalysisStatus.DONE);
            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in field analyser: {}", fieldInfo.fullyQualifiedName);
            throw rte;
        }
    }

    @Override
    public void write() {

    }

    @Override
    public void check() {

    }

    private void mainAnalyser() {
        analyserResultBuilder.addMessages(fieldAnalysis
                .fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.FIELD, true,
                        fieldInfo.fieldInspection.get().getAnnotations(),
                        analyserContext.getE2ImmuAnnotationExpressions()));

        FieldInspection fieldInspection = analyserContext.getFieldInspection(fieldInfo);
        boolean typeIsEnum = analyserContext.getTypeInspection(fieldInfo.owner).isEnum();

        boolean enumField = typeIsEnum && fieldInspection.isSynthetic();

        if (!fieldAnalysis.properties.isDone(Property.FINAL)) {
            // the following code is here to save some @Final annotations in annotated APIs where there already is a `final` keyword.
            fieldAnalysis.setProperty(Property.FINAL, DV.fromBoolDv(fieldInfo.isExplicitlyFinal() || enumField));
        }

        // unless annotated with something heavier, ...
        DV notNull;
        if (!fieldAnalysis.properties.isDone(Property.EXTERNAL_NOT_NULL)) {
            notNull = enumField ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : AnalysisProvider.defaultNotNull(fieldInfo.type);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, notNull);
        } else {
            notNull = fieldAnalysis.getPropertyFromMapNeverDelay(Property.EXTERNAL_NOT_NULL);
        }

        DV typeIsContainer;
        if (!fieldAnalysis.properties.isDone(Property.CONTAINER)) {
            if (fieldAnalysis.bestType == null) {
                typeIsContainer = MultiLevel.CONTAINER_DV;
            } else {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(fieldAnalysis.bestType);
                if (typeAnalysis != null) {
                    typeIsContainer = typeAnalysis.getProperty(Property.CONTAINER);
                } else {
                    typeIsContainer = Property.CONTAINER.falseDv;
                    if (fieldInfo.fieldInspection.get().isPublic()) {
                        Message m = Message.newMessage(fieldInfo.newLocation(), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                                fieldAnalysis.bestType.fullyQualifiedName);
                        analyserResultBuilder.add(m);
                    }
                }
            }
            fieldAnalysis.setProperty(Property.CONTAINER, typeIsContainer);
        } else {
            typeIsContainer = fieldAnalysis.properties.getOrDefaultNull(Property.CONTAINER);
        }
        if (!fieldAnalysis.properties.isDone(Property.CONTAINER_RESTRICTION)) {
            fieldAnalysis.setProperty(Property.CONTAINER_RESTRICTION, Property.CONTAINER_RESTRICTION.falseDv);
        }

        DV annotatedImmutable = fieldAnalysis.getPropertyFromMapDelayWhenAbsent(Property.IMMUTABLE);
        DV formallyImmutable = analyserContext.typeImmutable(fieldInfo.type);
        DV immutable = MultiLevel.MUTABLE_DV.maxIgnoreDelay(annotatedImmutable.maxIgnoreDelay(formallyImmutable));
        fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, immutable);
        DV annotatedIndependent = fieldAnalysis.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        DV formallyIndependent = analyserContext.typeIndependent(fieldInfo.type);
        DV independent = MultiLevel.DEPENDENT_DV.maxIgnoreDelay(annotatedIndependent.maxIgnoreDelay(formallyIndependent));
        DV ignoreMods = fieldAnalysis.getPropertyFromMapNeverDelay(Property.IGNORE_MODIFICATIONS);

        Properties properties = Properties.of(Map.of(Property.NOT_NULL_EXPRESSION, notNull,
                Property.IMMUTABLE, immutable, Property.INDEPENDENT, independent, Property.CONTAINER, typeIsContainer,
                Property.IGNORE_MODIFICATIONS, ignoreMods, Property.IDENTITY, DV.FALSE_DV));

        DV constant;
        Expression value;
        if (fieldAnalysis.getProperty(Property.FINAL).valueIsTrue()
                && fieldInspection.fieldInitialiserIsSet()) {
            Expression initialiser = fieldInspection.getFieldInitialiser().initialiser().unwrapIfConstant();
            if (initialiser.isConstant()) {
                value = initialiser;
                constant = DV.TRUE_DV;
            } else {
                value = Instance.forField(fieldInfo, initialiser.returnType(), properties);
                constant = DV.FALSE_DV;
            }
        } else {
            value = Instance.forField(fieldInfo, null, properties);
            constant = DV.FALSE_DV;
        }
        fieldAnalysis.setInitialiserValue(value);
        fieldAnalysis.setProperty(Property.CONSTANT, constant);
        fieldAnalysis.setValue(value);
        ValueAndPropertyProxy vap = new ValueAndPropertyProxy.ProxyData(value, properties, LinkedVariables.EMPTY,
                ValueAndPropertyProxy.Origin.INITIALISER);
        fieldAnalysis.setValues(List.of(vap), CausesOfDelay.EMPTY);
        fieldAnalysis.setLinkedVariables(LinkedVariables.EMPTY);
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return fieldInfo;
    }

    @Override
    public Analysis getAnalysis() {
        return fieldAnalysis;
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return null;
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return fqn;
    }
}
