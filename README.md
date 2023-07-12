# TJFetcher

A single-class Java utility for downloading files identified by
`urn:bitprint:` or `urn:sha1:` URIs and validating based on their
SHA-1 hash (bitprint contains an SHA-1).

The purpose is to allow blobs to be fetched as needed so that one can
avoid committing large files to Git repositories or having to use Git LFS.

TJFetcher is intentionally a tiny single-purpose program consisting of
a single Java class in order to minimize the size of the resulting JAR
file so that TJFetcher itself can be committed to a project without
affecting the size much.

Usage:

```
java -jar TJFetcher.jar [<option> ...] <urn> -o <dest-file>
```

Options:

- ``-nc`` - 'no-clobber' - skip download if destination file already exists
  (note: as of v1.3.0 this doesn't re-verify it!)
- ```-repo <hostname or url prefix>``` - Add to the list of N2R
  repositories from which TJFetcher will attempt to fetch
- ```-repo @<file>``` - Add repositories listed in the named file
  to the repository list.
- ```-debug``` - Be verbose
