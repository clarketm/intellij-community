/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ApplyPatchDefaultExecutor implements ApplyPatchExecutor<AbstractFilePatchInProgress> {
  protected final Project myProject;

  public ApplyPatchDefaultExecutor(Project project) {
    myProject = project;
  }

  @Override
  public String getName() {
    // not used
    return null;
  }

  @CalledInAwt
  @Override
  public void apply(@NotNull List<FilePatch> remaining, @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable ThrowableComputable<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    final CommitContext commitContext = new CommitContext();
    applyAdditionalInfoBefore(myProject, additionalInfo, commitContext);
    final Collection<PatchApplier> appliers = getPatchAppliers(patchGroupsToApply, localList, commitContext);
    PatchApplier.executePatchGroup(appliers, localList);
  }

  @NotNull
  protected Collection<PatchApplier> getPatchAppliers(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups,
                                                      @Nullable LocalChangeList localList,
                                                      @NotNull CommitContext commitContext) {
    final Collection<PatchApplier> appliers = new LinkedList<>();
    for (VirtualFile base : patchGroups.keySet()) {
      appliers.add(new PatchApplier<BinaryFilePatch>(myProject, base,
                                                     ContainerUtil
                                                       .map(patchGroups.get(base), patchInProgress -> patchInProgress.getPatch()), localList, null, commitContext));
    }
    return appliers;
  }


  public static void applyAdditionalInfoBefore(final Project project,
                                               @Nullable ThrowableComputable<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo,
                                               @Nullable CommitContext commitContext) {
    final PatchEP[] extensions = Extensions.getExtensions(PatchEP.EP_NAME, project);
    if (extensions.length == 0 || additionalInfo == null) return;
    try {
      Map<String, Map<String, CharSequence>> additionalInfoMap = additionalInfo.compute();
      for (Map.Entry<String, Map<String, CharSequence>> entry : additionalInfoMap.entrySet()) {
        for (PatchEP extension : extensions) {
          final CharSequence charSequence = entry.getValue().get(extension.getName());
          if (charSequence != null) {
            extension.consumeContentBeforePatchApplied(entry.getKey(), charSequence, commitContext);
          }
        }
      }
    }
    catch (PatchSyntaxException e) {
      VcsBalloonProblemNotifier
        .showOverChangesView(project, "Can not apply additional patch info: " + e.getMessage(), MessageType.ERROR);
    }
  }

  public static Set<String> pathsFromGroups(MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups) {
    final Set<String> selectedPaths = new HashSet<>();
    final Collection<? extends AbstractFilePatchInProgress> values = patchGroups.values();
    for (AbstractFilePatchInProgress value : values) {
      final String path = value.getPatch().getBeforeName() == null ? value.getPatch().getAfterName() : value.getPatch().getBeforeName();
      selectedPaths.add(path);
    }
    return selectedPaths;
  }
}
