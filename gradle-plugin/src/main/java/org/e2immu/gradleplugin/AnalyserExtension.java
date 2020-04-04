/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.gradleplugin;

import org.gradle.api.Action;

public class AnalyserExtension {
    public static final String ANALYSER_EXTENSION_NAME = "e2immu";
    public static final String ANALYSER_TASK_NAME = "e2immu-analyser";

    private boolean skipProject;
    private String jmods;
    private String jre;
    private String sourcePackages;
    private String writeAnnotatedAPIPackages;
    private String writeAnnotationXMLPackages;
    private Boolean upload;
    private String uploadUrl;
    private String uploadPackages;
    private String debug;

    private final ActionBroadcast<AnalyserProperties> propertiesActions;

    public AnalyserExtension(ActionBroadcast<AnalyserProperties> propertiesActions) {
        this.propertiesActions = propertiesActions;
    }

    public void properties(Action<? super AnalyserProperties> action) {
        propertiesActions.add(action);
    }

    public boolean isSkipProject() {
        return skipProject;
    }

    public void setSkipProject(boolean skipProject) {
        this.skipProject = skipProject;
    }

    public String getJmods() {
        return jmods;
    }

    public void setJmods(String jmods) {
        this.jmods = jmods;
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
}
