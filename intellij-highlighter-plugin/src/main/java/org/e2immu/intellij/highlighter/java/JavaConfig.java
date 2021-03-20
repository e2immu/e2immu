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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Singleton;
import org.e2immu.intellij.highlighter.Application;
import org.e2immu.intellij.highlighter.Constants;
import org.jetbrains.annotations.NotNull;

@Singleton
@Container
@State(name = "Java", storages = {@Storage(Constants.STORAGE_FILE)})
public class JavaConfig implements PersistentStateComponent<ConfigData> {

    public static JavaConfig INSTANCE = ServiceManager.getService(JavaConfig.class);

    private ConfigData state = new ConfigData();

    @NotNull
    @Override
    public ConfigData getState() {
        return state;
    }

    public void setState(ConfigData state) {
        this.state = state;
        Application.refreshFiles();
    }

    @Override
    public void loadState(@NotNull ConfigData state) {
        this.state = state;
    }

}
