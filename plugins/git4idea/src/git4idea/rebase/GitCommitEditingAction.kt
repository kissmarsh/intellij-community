/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.rebase

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.VcsLogData
import git4idea.GitUtil.HEAD
import git4idea.GitUtil.getRepositoryManager
import git4idea.config.GitSharedSettings
import git4idea.repo.GitRepository

/**
 * Base class for Git action which is going to edit existing commits,
 * i.e. should be enabled only on commits not pushed to a protected branch.
 */
abstract class GitCommitEditingAction : DumbAwareAction() {

  private val COMMIT_NOT_IN_HEAD = "The commit is not in the current branch"
  private val COMMIT_PUSHED_TO_PROTECTED = "The commit is already pushed to protected branch "

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    val data = e.getData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData?
    val ui = e.getData(VcsLogDataKeys.VCS_LOG_UI)
    if (project == null || log == null || data == null || ui == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val selectedCommits = log.selectedShortDetails.size
    if (selectedCommits != 1) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val commit = log.selectedShortDetails[0]
    val repository = getRepositoryManager(project).getRepositoryForRoot(commit.root)
    if (repository == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    // editing merge commit is not allowed
    val parents = commit.parents.size
    if (parents != 1) {
      e.presentation.isEnabled = false
      e.presentation.description = "Selected commit has $parents parents"
      return
    }

    // allow editing only in the current branch
    val branches = data.containingBranchesGetter.getContainingBranchesFromCache(commit.root, commit.id)
    if (branches != null) { // otherwise the information is not available yet, and we'll recheck harder in actionPerformed
      if (!branches.contains(HEAD)) {
        e.presentation.isEnabled = false
        e.presentation.description = COMMIT_NOT_IN_HEAD
        return
      }

      // and not if pushed to a protected branch
      val protectedBranch = findProtectedRemoteBranch(repository, branches)
      if (protectedBranch != null) {
        e.presentation.isEnabled = false
        e.presentation.description = COMMIT_PUSHED_TO_PROTECTED + protectedBranch
        return
      }
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val data = e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData
    val log = e.getRequiredData(VcsLogDataKeys.VCS_LOG)

    val commit = log.selectedShortDetails[0]
    val repository = getRepositoryManager(project).getRepositoryForRoot(commit.root)!!

    val branches = findContainingBranches(data, commit.root, commit.id)

    if (!branches.contains(HEAD)) {
      Messages.showErrorDialog(project, COMMIT_NOT_IN_HEAD, getFailureTitle())
      return
    }

    // and not if pushed to a protected branch
    val protectedBranch = findProtectedRemoteBranch(repository, branches)
    if (protectedBranch != null) {
      Messages.showErrorDialog(project, COMMIT_PUSHED_TO_PROTECTED + protectedBranch, getFailureTitle())
      return
    }
  }

  protected fun getLog(e: AnActionEvent) = e.getRequiredData(VcsLogDataKeys.VCS_LOG)

  protected fun getLogData(e: AnActionEvent) = e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData

  protected fun getUi(e: AnActionEvent) = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI)

  protected fun getSelectedCommit(e: AnActionEvent) = getLog(e).selectedShortDetails[0]!!

  protected fun getRepository(e: AnActionEvent) = getRepositoryManager(e.project!!).getRepositoryForRoot(getSelectedCommit(e).root)!!

  protected abstract fun getFailureTitle(): String

  protected fun findContainingBranches(data: VcsLogData, root: VirtualFile, hash: Hash): List<String> {
    val branchesGetter = data.containingBranchesGetter
    return branchesGetter.getContainingBranchesFromCache(root, hash) ?:
           branchesGetter.getContainingBranchesSynchronously(root, hash)
  }

  protected fun findProtectedRemoteBranch(repository: GitRepository, branches: List<String>): String? {
    val settings = GitSharedSettings.getInstance(repository.project)
    // protected branches hold patterns for branch names without remote names
    return repository.branches.remoteBranches.
        filter { settings.isBranchProtected(it.nameForRemoteOperations) }.
        map { it.nameForLocalOperations }.
        filter { branches.contains(it) }.firstOrNull()
  }
}