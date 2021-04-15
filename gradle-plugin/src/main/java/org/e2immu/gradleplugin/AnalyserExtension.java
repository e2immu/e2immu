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
    private boolean skipProject;

    /* from InputConfiguration -- sources taken from Gradle */
    private String jmods; // part of the class path
    private String jre;
    private String sourcePackages;

    /* from AnnotatedAPIConfiguration -- sources taken from Gradle */
    private String readAnnotatedAPIPackages;
    private String annotatedAPIWriteMode;
    private String writeAnnotatedAPIPackages;
    private String writeAnnotatedAPIDestinationPackage;
    private String writeAnnotatedAPIDir;

    /* from AnnotationXMLConfiguration */
    private String readAnnotationXMLPackages;
    private boolean writeAnnotationXML;
    private String writeAnnotationXMLPackages;
    private String writeAnnotationXMLDir;

    /* from UploadConfiguration */
    private Boolean upload;
    private String uploadUrl;
    private String uploadPackages;
    private String uploadProject;

    /* from the general Configuration -- Quiet taken from Gradle */
    private String debug;
    private boolean ignoreErrors;

    private final ActionBroadcast<AnalyserProperties> propertiesActions;

    public AnalyserExtension(ActionBroadcast<AnalyserProperties> propertiesActions) {
        this.propertiesActions = propertiesActions;
    }

    public void properties(Action<? super AnalyserProperties> action) {
        propertiesActions.add(action);
    }

    /* ********* getters and setters ************* */

    public boolean isSkipProject() {
        return skipProject;
    }

    public void setSkipProject(boolean skipProject) {
        this.skipProject = skipProject;
    }

    public String getJmods() {
        return jmods;
    }

    public void setJmods(String jMods) {
        this.jmods = jMods;
    }

    public String getJre() {
        return jre;
    }

    public void setJre(String jre) {
        this.jre = jre;
    }

    public String getSourcePackages() {
        return sourcePackages;
    }

    public void setSourcePackages(String sourcePackages) {
        this.sourcePackages = sourcePackages;
    }

    public String getWriteAnnotatedAPIPackages() {
        return writeAnnotatedAPIPackages;
    }

    public void setWriteAnnotatedAPIPackages(String writeAnnotatedAPIPackages) {
        this.writeAnnotatedAPIPackages = writeAnnotatedAPIPackages;
    }

    public String getWriteAnnotationXMLPackages() {
        return writeAnnotationXMLPackages;
    }

    public void setWriteAnnotationXMLPackages(String writeAnnotationXMLPackages) {
        this.writeAnnotationXMLPackages = writeAnnotationXMLPackages;
    }

    public Boolean getUpload() {
        return upload;
    }

    public void setUpload(Boolean upload) {
        this.upload = upload;
    }

    public String getUploadPackages() {
        return uploadPackages;
    }

    public void setUploadPackages(String uploadPackages) {
        this.uploadPackages = uploadPackages;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getDebug() {
        return debug;
    }

    public void setDebug(String debug) {
        this.debug = debug;
    }

    public String getReadAnnotationXMLPackages() {
        return readAnnotationXMLPackages;
    }

    public void setReadAnnotationXMLPackages(String readAnnotationXMLPackages) {
        this.readAnnotationXMLPackages = readAnnotationXMLPackages;
    }

    public boolean isWriteAnnotationXML() {
        return writeAnnotationXML;
    }

    public void setWriteAnnotationXML(boolean writeAnnotationXML) {
        this.writeAnnotationXML = writeAnnotationXML;
    }

    public String getWriteAnnotationXMLDir() {
        return writeAnnotationXMLDir;
    }

    public void setWriteAnnotationXMLDir(String writeAnnotationXMLDir) {
        this.writeAnnotationXMLDir = writeAnnotationXMLDir;
    }

    public String getReadAnnotatedAPIPackages() {
        return readAnnotatedAPIPackages;
    }

    public void setReadAnnotatedAPIPackages(String readAnnotatedAPIPackages) {
        this.readAnnotatedAPIPackages = readAnnotatedAPIPackages;
    }

    public String getAnnotatedAPIWriteMode() {
        return annotatedAPIWriteMode;
    }

    public void setAnnotatedAPIWriteMode(String annotatedAPIWriteMode) {
        this.annotatedAPIWriteMode = annotatedAPIWriteMode;
    }

    public String getWriteAnnotatedAPIDestinationPackage() {
        return writeAnnotatedAPIDestinationPackage;
    }

    public void setWriteAnnotatedAPIDestinationPackage(String writeAnnotatedAPIDestinationPackage) {
        this.writeAnnotatedAPIDestinationPackage = writeAnnotatedAPIDestinationPackage;
    }

    public String getWriteAnnotatedAPIDir() {
        return writeAnnotatedAPIDir;
    }

    public void setWriteAnnotatedAPIDir(String writeAnnotatedAPIDir) {
        this.writeAnnotatedAPIDir = writeAnnotatedAPIDir;
    }

    public String getUploadProject() {
        return uploadProject;
    }

    public void setUploadProject(String uploadProject) {
        this.uploadProject = uploadProject;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public void setIgnoreErrors(boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
    }
}
