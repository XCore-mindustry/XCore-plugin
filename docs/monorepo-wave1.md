# Monorepo migration: wave 1

Wave 1 was merged in PR #57. FluBundle, cloud-mindustry, xcore-ui,
mindustry-testkit and xcore-protocol now live in the public `XCore-mindustry/xcore`
repository (formerly XCore-plugin). Only the Java part of protocol joins Gradle;
Python packaging and generation are checked separately. Maven coordinates stay
unchanged. Component tags use `<component>/vX.Y.Z`.

The owner confirmed the required contributor consents on 2026-11-01.
The `ci-ok` check is required on main. The `release` environment accepts only
component release tags (`*/v*`). Repository Maven secrets remain available.

## First monorepo releases

| Component | Version | Status |
| --- | --- | --- |
| FluBundle | 2.1.3 | Prepared |
| cloud-mindustry | 0.4.1 | Prepared |
| xcore-ui | 0.3.0 | Existing unreleased version, prepared |
| testkit (core, ui, fixtures) | 0.2.0 | Existing unreleased version, prepared |
| protocol (Java and Python) | 0.17.1 | Prepared; owner confirmed PyPI trusted publisher setup |

## Before the first release

- Rotate any still-active Maven credentials exposed in the old FluBundle history.
  The imported history removes them; the original repository is unaffected.
- PyPI trusted publishing is configured for this repository, `release.yml`, environment
  `pypi` (confirmed by the owner). Check Java and Python versions agree before a protocol release.
- Release new patch versions through the component tags; do not republish existing
  Maven versions or push historical tags as release events.
- Build satellite consumers against the new published artifacts before archiving
  source repositories. Archive each source only after its first successful release.

TileLogger, Python services, web and the replacement dev tool are later waves.

## Secret scan

`.gitleaksignore` lists only reviewed historical fingerprints for public Discord
snowflake IDs in fixtures and documentation. No secret-detection rule is disabled.
Run from the repository root:

```sh
gitleaks git . --redact --log-opts=--all --gitleaks-ignore-path=.gitleaksignore
```
