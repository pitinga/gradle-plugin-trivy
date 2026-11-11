# Publishing Gradle Trivy Plugin — v0.1.4

[README](README.md) · [Release notes](RELEASE_NOTES_0.1.4.md) · [Changelog](CHANGELOG.md)

The release is distributed in two locations:

- **GitHub:** an immutable annotated `v0.1.4` Git tag and a GitHub Release with release notes.
- **Gradle Plugin Portal:** the plugin ID `io.github.pitinga.trivy`, version `0.1.4`.

The repository's release workflow creates the GitHub release first and then publishes **from that immutable tag** to the Gradle Plugin Portal.

## Prerequisites

- `main` has the fully reviewed, tested release code, and `build.gradle` declares `version = '0.1.4'` (no `-SNAPSHOT`).
- Windows/Linux Gradle matrix, native report lab, and Isolated Projects checks have passed.
- GitHub Actions repository secrets `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` are configured securely.
- `RELEASE_NOTES_0.1.4.md` accurately describes this release.
- No existing `v0.1.4` tag or Gradle Plugin Portal version is being replaced.

## Test locally (PowerShell 7)

~~~powershell
gradle --no-daemon clean test validatePlugins publishToMavenLocal --warning-mode all
~~~

To check release credentials and Portal metadata without publishing (requires configured credentials):

~~~powershell
gradle --no-daemon publishPlugins --validate-only
~~~

## Automated gated release

`.github/workflows/release.yml` triggers **only when `RELEASE_READY.md` changes on `main`**. It runs three jobs in this order:

1. **Verify:** check version `0.1.4`, run `clean test validatePlugins publishToMavenLocal` and `publishPlugins --validate-only`.
2. **GitHub release:** create an annotated `v0.1.4` tag and publish a GitHub Release from `RELEASE_NOTES_0.1.4.md`; skip creation if the release already exists.
3. **Plugin Portal:** check out the immutable `v0.1.4` tag, check the version, and run `publishPlugins` with encrypted repository secrets.

The `RELEASE_READY.md` marker should be updated **only after** merging and verifying all normal CI checks. Never edit an existing version tag or publish from moving `main` code after creating a release tag.

## Manual validation/publication workflow

`.github/workflows/publish-plugin.yml` supports `workflow_dispatch` on `main` with a choice of `validate` or `publish`. Both modes check out `v0.1.4` rather than moving `main`. Use `validate` to inspect publication status without uploading, or `publish` to retry a Portal upload from the existing tag **only if the release hasn't already been published**.

The workflow has read-only repository permissions and obtains API credentials only from encrypted GitHub Actions secrets.

## Credentials

Set these **GitHub Actions Repository Secrets**, not values in source control:

- `GRADLE_PUBLISH_KEY`: Gradle Plugin Portal API key
- `GRADLE_PUBLISH_SECRET`: matching API secret

For a local manual publication you may instead use `~/.gradle/gradle.properties` with `gradle.publish.key` and `gradle.publish.secret`. Never commit API keys, build logs containing secrets, or generated credentials.

## After release

Verify that [GitHub release v0.1.4](https://github.com/pitinga/gradle-plugin-trivy/releases/tag/v0.1.4) exists and [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.pitinga.trivy) lists `0.1.4` as the latest version.

Publish consumers with:

~~~groovy
plugins {
    id 'io.github.pitinga.trivy' version '0.1.4'
}
~~~

Afterward, prepare future changes in a new development version rather than modifying the immutable `v0.1.4` release tag.
