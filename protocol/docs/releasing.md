# Releasing xcore-protocol

`xcore-protocol` publishes one immutable version for Java and Python from a single Git tag.
Consumers must not select individual source commits in production.

## One-time PyPI setup

Create the `xcore-protocol` project on PyPI, then add a Trusted Publisher with:

- owner: `XCore-mindustry`
- repository: `xcore-protocol`
- workflow: `release.yml`
- environment: `pypi`

This uses GitHub's OIDC token. No PyPI API token or self-hosted registry is required.

## Release procedure

1. Set `project.version` in `pyproject.toml` to the release version.
2. Ensure the protocol CI workflow is green.
3. Create and push an annotated `vX.Y.Z` tag matching `pyproject.toml` exactly.
4. The release workflow validates generation and tests, publishes Java to the XCore Maven releases repository and Python to PyPI, then creates the GitHub Release.
5. Update downstream consumers to the exact published version and regenerate their lockfiles:

```toml
dependencies = ["xcore-protocol==X.Y.Z"]
```

```kotlin
implementation("org.xcore:xcore-protocol-java:X.Y.Z")
```

Do not merge the downstream dependency changes before the PyPI package exists. The first PyPI release
of the current source is `v0.5.1`; after it completes, replace both current Git-revision sources with
`xcore-protocol==0.5.1` and run `uv lock` in each Python consumer.
