package org.vcsreader.vcs.git

import static org.vcsreader.lang.FileUtil.deleteOnShutdown
import static org.vcsreader.lang.FileUtil.findSequentNonExistentFile
import static org.vcsreader.lang.FileUtil.tempDirectoryFile

class GitIntegrationTestConfig {
	static final String pathToGit = System.getProperty("vcsreader.test.gitPath", "/usr/local/Cellar/git/2.5.0/bin/git")
	static final String author = "Some Author"
	static final String authorWithEmail = "Some Author <some.author@mail.com>"
	static final String nonExistentPath = "/tmp/non-existent-path"

	static String newReferenceRepoPath() {
		def file = findSequentNonExistentFile(tempDirectoryFile(), "git-reference-repo-", "")
		assert file.mkdirs()
		deleteOnShutdown(file)
		file.absolutePath
	}

	static String newProjectPath() {
		def file = findSequentNonExistentFile(tempDirectoryFile(), "git-project-repo-", "")
		assert file.mkdirs()
		deleteOnShutdown(file)
		file.absolutePath
	}
}
