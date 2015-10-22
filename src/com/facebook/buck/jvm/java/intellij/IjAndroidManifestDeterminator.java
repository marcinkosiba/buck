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

package com.facebook.buck.jvm.java.intellij;

import com.facebook.buck.android.AndroidBinaryDescription;
import com.facebook.buck.graph.AbstractBreadthFirstTraversal;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;

/**
 * The Android plugin needs to know the path to the AndroidManifest for every module.
 * In Buck the AndroidManifest is an attribute of the android_binary target we're building, so
 * for the intermediate targets we need to infer this information.
 * To do this we calculate transitive reverse deps and chose the manifest of the first
 * android_binary for that target.
 */
public class IjAndroidManifestDeterminator {
  private final ImmutableMap<TargetNode<?>, TargetNode<?>> manifestMap;

  public IjAndroidManifestDeterminator(TargetGraph targetGraph) {
    this.manifestMap = generateManifestHolderMap(targetGraph);
  }

  private static ImmutableMap<TargetNode<?>, TargetNode<?>> generateManifestHolderMap(
      final TargetGraph targetGraph) {
    ImmutableSet<TargetNode<?>> manifestHolders = FluentIterable.from(targetGraph.getNodes())
        .filter(
            new Predicate<TargetNode<?>>() {
              @Override
              public boolean apply(TargetNode<?> input) {
                return input.getType().equals(AndroidBinaryDescription.TYPE);
              }
            })
        .toSet();
    final ImmutableMap.Builder<TargetNode<?>, TargetNode<?>> result =
        ImmutableMap.builder();

    final Map<TargetNode<?>, TargetNode<?>> nodeToManifestHolder = new HashMap<>();
    for (TargetNode<?> manifestHolder : manifestHolders) {
      nodeToManifestHolder.put(manifestHolder, manifestHolder);
    }

    AbstractBreadthFirstTraversal<TargetNode<?>> traversal =
        new AbstractBreadthFirstTraversal<TargetNode<?>>(manifestHolders) {
          @Override
          public ImmutableSet<TargetNode<?>> visit(TargetNode<?> targetNode) {
            TargetNode<?> binaryTarget = nodeToManifestHolder.remove(targetNode);
            ImmutableSet<TargetNode<?>> depNodes =
                FluentIterable.from(targetNode.getDeps())
                    .transform(targetGraph.get())
                    .toSet();
            if (binaryTarget != null) {
              result.put(targetNode, binaryTarget);
              for (TargetNode<?> depNode : depNodes) {
                nodeToManifestHolder.put(depNode, binaryTarget);
              }
            }
            return depNodes;
          }
        };
    traversal.start();
    return result.build();
  }

  public Optional<TargetNode<?>> getManifestHolder(TargetNode<?> targetNode) {
    return Optional.<TargetNode<?>>fromNullable(manifestMap.get(targetNode));
  }
}
