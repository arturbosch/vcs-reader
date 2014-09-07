package vcsreader.vcs.git

import groovy.json.JsonSlurper

class GitIntegrationTestConfig {
    static String pathToGit
    static String referenceProject
    static String nonExistentPath = "/tmp/non-existent-path"
    static String author
    private static List<String> revisions

    static String revision(int n) {
        revisions[n - 1]
    }

    static initTestConfig() {
        def configFile = new File("src/test/vcsreader/vcs/git/git-test-config.json")
        if (!configFile.exists()) {
            throw new IllegalStateException("Cannot find " + configFile.name + ".\n" +
                    "Please make sure you run tests with project root as working directory.")
        }
        def config = new JsonSlurper().parse(configFile)
        pathToGit = config["pathToGit"] as String
        referenceProject = config["referenceProject"] as String
        author = config["author"] as String
        revisions = config["revisions"] as List

        if (!new File(pathToGit).exists())
            throw new FileNotFoundException("Cannot find " + pathToGit + ". Please check content of " + configFile.absolutePath)
        if (!new File(referenceProject).exists())
            throw new FileNotFoundException("Cannot find " + referenceProject + ". Please run git-create-repo.rb")
    }
}
