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
