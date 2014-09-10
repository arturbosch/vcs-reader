package vcsreader.vcs.git;

import vcsreader.Change;
import vcsreader.Commit;
import vcsreader.lang.VcsCommand;
import vcsreader.vcs.infrastructure.ShellCommand;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.util.Arrays.asList;
import static vcsreader.Change.Type.*;
import static vcsreader.VcsProject.LogResult;
import static vcsreader.lang.StringUtil.split;
import static vcsreader.lang.StringUtil.trim;

class GitLog implements VcsCommand<LogResult> {
    private final String gitPath;
    private final String folder;
    private final Date fromDate;
    private final Date toDate;
    private final ShellCommand shellCommand;

    public GitLog(String gitPath, String folder, Date fromDate, Date toDate) {
        this.gitPath = gitPath;
        this.folder = folder;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.shellCommand = gitLog(gitPath, folder, fromDate, toDate);
    }

    @Override public LogResult execute() {
        shellCommand.execute();

        // TODO try extracting commit parser
        List<Commit> commits = parseListOfCommits(shellCommand.stdout());
        commits = handleFileRenamesIn(commits);

        List<String> errors = (shellCommand.stderr().trim().isEmpty() ? new ArrayList<String>() : asList(shellCommand.stderr()));
        return new LogResult(commits, errors);
    }

    static ShellCommand gitLog(String gitPath, String folder, Date fromDate, Date toDate) {
        String from = "--after=" + Long.toString(fromDate.getTime() / 1000);
        String to = "--before=" + Long.toString(toDate.getTime() / 1000);
        String showFileStatus = "--name-status"; // see --diff-filter at https://www.kernel.org/pub/software/scm/git/docs/git-log.html
        return new ShellCommand(gitPath, "log", logFormat(), from, to, showFileStatus, "--encoding=UTF-8").workingDir(folder);
    }

    private List<Commit> handleFileRenamesIn(List<Commit> commits) {
        List<Commit> result = new ArrayList<Commit>();
        for (Commit commit : commits) {
            if (hasPotentialRenames(commit)) {

                ShellCommand shellCommand = gitLogRenames(gitPath, folder, commit.revision);
                List<Change> updatedChanges = parseListOfChanges(shellCommand.stdout(), commit.revision, commit.revisionBefore);
                commit = new Commit(commit.revision, commit.revisionBefore, commit.commitDate, commit.authorName, commit.comment, updatedChanges);

            }
            result.add(commit);
        }
        return result;
    }

    private static boolean hasPotentialRenames(Commit commit) {
        boolean hasDeletions = false;
        boolean hasAdditions = false;
        for (Change change : commit.changes) {
            if (change.type == DELETED) hasDeletions = true;
            else if (change.type == NEW) hasAdditions = true;
        }
        return hasDeletions && hasAdditions;
    }

    static ShellCommand gitLogRenames(String gitPath, String folder, String revision) {
        // based on git4idea.history.GitHistoryUtils#getFirstCommitRenamePath
        ShellCommand shellCommand = new ShellCommand(gitPath, "show", "-M", "--pretty=format:", "--name-status", revision).workingDir(folder);
        return shellCommand.execute();
    }

    private static String logFormat() {
        // see "PRETTY FORMATS" at https://www.kernel.org/pub/software/scm/git/docs/git-log.html
        String commitHash = "%H";
        String parentHashes = "%P";
        String commitDate = "%ct";
        String authorName = "%an"; // see http://stackoverflow.com/questions/18750808/difference-between-author-and-committer-in-git
        String rawBody = "%s%n%n%-b"; // based on git4idea.checkin.GitCheckinEnvironment.GitCheckinOptions.getLastCommitMessage()

        String commitStartSeparator = "%x11%x12%x13%n";
        String fieldSeparator = "%x10%x11%x12%n";

        return "--pretty=format:" +
                commitStartSeparator +
                commitHash + fieldSeparator +
                parentHashes + fieldSeparator +
                commitDate + fieldSeparator +
                authorName + fieldSeparator +
                rawBody + fieldSeparator;
    }

    private static List<Commit> parseListOfCommits(String stdout) {
        List<Commit> commits = new ArrayList<Commit>();

        String commitStartSeparator = "\u0011\u0012\u0013\n";
        String commitFieldsSeparator = "\u0010\u0011\u0012\n";

        List<String> commitsAsString = split(stdout, commitStartSeparator);

        for (String s : commitsAsString) {
            Commit commit = parseCommit(s, commitFieldsSeparator);
            if (commit != null) {
                commits.add(commit);
            }
        }
        return commits;
    }

