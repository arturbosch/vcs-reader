package org.vcsreader;

import org.vcsreader.lang.Aggregatable;
import org.vcsreader.vcs.VcsCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Arrays.asList;

public class LogResult implements Aggregatable<LogResult> {
	public static final VcsCommand.ResultAdapter<LogResult> adapter = new VcsCommand.ResultAdapter<LogResult>() {
		@Override public LogResult wrapException(Exception e) {
			return new LogResult(e);
		}

		@Override public boolean isSuccessful(LogResult result) {
			return result.isSuccessful();
		}

		@Override public List<String> vcsErrorsIn(LogResult result) {
			return result.vcsErrors();
		}
	};
	private final List<VcsCommit> commits;
	private final List<String> vcsErrors;
	private final List<Exception> exceptions;

	public LogResult() {
		this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	}

	public LogResult(Exception e) {
		this(new ArrayList<>(), new ArrayList<>(), asList(e));
	}

	public LogResult(List<VcsCommit> commits, List<String> vcsErrors) {
		this(commits, vcsErrors, new ArrayList<>());
	}

	public LogResult(List<VcsCommit> commits, List<String> vcsErrors, List<Exception> exceptions) {
		this.commits = commits;
		this.vcsErrors = vcsErrors;
		this.exceptions = exceptions;
	}

	public LogResult aggregateWith(LogResult value) {
		List<VcsCommit> newCommits = new ArrayList<>(commits);
		List<String> newErrors = new ArrayList<>(vcsErrors);
		List<Exception> newExceptions = new ArrayList<>(exceptions);

		newCommits.addAll(value.commits);
		newCommits.sort(Comparator.comparing(VcsCommit::getDateTime));
		newErrors.addAll(value.vcsErrors);
		newExceptions.addAll(value.exceptions);

		return new LogResult(newCommits, newErrors, newExceptions);
	}

	public boolean isSuccessful() {
		return vcsErrors.isEmpty() && exceptions.isEmpty();
	}

	public List<String> vcsErrors() {
		return vcsErrors;
	}

	public List<Exception> exceptions() {
		return exceptions;
	}

	public List<VcsCommit> commits() {
		return commits;
	}

	public LogResult setVcsRoot(VcsRoot vcsRoot) {
		for (VcsCommit commit : commits) {
			if (commit instanceof VcsCommit.WithRootReference) {
				((VcsCommit.WithRootReference) commit).setVcsRoot(vcsRoot);
			}
		}
		return this;
	}

	@Override public String toString() {
		return "LogResult{" +
				"commits=" + commits.size() +
				", vcsErrors=" + vcsErrors.size() +
				", exceptions=" + exceptions.size() +
				'}';
	}

	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		LogResult logResult = (LogResult) o;

		return commits.equals(logResult.commits)
			&& vcsErrors.equals(logResult.vcsErrors)
			&& exceptions.equals(logResult.exceptions);
	}

	@Override public int hashCode() {
		int result = commits.hashCode();
		result = 31 * result + vcsErrors.hashCode();
		result = 31 * result + exceptions.hashCode();
		return result;
	}
}