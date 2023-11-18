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

package org.e2immu.gradleplugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class AnalyserPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(AnalyserPlugin.class);

    @Override
    public void apply(Project project) {
        if (project.getExtensions().findByName(AnalyserExtension.ANALYSER_EXTENSION_NAME) == null) {
            Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap = new HashMap<>();
            addExtensions(project, actionBroadcastMap);
            LOGGER.debug("Adding " + AnalyserExtension.ANALYSER_TASK_NAME + " task to " + project);
            AnalyserTask task = project.getTasks().create(AnalyserExtension.ANALYSER_TASK_NAME, AnalyserTask.class);
            task.setDescription("Analyses " + project + " and its sub-projects with the e2immu analyser.");
            configureTask(task, project, actionBroadcastMap);
        }
    }

    private void addExtensions(Project project, Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap) {
        project.getAllprojects().forEach(p -> {
            LOGGER.debug("Adding " + AnalyserExtension.ANALYSER_EXTENSION_NAME + " extension to " + p);
            ActionBroadcast<AnalyserProperties> actionBroadcast = new ActionBroadcast<>();
            actionBroadcastMap.put(project.getPath(), actionBroadcast);
            p.getExtensions().create(AnalyserExtension.ANALYSER_EXTENSION_NAME, AnalyserExtension.class, actionBroadcast);
        });
    }

    private void configureTask(AnalyserTask analyserTask, Project project, Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap) {
        ConventionMapping conventionMapping = analyserTask.getConventionMapping();
        // this will call the AnalyserPropertyComputer to populate the properties of the task just before running it
        conventionMapping.map("properties", () -> new AnalyserPropertyComputer(actionBroadcastMap, project).computeProperties());

        Callable<Iterable<? extends Task>> compileTasks = () -> project.getAllprojects().stream()
                .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && !p.getExtensions().getByType(AnalyserExtension.class).skipProject)
                .map(p -> p.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME))
                .collect(Collectors.toList());
        analyserTask.dependsOn(compileTasks);
    }
}
