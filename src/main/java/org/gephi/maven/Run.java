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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Run the Gephi application with plug-ins installed.
 */
@Mojo(name = "run", aggregator = true, defaultPhase = LifecyclePhase.NONE)
public class Run extends AbstractMojo {

    /**
     * Gephi user directory for the executed instance.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/userdir", property = "gephi.userdir")
    protected File gephiUserdir;

    /**
     * Branding token.
     */
    @Parameter(required = true, defaultValue = "gephi")
    protected String brandingToken;

    /**
     * List of plugin clusters.
     */
    @Parameter
    private List<String> clusters;

    /**
     * Output directory where the the Gephi application is created.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/gephi")
    private File gephiDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        gephiUserdir.mkdirs();

        File appbasedir = gephiDirectory;

        if (!appbasedir.exists()) {
            throw new MojoExecutionException("The directory that shall contain the gephi application, doesn't exist ("
                    + appbasedir.getAbsolutePath() + ")\n Please invoke 'mvn package' on the project first");
        }

        boolean windows = Os.isFamily("windows");

        Commandline cmdLine = new Commandline();
        File exec;
        if (windows) {
            exec = new File(appbasedir, "bin\\" + brandingToken + ".exe");
            // if jdk is 32 or 64-bit
            String jdkHome = System.getenv("JAVA_HOME");
            if (jdkHome != null) {
                if (new File(jdkHome, "jre\\lib\\amd64\\jvm.cfg").exists()) {
                    File exec64 = new File(appbasedir, "bin\\" + brandingToken + "64.exe");
                    if (exec64.isFile()) {
                        exec = exec64;
                    }
                }
            }
            cmdLine.addArguments(new String[]{"--console", "suppress"});
        } else {
            exec = new File(appbasedir, "bin/" + brandingToken);
        }

        cmdLine.setExecutable(exec.getAbsolutePath());

        try {

            List<String> args = new ArrayList<String>();
            args.add("--userdir");
            args.add(gephiUserdir.getAbsolutePath());
            args.add("-J-Dnetbeans.logger.console=true");
            args.add("-J-ea");
            args.add("--branding");
            args.add(brandingToken);

            if (clusters != null && !clusters.isEmpty()) {
                StringBuilder sBuilder = new StringBuilder();
                for (String cluster : clusters) {
                    sBuilder.append(cluster);
                    sBuilder.append(File.pathSeparator);
                }
                sBuilder.deleteCharAt(sBuilder.length() - 1);

                File confFile;
                if (windows) {
                    confFile = new File(appbasedir, "etc\\" + brandingToken + ".conf");
                } else {
                    confFile = new File(appbasedir, "etc/" + brandingToken + ".conf");
                }
                updateLine(confFile, "#extra_clusters=", "extra_clusters=" + sBuilder.toString());
            }

            // use JAVA_HOME if set
            if (System.getenv("JAVA_HOME") != null) {
                args.add("--jdkhome");
                args.add(System.getenv("JAVA_HOME"));
            }

            cmdLine.addArguments(args.toArray(new String[0]));

            getLog().info("Executing: " + cmdLine.toString());
            StreamConsumer out = new StreamConsumer() {

                @Override
                public void consumeLine(String line) {
                    getLog().info(line);
                }
            };
            CommandLineUtils.executeCommandLine(cmdLine, out, out);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed executing Gephi", e);
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Failed executing Gephi", e);
        }
    }

    /**
     * Update the content of <em>file</em> based on search/replace.
     * <p>
     * Note that this only works line-by-line.
     *
     * @param file file to be updated
     * @param search string to search
     * @param replace string to replace
     * @throws IOException if an io error occurs
     */
    private void updateLine(File file, String search, String replace) throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(file));
        File newFile = new File(file.getParent(), file.getName() + ".new");
        PrintWriter writer = new PrintWriter(newFile, "UTF-8");
        String line;

        while ((line = fileReader.readLine()) != null) {
            line = line.replace(search, replace);
            writer.println(line);
        }
        fileReader.close();
        if (writer.checkError()) {
            throw new IOException("Could not rewrite configuration file");
        }
        writer.close();
        file.delete();
        newFile.renameTo(file);
    }
}
