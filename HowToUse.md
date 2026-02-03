## How to Use

This plugin can be published in two places:

- Gradle Plugin Portal
- JitPack

Use the same plugin ID in both places:
`io.github.tafilovic.central-portal-publisher`

## Plugin Portal usage (recommended)

```
plugins {
    id("io.github.tafilovic.central-portal-publisher") version "2.0.8"
}
```

## JitPack usage

If you publish the plugin to JitPack, apply it with the same ID:

```
plugins {
    id("io.github.tafilovic.central-portal-publisher") version "2.0.8"
}
```

## Minimal configuration

```
centralPortalPublisher {
    componentName = "release" // or "<flavor>Release"
    groupId = "io.github.tafilovic"
    artifactId = "your-library"
    version = "1.0.0"
    flavorName = "prod-release" // optional, only if you use flavors
}
```

## Required Gradle properties

Add these to `gradle.properties`:

```
GROUP=io.github.tafilovic
POM_ARTIFACT_ID=your-library
VERSION_NAME=1.0.0
POM_NAME=Your Library
POM_DESCRIPTION=Short description
POM_URL=https://github.com/your/repo

POM_LICENSE_NAME=The Apache License, Version 2.0
POM_LICENSE_URL=https://www.apache.org/licenses/LICENSE-2.0.txt
POM_LICENSE_DIST=repo

POM_DEVELOPER_ID=your-id
POM_DEVELOPER_NAME=Your Name
POM_DEVELOPER_URL=https://github.com/your

POM_SCM_URL=https://github.com/your/repo
POM_SCM_CONNECTION=scm:git:github.com/your/repo.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://github.com/your/repo.git
```

## Signing

Add signing credentials to `local.properties` (or CI secrets):

```
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg
```

Or use base64:

```
signing.keyBase64=BASE64_PGP_KEY
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
```

## Sonatype Central Portal credentials

Add to `local.properties` (or CI secrets):

```
mavenCentralUsername=YOUR_USERNAME
mavenCentralPassword=YOUR_PASSWORD
```

## Tasks

Create the bundle and upload:

```
./gradlew uploadToCentralPortal
```

Create the bundle without uploading:

```
./gradlew fakeUpload
```

The bundle is created at:

```
publishTest/build/central-portal-upload.zip
```

## JitPack build note

If you publish this plugin with JitPack, exclude `publishTest` to avoid build failures:

```
if (System.getenv("JITPACK") == null) {
    include(":publishTest")
}
```

## Maintainer Notes (build + release)

### 1) Update version

Update the version in `plugin/build.gradle.kts`:

```
version = "X.Y.Z"
```

Keep the same version in usage snippets (README/HowToUse).

### 2) Build locally (optional but recommended)

```
./gradlew :plugin:build
```

### 3) Publish to Gradle Plugin Portal

Prerequisites:
- Gradle Plugin Portal API key/secret in `~/.gradle/gradle.properties`:
  - `gradle.publish.key`
  - `gradle.publish.secret`

Publish (skips `publishTest`):
```
./gradlew -PskipPublishTest :plugin:publishPlugins
```

Alternative helper task:
```
./gradlew publishPluginPortal
```

### 4) Publish to JitPack

JitPack publishes by Git tag.

```
git tag vX.Y.Z
git push origin vX.Y.Z
```

JitPack will build the tag using `jitpack.yml` and publish artifacts under version `X.Y.Z`.

### 5) Verify consumption

Plugin Portal:
```
plugins {
    id("io.github.tafilovic.central-portal-publisher") version "X.Y.Z"
}
```

JitPack:
```
plugins {
    id("io.github.tafilovic.central-portal-publisher") version "X.Y.Z"
}
```
