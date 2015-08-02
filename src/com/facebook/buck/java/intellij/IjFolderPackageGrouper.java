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

import com.facebook.buck.graph.MutableDirectedGraph;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.java.JavaPackageFinder;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Groups {@link IjFolder}s into sets which are of the same type and belong to the same package
 * structure.
 */
public abstract class IjFolderPackageGrouper {

  public static ImmutableSet<IjFolder> mergeSourceFolders(
      ImmutableSet<IjFolder> folders,
      JavaPackageFinder packageFinder) {

    ImmutableSet.Builder<IjFolder> mergedFolders = ImmutableSet.builder();
    Map<Path, IjFolder> mergePathsMap = new HashMap<>();
    MutableDirectedGraph<Path> trie = new MutableDirectedGraph<>();

    ParsingJavaPackageFinder.PackagePathCache packagePathCache =
        new ParsingJavaPackageFinder.PackagePathCache();

    for (IjFolder folder : folders) {
      if (folder.getType() == AbstractIjFolder.Type.EXCLUDE_FOLDER) {
        // Skip exclude folders because we assume they've already been generated at the topmost
        // possible depth.
        mergedFolders.add(folder);
      } else {
        if (folder.getWantsPackagePrefix()) {
          Path pathToLookUp = pathForPackageLookup(folder);
          Path packageFolder = packageFinder.findJavaPackageFolder(pathToLookUp);
          packagePathCache.insert(pathToLookUp, packageFolder);
        }

        mergePathsMap.put(folder.getPath(), folder);

        Path path = folder.getPath();
        while(true) {
          Path parent = path.getParent();
          if (parent != null) {
            trie.addEdge(parent, path);
          } else {
            break;
          }
          path = parent;
        }
      }
    }

    for (Path topLevel : trie.getNodesWithNoIncomingEdges()) {
      walkTrie(topLevel, trie, mergePathsMap, packagePathCache);
    }

    return mergedFolders
        .addAll(mergePathsMap.values())
        .build();
  }

  private static Path pathForPackageLookup(IjFolder folder) {
   return FluentIterable.from(folder.getInputs()).first().or(folder.getPath().resolve("notfound"));
  }

  private static Optional<IjFolder> walkTrie(
      Path path,
      final MutableDirectedGraph<Path> trie,
      final Map<Path, IjFolder> mergePathsMap,
      final ParsingJavaPackageFinder.PackagePathCache packagePathCache) {
    ImmutableList<Optional<IjFolder>> children =
        FluentIterable.from(trie.getOutgoingNodesFor(path))
            .transform(
                new Function<Path, Optional<IjFolder>>() {
                  @Override
                  public Optional<IjFolder> apply(Path input) {
                    return walkTrie(input, trie, mergePathsMap, packagePathCache);
                  }
                })
            .toList();

    boolean anyAbsent = FluentIterable.from(children)
        .anyMatch(Predicates.equalTo(Optional.<IjFolder>absent()));
    if (anyAbsent) {
      // Signal that no further merging should be done.
      return Optional.absent();
    }

    ImmutableSet<IjFolder> presentChildren = FluentIterable.from(children)
        .transform(
            new Function<Optional<IjFolder>, IjFolder>() {
              @Override
              public IjFolder apply(Optional<IjFolder> input) {
                return input.get();
              }
            })
        .toSet();
    if (presentChildren.isEmpty()) {
      return Optional.of(Preconditions.checkNotNull(mergePathsMap.get(path)));
    }
    Preconditions.checkState(!mergePathsMap.containsKey(path));
    IjFolder aChild = presentChildren.iterator().next();
    final IjFolder myFolder = aChild
        .withInputs(ImmutableSortedSet.<Path>of())
        .withPath(path);
    boolean allChildrenCanBeMerged = FluentIterable.from(presentChildren)
        .allMatch(
            new Predicate<IjFolder>() {
              @Override
              public boolean apply(IjFolder input) {
                return canMerge(myFolder, input, packagePathCache);
              }
            });
    if (!allChildrenCanBeMerged) {
      return Optional.absent();
    }
    IjFolder mergedFolder = myFolder;
    for (IjFolder presentChild : presentChildren) {
      mergePathsMap.remove(presentChild.getPath());
      mergedFolder = presentChild.merge(mergedFolder);
    }
    mergePathsMap.put(mergedFolder.getPath(), mergedFolder);

    return Optional.of(mergedFolder);
  }

  private static boolean canMerge(
      IjFolder parent,
      IjFolder child,
      ParsingJavaPackageFinder.PackagePathCache packagePathCache) {
    Preconditions.checkArgument(child.getPath().startsWith(parent.getPath()));

    if (parent.getType() != child.getType()) {
      return false;
    }
    if (parent.getWantsPackagePrefix() != child.getWantsPackagePrefix()) {
      return false;
    }
    if (parent.getWantsPackagePrefix()) {
      Optional<Path> parentPackageOptional = packagePathCache.lookup(
          parent.getPath().resolve("notfound"));
      if (!parentPackageOptional.isPresent()) {
        return false;
      }
      Path parentPackage = parentPackageOptional.get();
      Path childPackage = packagePathCache.lookup(child.getPath().resolve("notfound")).get();

      int pathDifference = child.getPath().getNameCount() - parent.getPath().getNameCount();
      Preconditions.checkState(pathDifference > 0);
      if (pathDifference >= childPackage.getNameCount()) {
        return false;
      }
      if (!childPackage.subpath(0, childPackage.getNameCount() - pathDifference).equals(
          parentPackage)) {
        return false;
      }
    }
    return true;
  }

}
