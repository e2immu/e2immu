package org.e2immu.intellij.highlighter.settings;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import org.e2immu.intellij.highlighter.Bundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.e2immu.intellij.highlighter.Constants.*;

import javax.swing.*;
import java.util.Map;

public class E2ImmuColorSettingsPage implements com.intellij.openapi.options.colors.ColorSettingsPage {
    @Nullable
    @Override
    public Icon getIcon() {
        return null;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new PlainSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return Bundle.INSTANCE.get("e2i.settings.colors.demoText");
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return TAK_MAP;
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        final String SETTINGS = "e2i.settings.colors.attr.";
        return new AttributesDescriptor[]{
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E2IMMUTABLE_TYPE), TAK_E2IMMUTABLE_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E2CONTAINER_TYPE), TAK_FINAL_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_CONTAINER_TYPE), TAK_CONTAINER_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + NOT_ANNOTATED_TYPE), TAK_NOT_ANNOTATED_TYPE),

                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_NOT_MODIFIED_METHOD), TAK_NOT_MODIFIED_METHOD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_MODIFIED_METHOD), TAK_MODIFIED_METHOD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_INDEPENDENT_METHOD), TAK_INDEPENDENT_METHOD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + NOT_ANNOTATED_METHOD), TAK_NOT_ANNOTATED_METHOD),

                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_NOT_MODIFIED_FIELD), TAK_NOT_MODIFIED_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_MODIFIED_FIELD), TAK_MODIFIED_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_FINAL_FIELD), TAK_FINAL_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_VARIABLE_FIELD), TAK_VARIABLE_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E2IMMUTABLE_FIELD), TAK_E2IMMUTABLE_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E2CONTAINER_FIELD), TAK_E2CONTAINER_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + NOT_ANNOTATED_FIELD), TAK_NOT_ANNOTATED_FIELD),

                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_NOT_MODIFIED_PARAM), TAK_NOT_MODIFIED_PARAM),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_MODIFIED_PARAM), TAK_MODIFIED_PARAM),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + NOT_ANNOTATED_PARAM), TAK_NOT_ANNOTATED_PARAM),
        };
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return Bundle.INSTANCE.get("e2i.app.presentableName");
    }
}
