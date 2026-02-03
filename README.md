# Central Portal Publisher

Gradle plugin for publishing Android/Kotlin/Java libraries to Sonatype Central Portal (Maven Central).

## Features

- Publishes Android library artifacts (AAR, POM, Gradle module metadata)
- Generates javadoc and sources artifacts (when available)
- Signs and packages artifacts into a Central Portal upload bundle
- Supports user-managed or automatic Central Portal publishing modes

## Requirements

- Gradle 8.x
- Android Gradle Plugin 8.x (for Android libraries)
- JDK 17+

## Installation

Plugin Portal:
```
plugins {
    id("io.github.tafilovic.central-portal-publisher") version "2.0.8"
}
```

JitPack / Local:
```
plugins {
    id("io.github.tafilovic.central-portal-publisher") version "2.0.8"
}
```

Use the Plugin Portal ID when publishing to plugins.gradle.org.

## Configuration

```
centralPortalPublisher {
    componentName = "prodRelease" // e.g. "release" or "<flavor>Release"
    groupId = "io.github.tafilovic"
    artifactId = "your-library"
    version = "1.0.0"
    flavorName = "prod-release" // optional, only if you build flavors
}
```

### Required properties

These can be in `gradle.properties`:

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

### Signing

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

### Sonatype Central Portal credentials

```
mavenCentralUsername=YOUR_USERNAME
mavenCentralPassword=YOUR_PASSWORD
```

## Usage

Build + upload bundle (local test):
```
./gradlew uploadToCentralPortal
```

Build + create bundle without upload:
```
./gradlew fakeUpload
```

The generated bundle is at:
```
publishTest/build/central-portal-upload.zip
```

## Publishing modes

Set in `gradle.properties`:

```
POM_PUBLISHING_TYPE=USER_MANAGED
```

Options:
- `USER_MANAGED` (default)
- `AUTOMATIC`

## JitPack Notes

If you publish this plugin with JitPack, exclude the `publishTest` module to avoid build failures.
Recommended in `settings.gradle.kts`:

```
if (System.getenv("JITPACK") == null) {
    include(":publishTest")
}
```

## Documentation and Support

- Project URL: https://github.com/tafilovic/central-portal-publisher
- Source code: https://github.com/tafilovic/central-portal-publisher.git
- Issues: https://github.com/tafilovic/central-portal-publisher/issues

## License

MIT License

Copyright (c) 2026 Semsudin Tafilovic

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
