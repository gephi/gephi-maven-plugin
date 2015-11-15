/*
 * Copyright 2015 Gephi Consortium
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.gephi.maven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public class ModuleUtils {

    protected static Map<MavenProject, List<MavenProject>> getModulesTree(List<MavenProject> modules, Log log) {
        Map<MavenProject, List<MavenProject>> result = new LinkedHashMap<MavenProject, List<MavenProject>>();
        for (MavenProject proj : modules) {
            List<Dependency> dependencies = proj.getDependencies();
            log.debug("Investigating the " + dependencies.size() + " dependencies of project '" + proj.getName() + "'");
            for (Dependency d : dependencies) {
                for (MavenProject projDependency : modules) {
                    if (projDependency != proj
                            && projDependency.getArtifactId().equals(d.getArtifactId())
                            && projDependency.getGroupId().equals(d.getGroupId())
                            && projDependency.getVersion().equals(d.getVersion())) {
                        log.debug("Found a dependency that matches another module '" + proj.getName() + "' -> '" + projDependency.getName() + "'");
                        List<MavenProject> l = result.get(proj);
                        if (l == null) {
                            l = new ArrayList<MavenProject>();
                            l.add(proj);
                            result.put(proj, l);
                        }
                        l.add(projDependency);
                    }
                }
            }
        }
        return result;
    }
}
