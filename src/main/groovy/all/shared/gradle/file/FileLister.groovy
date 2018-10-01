//  Copyright (c) 2018 Gonzalo Müller Bravo.
//  Licensed under the MIT License (MIT), see LICENSE.txt
package all.shared.gradle.file

import groovy.transform.CompileStatic

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTreeElement

@CompileStatic
class FileLister {
  private final static Iterable<String> EMPTY_ITERABLE = []
  private final static Iterable<String> DEFAULT_EXCLUDES = ['**/gradlew.*', '**/gradle', '**/.gradle', '**/build', '**/node_modules']
  private final Project project

  FileLister (final Project project) {
    this.project = project
  }

  private static Iterable<String> obtainExcludesFromGitIgnore(final File gitIgnoreFile) {
    gitIgnoreFile
      .readLines()
      *.trim()
      .findAll { !it.isEmpty() && !it.matches('(^\\s*[#].*)|(.*[\\[\\]\\!].*)') } // Ignores patterns with ! [ ] and comments
      .collect { it.matches('(^[^/].*/?$)') ? "/**/$it" : it }
      .collect { gitIgnoreFile.parent + it }
  }

  private Iterable<String> obtainExcludesFromDir(final String dir) {
    final File gitIgnoreFile = project.file("$dir/.gitignore")
    project.logger.debug "Scanning $gitIgnoreFile for ignored patterns"
    gitIgnoreFile.exists() ? obtainExcludesFromGitIgnore(gitIgnoreFile) : [] as Iterable<String>
  }

  private Iterable<String> removeDirPathToExcludes(final Iterable<String> excludes, final String dirPath) {
    excludes.asList().collect { (it - dirPath) - '/' }
  }

  private Iterable<String> obtainAllExcludes(final String folder, final ConfigurableFileTree tree) {
    final Iterable<String> allExcludes = obtainExcludesFromDir(folder)
    tree.visit { FileTreeElement el ->
      if (el.directory) {
        allExcludes += obtainExcludesFromDir(el.file.path)
      }
    }
    removeDirPathToExcludes(allExcludes, tree.dir.path)
  }

  private ConfigurableFileTree showDebugInfo(final ConfigurableFileTree tree) {
    if (project.logger.debugEnabled) {
      tree.includes.each { project.logger.debug "Including $it in $tree.dir" }
      tree.excludes.each { project.logger.debug "Excluding $it in $tree.dir" }
    }
    tree
  }

  private ConfigurableFileTree obtainFileTree(final String folder, final Iterable<String> excludes, final Iterable<String> includes) {
    project.fileTree(folder) { ConfigurableFileTree tree ->
      tree.include includes
      tree.exclude excludes + DEFAULT_EXCLUDES
    }
  }

  final ConfigurableFileTree obtainFullFileTree(final String folder = '.', final Map<String, List<String>> cludes = [:]) {
    showDebugInfo(obtainFileTree(folder, cludes?.excludes ?: EMPTY_ITERABLE, cludes?.includes ?: EMPTY_ITERABLE))
  }

  final ConfigurableFileTree obtainPartialFileTree(final String folder = '.', final Map<String, List<String>> cludes = [:]) {
    final ConfigurableFileTree tree = obtainFullFileTree(folder, cludes)
    showDebugInfo((ConfigurableFileTree) tree.exclude(obtainAllExcludes(folder, tree)))
  }
}