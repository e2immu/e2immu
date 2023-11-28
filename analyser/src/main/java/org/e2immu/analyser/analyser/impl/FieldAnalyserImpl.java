package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.FieldAnalyser;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.FieldInspection;
import org.e2immu.analyser.model.TypeInfo;

public abstract class FieldAnalyserImpl extends AbstractAnalyser implements FieldAnalyser {

    protected final TypeInfo primaryType;
    protected final FieldInfo fieldInfo;
    protected final String fqn; // of fieldInfo, saves a lot of typing
    protected final FieldInspection fieldInspection;
    protected final FieldAnalysisImpl.Builder fieldAnalysis;

    public FieldAnalyserImpl(AnalyserContext analyserContext, TypeAnalysis ownerTypeAnalysis, FieldInfo fieldInfo) {
        super("Field " + fieldInfo.name, analyserContext);
        this.fieldInfo = fieldInfo;
        this.fqn = fieldInfo.fullyQualifiedName();
        this.fieldInspection = fieldInfo.fieldInspection.get();
        this.primaryType = fieldInfo.owner.primaryType();
        fieldAnalysis = new FieldAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, fieldInfo,
                ownerTypeAnalysis);
    }

    @Override
    public TypeInfo getPrimaryType() {
        return primaryType;
    }

    @Override
    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    @Override
    public FieldAnalysisImpl.Builder getFieldAnalysis() {
        return fieldAnalysis;
    }
}
