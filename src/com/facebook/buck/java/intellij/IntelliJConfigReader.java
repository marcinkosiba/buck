/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.java.intellij;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;

import java.io.*;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reads IntelliJ settings.
 */
public class IntelliJConfigReader {

  private static Logger LOG = Logger.get(IntelliJConfigReader.class);

  private static ImmutableMap<Platform, String> CONFIG_FOLDER_PATTERNS =
      ImmutableMap.of(
          Platform.LINUX, "glob:{.IdeaIC*,IntelliJIdea*}",
          Platform.MACOS, "glob:{IdeaIC*,IntelliJIdea*}",
          Platform.WINDOWS, "glob{.IdeaIC*,IntelliJIdea*}"
      );

  private ProjectFilesystem projectFilesystem;
  private Path ijSettingsFolder;
  private Supplier<Boolean> isAndroidPluginDisabled;

  public IntelliJConfigReader(ProjectFilesystem projectFilesystem, Path ijSettingsFolder) {
    this.projectFilesystem = projectFilesystem;
    this.ijSettingsFolder = ijSettingsFolder.toAbsolutePath();
    this.isAndroidPluginDisabled = Suppliers.memoize(
        new Supplier<Boolean>() {
          @Override
          public Boolean get() {
            return computeIsAndroidPluginDisabled();
          }
        });
  }

  public static Optional<IntelliJConfigReader> create(
      final ProjectFilesystem projectFilesystem,
      Optional<Path> ijHomeFromEnv,
      Optional<Path> ijHomeFromCommandLine,
      Path userHome) {
    Optional<Path> ijSettingsFolder = ijHomeFromCommandLine.or(ijHomeFromEnv);
    if (!ijSettingsFolder.isPresent() || !ijSettingsFolder.get().toFile().isDirectory()) {
      PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(
          CONFIG_FOLDER_PATTERNS.get(Platform.detect()));
      File[] homeFolderContents = userHome.toFile().listFiles();
      Arrays.sort(homeFolderContents, Collections.reverseOrder());
      for (File file : homeFolderContents) {
        Path candidate = file.toPath();
        if (pathMatcher.matches(candidate) && file.isDirectory()) {
          ijSettingsFolder = Optional.of(candidate);
          break;
        }
      }
    }
    return ijSettingsFolder
        .transform(
            new Function<Path, IntelliJConfigReader>() {
              @Override
              public IntelliJConfigReader apply(Path input) {
                return new IntelliJConfigReader(projectFilesystem, input);
              }
            });
  }

  public boolean getIsAndroidPluginDisabled() {
    return isAndroidPluginDisabled.get();
  }

  private boolean computeIsAndroidPluginDisabled() {
    Path disabledPluginsListPath = ijSettingsFolder.resolve(
        "config/disabled_plugins.txt");
    if (!projectFilesystem.exists(disabledPluginsListPath)) {
      return false;
    }

    try {
      List<String> lines = projectFilesystem.readLines(disabledPluginsListPath);
      return FluentIterable.from(lines)
          .anyMatch(
              new Predicate<String>() {
                @Override
                public boolean apply(String input) {
                  return input.trim().equals("org.jetbrains.android");
                }
              });
    } catch (IOException e) {
      LOG.error(e, "Problem reading disabled plugins list.");
      return false;
    }
  }
}
