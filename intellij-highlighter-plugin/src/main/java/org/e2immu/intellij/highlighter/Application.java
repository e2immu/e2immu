package org.e2immu.intellij.highlighter;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public class Application {
    public static void refreshFiles() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            DaemonCodeAnalyzer.getInstance(project).restart();
        }
    }
}