    private static Commit parseCommit(String s, String commitFieldsSeparator) {
        List<String> values = split(s, commitFieldsSeparator);

        List<String> previousRevision = split(values.get(1), " ");
        boolean isFirstCommit = previousRevision.size() == 0;
        boolean isMergeCommit = previousRevision.size() > 1;
        if (isMergeCommit) return null;

        String revision = values.get(0);
        String revisionBefore = (isFirstCommit ? Change.noRevision : previousRevision.get(0));
        Date commitDate = parseDate(values.get(2));
        String authorName = values.get(3);
        String comment = trim(values.get(4), " \r\n\t");
        List<Change> changes = parseListOfChanges(values.get(5), revision, revisionBefore);

        return new Commit(revision, revisionBefore, commitDate, authorName, comment, changes);
    }

    private static List<Change> parseListOfChanges(String changesAsString, String revision, String revisionBefore) {
        List<Change> changes = new ArrayList<Change>();

        for (String s : split(changesAsString, "\n")) {
            Change change = parseChange(s, revision, revisionBefore);
            if (change != null) {
                changes.add(change);
            }
        }

        return changes;
    }

    private static Change parseChange(String s, String revision, String revisionBefore) {
        if (s.trim().isEmpty()) return null;

        List<String> values = split(s, "\t");
        Change.Type changeType = parseChangeType(values.get(0));

        boolean hasRenames = values.size() > 2;
        String filePath = unescapeQuotes(hasRenames ? values.get(2) : values.get(1));
        String filePathBefore = unescapeQuotes(hasRenames ? values.get(1) : filePath);

        if (changeType == NEW) {
            filePathBefore = Change.noFilePath;
            revisionBefore = Change.noRevision;
        } else if (changeType == DELETED) {
            filePathBefore = filePath;
            filePath = Change.noFilePath;
        }

        return new Change(changeType, filePath, filePathBefore, revision, revisionBefore);
    }

    /**
     * See git4idea.GitUtil#unescapePath(java.lang.String) for more complete implementation.
     */
    private static String unescapeQuotes(String filePath) {
        String quote = "\"";
        if (!filePath.startsWith(quote)) return filePath;
        return filePath.substring(1, filePath.length() - 1).replace("\\\"", "\"");
    }

    private static Change.Type parseChangeType(String s) {
        // see "--diff-filter" at https://www.kernel.org/pub/software/scm/git/docs/git-log.html
        char added = 'A';
        char copied = 'C';
        char modified = 'M';
        char typeChanged = 'T';
        char unmerged = 'U';
        char unknown = 'X';
        char deleted = 'D';
        char renamed = 'R';

        char c = s.charAt(0);
        if (c == added || c == copied) {
            return NEW;
        } else if (c == modified || c == typeChanged || c == unmerged || c == unknown) {
            return MODIFICATION;
        } else if (c == deleted) {
            return DELETED;
        } else if (c == renamed) {
            return MOVED;
        } else {
            throw new IllegalStateException("Unknown git change type: " + s);
        }
    }

    private static Date parseDate(String s) {
        return new Date(Long.parseLong(s) * 1000);
    }

    @Override public String describe() {
        return shellCommand.describe(); // TODO add subcommands descriptions
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GitLog gitLog = (GitLog) o;

        if (folder != null ? !folder.equals(gitLog.folder) : gitLog.folder != null) return false;
        if (fromDate != null ? !fromDate.equals(gitLog.fromDate) : gitLog.fromDate != null) return false;
        if (gitPath != null ? !gitPath.equals(gitLog.gitPath) : gitLog.gitPath != null) return false;
        if (toDate != null ? !toDate.equals(gitLog.toDate) : gitLog.toDate != null) return false;

        return true;
    }

    @Override public int hashCode() {
        int result = gitPath != null ? gitPath.hashCode() : 0;
        result = 31 * result + (folder != null ? folder.hashCode() : 0);
        result = 31 * result + (fromDate != null ? fromDate.hashCode() : 0);
        result = 31 * result + (toDate != null ? toDate.hashCode() : 0);
        return result;
    }

    @Override public String toString() {
        return "GitLog{" +
                "toDate=" + toDate +
                ", fromDate=" + fromDate +
                ", folder='" + folder + '\'' +
                ", gitPath='" + gitPath + '\'' +
                '}';
    }
}
