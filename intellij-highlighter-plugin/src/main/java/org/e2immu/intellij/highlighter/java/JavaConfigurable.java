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

package org.e2immu.intellij.highlighter.java;

import com.intellij.openapi.options.SearchableConfigurable;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.intellij.highlighter.Bundle;
import org.e2immu.intellij.highlighter.Constants;
import org.e2immu.intellij.highlighter.store.AnnotationStore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Container
public class JavaConfigurable implements SearchableConfigurable {

    private final JavaConfig config = JavaConfig.INSTANCE;
    private final JavaConfigurableGui gui = new JavaConfigurableGui();

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    @NotModified
    public String getDisplayName() {
        return Bundle.INSTANCE.get("e2i.app.presentableName");
    }

    @Override
    public void reset() {
        ConfigData state = config.getState();
        gui.setHighlightDeclarations(state.isHighlightDeclarations());
        gui.setHighlightStatements(state.isHighlightStatements());
        gui.setHighlightUnknownTypes(state.isHighlightUnknownTypes());
        gui.setAnnotationProject(state.getAnnotationProject());
        gui.setAnnotationServerUrl(state.getAnnotationServerUrl());
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        reset();
        return gui.getRootPanel();
    }

    @Override
    @NotModified
    public boolean isModified() {
        ConfigData state = config.getState();
        return gui.isHighlightDeclarations() != state.isHighlightDeclarations() ||
                gui.isHighlightStatements() != state.isHighlightStatements() ||
                gui.isHighlightUnknownTypes() != state.isHighlightUnknownTypes() ||
                isProjectOrServerChanged(state);
    }

    private boolean isProjectOrServerChanged(ConfigData state) {
        return !gui.getAnnotationProject().equals(state.getAnnotationProject()) ||
                !gui.getAnnotationServerUrl().equals(state.getAnnotationServerUrl());
    }

    @Override
    public void apply() {
        ConfigData state = config.getState();
        if(isProjectOrServerChanged(state)) {
            AnnotationStore.INSTANCE.clearCache();
        }
        config.setState(new ConfigData(
                gui.isHighlightDeclarations(),
                gui.isHighlightUnknownTypes(),
                gui.isHighlightStatements(),
                gui.getAnnotationServerUrl(),
                gui.getAnnotationProject()));
    }

    @NotNull
    @Override
    @NotModified
    @Constant
    public String getId() {
        return "preferences" + Constants.APP_NAME;
    }
}
