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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import net.coobird.thumbnailator.resizers.Resizers;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.gephi.maven.json.Image;

public class ScreenshotUtils {

    private static final String THUMBNAIL_SUFFIX = "-thumbnail";

    protected static List<Image> copyScreenshots(MavenProject mavenProject, File outputFolder, String urlPrefix, Log log) throws MojoExecutionException {
        File folder = new File(mavenProject.getBasedir(), "src/img");
        if (folder.exists()) {
            log.debug("Folder '" + folder.getAbsolutePath() + "' exists");

            // List images in folder
            File[] files = folder.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return !name.startsWith(".")
                            && (name.endsWith(".png") || name.endsWith(".jpg")
                            || name.endsWith(".jpeg") || name.endsWith(".gif"))
                            && !name.contains(THUMBNAIL_SUFFIX);
                }
            });

            // Sort files alphabetically
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return f1.getName().compareTo(f2.getName());
                }
            });
            log.debug(files.length + " images found in source folder");

            // Create dest folder
            if (outputFolder.mkdirs()) {
                log.debug("Output folder '" + outputFolder.getAbsolutePath() + "' was created");
            }

            List<Image> images = new ArrayList<Image>();
            for (File file : files) {
                if (file.getName().contains(" ")) {
                    throw new MojoExecutionException("Image file '" + file.getAbsolutePath() + "' contains spaces. Please rename image and try again");
                }
                // Read original file and copy to dest folder
                String fileName = file.getName().substring(0, file.getName().lastIndexOf(".")) + ".png";
                File imageDestFile = new File(outputFolder, fileName);
                try {
                    Thumbnails.of(file).
                            outputFormat("png").
                            outputQuality(0.90).
                            resizer(Resizers.NULL).
                            scale(1.0).
                            toFile(imageDestFile);
                } catch (IOException ex) {
                    log.error("Can't copy image file from '" + file.getAbsolutePath() + "' to '" + imageDestFile.getAbsolutePath() + "'", ex);
                }

                Image image = new Image();
                image.image = urlPrefix + fileName;
                images.add(image);

                // Thumbnail path
                String thumFileName = file.getName().substring(0, file.getName().lastIndexOf(".")) + THUMBNAIL_SUFFIX + ".png";
                File thumbFile = new File(outputFolder, thumFileName);
                if (!thumbFile.exists()) {
                    // Thumbnail creation
                    try {
                        Thumbnails.of(file)
                                .outputFormat("png")
                                .outputQuality(0.90)
                                .size(140, 140)
                                .crop(Positions.CENTER)
                                .toFile(thumbFile);
                        log.debug("Created thumbnail in file '" + thumbFile.getAbsolutePath() + "'");
                        image.thumbnail = urlPrefix + thumFileName;
                    } catch (IOException ex) {
                        log.error("Can't create thumbnail for image file '" + file.getAbsolutePath() + "'", ex);
                    }
                }

                log.info("Attached image '" + file.getName() + "' to plugin " + mavenProject.getName());
            }
            return images;
        } else {
            log.debug("Folder '" + folder.getAbsolutePath() + "' was not found");
        }
        return null;
    }
}
