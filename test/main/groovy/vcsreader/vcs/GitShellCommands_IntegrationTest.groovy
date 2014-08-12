package vcsreader.vcs
import org.junit.Before
import org.junit.Test

import static vcsreader.lang.DateTimeUtil.date
import static vcsreader.vcs.GitClone.gitClone
import static vcsreader.vcs.GitLog.gitLog
import static vcsreader.vcs.GitLog.gitLogRenames
import static vcsreader.vcs.GitShow.gitLogFile
import static vcsreader.vcs.IntegrationTestConfig.firstRevision
import static vcsreader.vcs.IntegrationTestConfig.pathToGit
import static vcsreader.vcs.IntegrationTestConfig.prePreparedProject
import static vcsreader.vcs.IntegrationTestConfig.projectFolder
import static vcsreader.vcs.IntegrationTestConfig.revisions

class GitShellCommands_IntegrationTest {

    @Test void "git log file content"() {
        def command = gitLogFile(prePreparedProject, "file1.txt", firstRevision)
        assert command.stdout().trim() == "file1 content"
        assert command.stderr() == ""
        assert command.exitValue() == 0
    }

    @Test void "failed git log file content"() {
        def command = gitLogFile(prePreparedProject, "non-existent file", firstRevision)
        assert command.stdout() == ""
        assert command.stderr().startsWith("fatal: Path 'non-existent file' does not exist")
        assert command.exitValue() == 128
    }

    @Test void "git log"() {
        def command = gitLog(pathToGit, prePreparedProject, date("01/01/2013"), date("01/01/2023"))
        assert command.stdout().contains("initial commit")
        assert command.stderr() == ""
        assert command.exitValue() == 0
    }

    @Test void "git log renames"() {
        def command = gitLogRenames(pathToGit, prePreparedProject, revisions[3])
        assert command.stdout().contains("R100")
        assert command.stdout().contains("folder1/file1.txt")
        assert command.stderr() == ""
        assert command.exitValue() == 0
    }

    @Test(expected = RuntimeException) void "failed git log"() { // TODO no exception
        gitLog(pathToGit, nonExistentPath, date("01/01/2013"), date("01/01/2023"))
    }

    @Test void "clone repository"() {
        def command = gitClone(pathToGit, "file://" + prePreparedProject, projectFolder)
        assert command.stdout() == ""
        assert command.stderr().trim() == "Cloning into '/tmp/git-commands-test/git-repo'..."
        assert command.exitValue() == 0
    }

    @Test void "failed clone of non-existent repository"() {
        def command = gitClone(pathToGit, "file://" + nonExistentPath, projectFolder)
        assert command.stdout() == ""
        assert command.stderr().startsWith(
                "Cloning into '/tmp/git-commands-test/git-repo'...\n" +
                "fatal: '/tmp/non-existent-path' does not appear to be a git repository")
        assert command.exitValue() == 128
    }

    @Before void setup() {
        new File(projectFolder).deleteDir()
        new File(projectFolder).mkdirs()
    }

    private static final String nonExistentPath = "/tmp/non-existent-path"
}
