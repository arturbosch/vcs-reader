package vcsreader.vcs.svn
import org.junit.Test

import java.nio.charset.Charset

import static vcsreader.lang.DateTimeUtil.date
import static vcsreader.vcs.svn.SvnIntegrationTestConfig.*
import static vcsreader.vcs.svn.SvnLog.svnLog
import static vcsreader.vcs.svn.SvnLogFileContent.svnLogFileContent

class ShellCommands_SvnIntegrationTest {
    @Test void "svn log"() {
        def command = svnLog(pathToSvn, repositoryUrl, date("01/01/2013"), date("01/01/2023"))
        assert command.stdout().contains("initial commit")
        assert command.stderr() == ""
        assert command.exitValue() == 0
    }

    @Test void "failed svn log"() {
        def command = svnLog(pathToSvn, nonExistentUrl, date("01/01/2013"), date("01/01/2023"))
        assert !command.stdout().contains("logentry")
        assert command.stderr().contains("Unable to connect to a repository")
        assert command.exitValue() == 1
    }

    @Test void "svn log file content"() {
        def revision = "1"
        def command = svnLogFileContent(pathToSvn, repositoryUrl, "file1.txt", revision, utf8)
        assert command.stdout().trim() == "file1 content"
        assert command.stderr() == ""
        assert command.exitValue() == 0
    }

    @Test void "failed svn log file content"() {
        def revision = "1"
        def command = svnLogFileContent(pathToSvn, repositoryUrl, "non-existent-file", revision, utf8)
        assert command.stdout() == ""
        assert command.stderr().contains("path not found")
        assert command.exitValue() == 1
    }

    private static final Charset utf8 = Charset.forName("UTF-8")
}