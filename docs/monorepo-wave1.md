# Monorepo migration: wave 1

This draft imports FluBundle, cloud-mindustry, xcore-ui, mindustry-testkit and
xcore-protocol into XCore-plugin. Only the Java part of protocol joins Gradle;
Python packaging and generation are checked separately. Maven coordinates stay
unchanged. Component tags use `<component>/vX.Y.Z`.

## Before merge

- Review `plugins/xcore/LICENSE-EXCEPTION` as a proposed additional permission.
- Resolve permission for relicensing contributions to formerly unlicensed
  components, including BRamil0 contributions to FluBundle. The license files
  in this draft express the proposed policy, not evidence of contributor consent.
- Require a successful `ci-ok`, including the real-client menu checks.
- Review the imported history and component artifacts.

## Before the first release

- Configure the `release` environment and its protection policy. Maven credentials
  use `XCORE_USERNAME` and `XCORE_PASSWORD` (repository secrets are already present).
- Rotate any still-active Maven credentials exposed in the old FluBundle history.
  The imported history removes them; the original repository is unaffected.
- Configure PyPI trusted publishing for this repository, `release.yml`, environment
  `pypi`. Check Java and Python versions agree before a protocol release.
- Release new patch versions through the component tags; do not republish existing
  Maven versions or push historical tags as release events.
- Build satellite consumers against the new published artifacts before archiving
  source repositories. Archive each source only after its first successful release.

Repository renaming and required-check configuration belong to the next phase.
TileLogger, Python services, web and the replacement dev tool are later waves.
Do not merge or rename repositories as part of reviewing this draft.

## Secret scan

`.gitleaksignore` lists only reviewed historical fingerprints for public Discord
snowflake IDs in fixtures and documentation. No secret-detection rule is disabled.
Run from the repository root:

```sh
gitleaks git . --redact --log-opts=--all --gitleaks-ignore-path=.gitleaksignore
```
