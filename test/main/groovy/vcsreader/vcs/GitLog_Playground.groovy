package vcsreader.vcs

import vcsreader.VcsProject

import static vcsreader.lang.DateTimeUtil.date

class GitLog_Playground {
    static void main(String[] args) {
        def vcsRoots = [new GitVcsRoot("/tmp/junit-test", "", GitSettings.defaults())]
        def project = new VcsProject(vcsRoots)

        def logResult = project.log(date("01/01/2013"), date("01/01/2014")).awaitCompletion()
        println(logResult.commits.join("\n"))
    }
}
