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

import org.gradle.api.Action;

public class AnalyserExtension {
    public static final String ANALYSER_EXTENSION_NAME = "e2immu";
    public static final String ANALYSER_TASK_NAME = "e2immu-analyser";

    /* local to the plugin */
    /* for Gradle multi-project builds; allows a project to be skipped. */
    public boolean skipProject;

    /* from InputConfiguration -- sources taken from Gradle */
    public String jmods; // part of the class path
    public String jre;
    public String sourcePackages;
    public String testSourcePackages;

    /* from InspectorConfiguration */
    public String graphDirectory;

    /* from AnnotatedAPIConfiguration -- sources taken from Gradle */
    public String readAnnotatedAPIPackages;
    public String annotatedAPIWriteMode;
    public String writeAnnotatedAPIPackages;
    public String writeAnnotatedAPIDestinationPackage;
    public String writeAnnotatedAPIDir;

    /* from AnnotationXMLConfiguration */
    public String readAnnotationXMLPackages;
    public boolean writeAnnotationXML;
    public String writeAnnotationXMLPackages;
    public String writeAnnotationXMLDir;

    /* from UploadConfiguration */
    public Boolean upload;
    public String uploadUrl;
    public String uploadPackages;
    public String uploadProject;

    /* from the general Configuration -- Quiet taken from Gradle */
    public String debug;
    public boolean ignoreErrors;
    public boolean skipAnalysis;
    public boolean parallel;

    // actions
    public String action;
    public String[] actionParameters;

    private final ActionBroadcast<AnalyserProperties> propertiesActions;

    public AnalyserExtension(ActionBroadcast<AnalyserProperties> propertiesActions) {
        this.propertiesActions = propertiesActions;
    }

    public void properties(Action<? super AnalyserProperties> action) {
        propertiesActions.add(action);
    }
}
