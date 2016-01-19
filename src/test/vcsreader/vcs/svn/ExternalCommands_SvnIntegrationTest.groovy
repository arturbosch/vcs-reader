package vcsreader.vcs.svn

import org.junit.BeforeClass
import org.junit.Test

import static SvnInfo.svnInfo
import static vcsreader.lang.Charsets.UTF8
import static vcsreader.lang.DateTimeUtil.date
import static vcsreader.vcs.svn.SvnIntegrationTestConfig.*
import static vcsreader.vcs.svn.SvnLog.svnLog
import static vcsreader.vcs.svn.SvnLogFileContent.svnLogFileContent

class ExternalCommands_SvnIntegrationTest {
    @Test void "basic log"() {
        def command = svnLog(pathToSvn, repositoryUrl, date("01/01/2013"), date("01/01/2023"), useMergeHistory, quoteDateRange).execute()
        assert command.stderr() == ""
        assert command.stdout().contains("initial commit")
        assert command.exitCode() == 0
    }

    @Test void "failed log"() {
        def command = svnLog(pathToSvn, nonExistentUrl, date("01/01/2013"), date("01/01/2023"), useMergeHistory, quoteDateRange).execute()
        assert !command.stdout().contains("logentry")
        assert command.stderr().contains("Unable to connect to a repository")
        assert command.exitCode() == 1
    }

    @Test void "log file content"() {
        def revision = "1"
        def command = svnLogFileContent(pathToSvn, repositoryUrl, "file1.txt", revision, UTF8).execute()
        assert command.stderr() == ""
        assert command.stdout().trim() == "file1 content"
        assert command.exitCode() == 0
    }

    @Test void "failed log file content"() {
        def revision = "1"
        def command = svnLogFileContent(pathToSvn, repositoryUrl, "non-existent-file", revision, UTF8).execute()
        assert command.stdout() == ""
        assert command.stderr().contains("path not found")
        assert command.exitCode() == 1
    }

    @Test void "get repository information"() {
        def command = svnInfo(pathToSvn, repositoryUrl).execute()
        assert command.stderr() == ""
        assert command.stdout().contains("Repository Root: " + repositoryUrl)
        assert command.exitCode() == 0
    }

    @Test void "failed to get repository information"() {
        def command = svnInfo(pathToSvn, nonExistentUrl).execute()
        assert command.stdout() == ""
        assert command.stderr().contains("Unable to connect to a repository")
        assert command.exitCode() == 1
    }

	@Test void "log with non-ascii characters"() {
		def command = svnLog(pathToSvn, repositoryUrl, date("01/01/2013"), date("01/01/2023"), useMergeHistory, quoteDateRange).execute()
		assert command.stderr() == ""
		assert command.stdout().contains("non-ascii комментарий")
		assert command.exitCode() == 0

		def revision = "8"
		command = svnLogFileContent(pathToSvn, repositoryUrl, "non-ascii.txt", revision, UTF8).execute()
		assert command.stderr() == ""
		assert command.stdout().trim() == "non-ascii содержимое"
		assert command.exitCode() == 0
	}

	@BeforeClass static void setupConfig() {
        initTestConfig()
    }

    private static final useMergeHistory = true
    private static final quoteDateRange = false
}