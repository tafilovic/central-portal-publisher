## Maintainer Notes

This file is for future maintenance and publishing, not for end-user usage.

## What this plugin does

- Builds a Maven publication from an Android library component.
- Generates a javadoc jar, bundles AAR/POM/module metadata + signatures.
- Uploads the bundle to Sonatype Central Portal.

## Key files and entry points

- Plugin implementation: `plugin/src/main/kotlin/io/github/tafilovic/CentralPortalPublisherPlugin.kt`
- Extension: `plugin/src/main/kotlin/io/github/tafilovic/CentralPortalExtension.kt`
- Plugin ID and version: `plugin/build.gradle.kts`
- CI publish workflow: `.github/workflows/publish-gradle-portal.yml`

## Tasks exposed by the plugin (consumer project)

- `packageArtifactsOnly` creates the upload bundle without uploading.
- `uploadToCentralPortal` creates and uploads the bundle.

Note: publication/signing configuration only runs when publish-related tasks are requested.

## Local build and smoke check

```
./gradlew :plugin:build
```

## Publish a new version to Gradle Plugin Portal

1) Update the plugin version:

```
version = "X.Y.Z"
```

2) Run GitHub Actions workflow:

- Workflow: `Publish Gradle Plugin`
- Input: version `X.Y.Z`
- Secrets: `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`
- Environment: `publishing environment`

## If you extend functionality

Common places to modify:

- New config options: `CentralPortalExtension`
- Publish flow: `packageArtifacts()`, `upload()`, and artifact collection helpers
- Task wiring: `packageArtifactsOnly` and `uploadToCentralPortal` registration
- Metadata mapping: POM values in `PublishingExtension` setup
