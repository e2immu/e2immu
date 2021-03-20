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
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E2CONTAINER_TYPE), TAK_E2CONTAINER_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E2IMMUTABLE_TYPE), TAK_E2IMMUTABLE_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E1CONTAINER_TYPE), TAK_E1CONTAINER_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_E1IMMUTABLE_TYPE), TAK_E1IMMUTABLE_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_CONTAINER_TYPE), TAK_CONTAINER_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_MUTABLE_MODIFIES_ARGUMENTS_TYPE), TAK_MUTABLE_MODIFIES_ARGUMENTS_TYPE),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + NOT_ANNOTATED_TYPE), TAK_NOT_ANNOTATED_TYPE),

                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_INDEPENDENT_METHOD), TAK_INDEPENDENT_METHOD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_DEPENDENT_METHOD), TAK_DEPENDENT_METHOD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_NOT_MODIFIED_METHOD), TAK_NOT_MODIFIED_METHOD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_MODIFIED_METHOD), TAK_MODIFIED_METHOD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + NOT_ANNOTATED_METHOD), TAK_NOT_ANNOTATED_METHOD),

                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_NOT_MODIFIED_FIELD), TAK_NOT_MODIFIED_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_MODIFIED_FIELD), TAK_MODIFIED_FIELD),
                new AttributesDescriptor(Bundle.INSTANCE.get(SETTINGS + ANNOTATION_VARIABLE_FIELD), TAK_VARIABLE_FIELD),
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
