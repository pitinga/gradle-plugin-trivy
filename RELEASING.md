# Publishing Gradle Trivy Plugin

The repository contains the **0.1.0** release configuration and publication metadata. There are two separate release destinations: GitHub (source release) and the Gradle Plugin Portal (plugin distribution).

## Preconditions

- All GitHub Actions checks pass on Windows and Linux.
- The plugin has been tested with `--warning-mode all` and `--configuration-cache`.
- `build.gradle` has `version = '0.1.0'` (not a SNAPSHOT) and includes the `com.gradle.plugin-publish` plugin.
- The [Gradle Plugin Portal](https://plugins.gradle.org/) account belongs to the publisher and has an API key.

## Build and validate locally

From the plugin repository:

~~~powershell
gradle --no-daemon clean test validatePlugins publishToMavenLocal --warning-mode all
~~~

To validate the Plugin Portal submission **without uploading**:

~~~powershell
gradle publishPlugins --validate-only
~~~

That server-side validation may require Plugin Portal credentials.

## Publish securely using GitHub Actions

The repository provides [Publish Gradle Plugin Portal](https://github.com/pitinga/gradle-plugin-trivy/actions/workflows/publish-plugin.yml), a **manual-only** workflow. Add encrypted **Repository Secrets** in Settings > Secrets and variables > Actions with these exact names:

- `GRADLE_PUBLISH_KEY`: Gradle Plugin Portal API key
- `GRADLE_PUBLISH_SECRET`: corresponding API secret

In Actions, select the workflow, choose `Run workflow` on `main`, and select:

- `validate` (default): builds/tests the tagged `v0.1.0` source and runs `publishPlugins --validate-only` without uploading.
- `publish`: runs the same verification and server-side validation first, then `publishPlugins` if both passed.

The workflow checks out the immutable release tag `v0.1.0`, not the latest changing `main`, and limits repository permissions to read. Both secrets are only injected into the validation/publication steps; the public workflow does not contain or print their values.

## Portal credentials

Prefer securely configured environment variables, for example in your local user session or a protected CI environment:

- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`

Alternatively, store `gradle.publish.key` and `gradle.publish.secret` in the publisher's user-level `~/.gradle/gradle.properties`. **Never** commit secrets or credentials to this repository or log them.

## Publish

After validating the public metadata and accepting the Portal's terms:

~~~powershell
gradle publishPlugins
~~~

The first version can require approval by the Gradle Plugin Portal team. Until approval, consumers should use the composite-build `includeBuild` example in README.md.

## GitHub release

For the first release, the checked-in GitHub Actions workflow `.github/workflows/release.yml` is triggered when `RELEASE_READY.md` is committed to `main` (or manually via workflow dispatch). It validates the plugin, creates an annotated `v0.1.0` tag at that commit, and publishes a GitHub release using CHANGELOG.md. The workflow does **not** publish to the Gradle Plugin Portal. Do not reuse the same version after publication; make future improvements in `0.1.1` or later.

## Plugin coordinates

- Plugin ID: `io.github.pitinga.trivy`
- Group: `io.github.pitinga`
- Version: `0.1.0`
- Source: https://github.com/pitinga/gradle-plugin-trivy
