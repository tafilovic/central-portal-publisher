package io.github.tafilovic

import io.github.tafilovic.Hash.md5
import io.github.tafilovic.Hash.sha1
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.io.FileOutputStream
import okhttp3.Credentials
import okio.ByteString.Companion.decodeBase64
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gradle plugin that packages and uploads Android library artifacts to Sonatype Central Portal.
 *
 * Responsibilities:
 * - Configure a Maven publication from an Android library component
 * - Attach metadata (POM) and javadoc artifacts
 * - Collect and bundle all publishable artifacts with checksums/signatures
 * - Upload the bundle to Central Portal
 */
class CentralPortalPublisherPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        plugins.apply("maven-publish")

        val ext =
            project.extensions.create("centralPortalPublisher", CentralPortalExtension::class.java)

        val localProperties = Properties().apply {
            val localFile = project.rootProject.file("local.properties")
            if (localFile.exists()) {
                load(localFile.inputStream())
            }
        }

        // Android Library plugin is required to expose a publishable component.
        val androidComponents =
            project.extensions.findByName("android") as? com.android.build.gradle.LibraryExtension
                ?: throw GradleException("Android library plugin is not applied")

        // Javadoc artifact published alongside the AAR.
        val javadocJar = project.tasks.register("javadocJar", Jar::class.java) {
            archiveClassifier.set("javadoc")
            from(project.layout.buildDirectory.dir("docs/javadoc"))
        }

        project.afterEvaluate {
            // Configure Maven publication and POM metadata.
            project.extensions.configure(PublishingExtension::class.java) {
                publications.register("maven", MavenPublication::class.java) {
                    val androidComponent = project.components.findByName(ext.componentName)
                    if (androidComponent == null) {
                        logger.error("❌ 'release' component not found — make sure this is an Android library module")
                        return@register
                    }

                    from(androidComponent)
                    groupId = ext.groupId ?: project.findProperty("GROUP")?.toString()
                    artifactId =
                        ext.artifactId ?: project.findProperty("POM_ARTIFACT_ID")?.toString()
                    version = ext.version ?: project.findProperty("VERSION_NAME")?.toString()

                    pom {
                        name.set(project.findProperty("POM_NAME")?.toString())
                        description.set(project.findProperty("POM_DESCRIPTION")?.toString())
                        url.set(project.findProperty("POM_URL")?.toString())

                        licenses {
                            license {
                                name.set(project.findProperty("POM_LICENSE_NAME")?.toString())
                                url.set(project.findProperty("POM_LICENSE_URL")?.toString())
                                distribution.set(
                                    project.findProperty("POM_LICENSE_DIST")?.toString()
                                )
                            }
                        }

                        developers {
                            developer {
                                id.set(project.findProperty("POM_DEVELOPER_ID")?.toString())
                                name.set(project.findProperty("POM_DEVELOPER_NAME")?.toString())
                                url.set(project.findProperty("POM_DEVELOPER_URL")?.toString())
                            }
                        }

                        scm {
                            url.set(project.findProperty("POM_SCM_URL")?.toString())
                            connection.set(
                                project.findProperty("POM_SCM_CONNECTION")?.toString()
                            )
                            developerConnection.set(
                                project.findProperty("POM_SCM_DEV_CONNECTION")?.toString()
                            )
                        }
                    }
                }

            }

            // Attach additional artifacts to all Maven publications.
            project.extensions.configure(PublishingExtension::class.java) {
                publications.withType(MavenPublication::class.java).configureEach {
                    artifact(javadocJar)
                }
            }

            // Optional signing for all publications.
            project.pluginManager.apply("signing")
            extensions.configure(org.gradle.plugins.signing.SigningExtension::class.java) {
                val keyId = localProperties["signing.keyId"] as String?
                val keyPassword = localProperties["signing.password"] as String?

                // Option 1: .asc file on disk
                val keyFile = (localProperties["signing.secretKeyRingFile"] as String?)?.let {
                    file(it).takeIf { f -> f.exists() }?.readText()
                }

                // Option 2: base64 in GitHub secret
                val keyBase64 = localProperties["signing.keyBase64"] as String?
                val keyDecoded = keyBase64?.decodeBase64()?.utf8()

                val signingKey = keyFile ?: keyDecoded

                if (keyId != null && keyPassword != null && signingKey != null) {
                    println("🔐 Using signing key: $keyId (source: ${if (keyFile != null) "file" else "base64"})")

                    useInMemoryPgpKeys(keyId, signingKey, keyPassword)
                    sign(extensions.getByType(PublishingExtension::class.java).publications)

                    println("✅ Signed artifacts")
                } else {
                    println("⚠️ Signing skipped: missing parameters")
                }
            }
        }

        fun packageArtifacts() {
            val groupId = ext.groupId ?: project.findProperty("GROUP")?.toString()
            val artifactId =
                ext.artifactId ?: project.findProperty("POM_ARTIFACT_ID")?.toString()
            val version = ext.version ?: project.findProperty("VERSION_NAME")?.toString()

            val bundleDir = layout.buildDirectory.dir("central-portal-bundle").get().asFile
            bundleDir.mkdirs()

            // Copy publishable artifacts from the Maven publication.
            val publication = project.extensions.getByType(PublishingExtension::class.java)
                .publications.findByName("maven") as? MavenPublication
            if (publication != null) {
                copyPublicationArtifacts(publication, bundleDir, artifactId, version)
            }
            // Ensure signatures for artifacts built outside the publication directory are included.
            copySignatureFilesFromBuildOutputs(artifactId, version, ext)
            // Copy POM/module metadata from the publication output directory.
            getPublicationsFiles(artifactId, version)

            val filesToZip = bundleDir.listFiles()

            val outputZip = layout.buildDirectory.file("central-portal-upload.zip").get().asFile
            ZipOutputStream(FileOutputStream(outputZip)).use { zipOut ->
                filesToZip?.forEach { file ->
                    zipOut.putNextEntry(
                        ZipEntry(
                            "${
                                groupId?.replace(
                                    ".", "/"
                                )
                            }/$artifactId/$version/${file.name}"
                        )
                    )
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }

            println("✅ Packaged artifacts into ${outputZip.name}")
        }

        // Ensure required artifacts exist before Gradle generates module metadata.
        tasks.matching { it.name.contains("generateMetadataFileForMavenPublication") }
            .configureEach {
                dependsOn(javadocJar)
            }

        tasks.named("build").configure {
            mustRunAfter("clean")
        }

        tasks.named("publishToMavenLocal").configure {
            mustRunAfter("build")
        }

        // Build + bundle only (no upload), useful for local verification.
        tasks.register("fakeUpload")
        {
            group = "publishing"
            dependsOn("clean", "build", "publishToMavenLocal")
            doLast {
                packageArtifacts()
            }
        }

        // Build, bundle, and upload to Central Portal.
        tasks.register("uploadToCentralPortal")
        {
            group = "publishing"
            dependsOn("clean", "build", "publishToMavenLocal")

            val pomVersion = project.findProperty("VERSION_NAME").toString()
            val pomName = project.findProperty("POM_NAME").toString()
            val publishingTypeValue = project.findProperty("POM_PUBLISHING_TYPE").toString()

            doLast {
                packageArtifacts()

                val username = localProperties.getValue("mavenCentralUsername").toString()
                val password = localProperties.getValue("mavenCentralPassword").toString()
                val uploadFile = layout.buildDirectory.dir("central-portal-upload.zip").get().asFile
                val publishingType = PublishingType.parseFrom(publishingTypeValue)

                upload(
                    username = username,
                    password = password,
                    bundleFile = uploadFile,
                    title = "$pomName $pomVersion",
                    publishingType = publishingType
                )
            }
        }
    }

    private fun Project.copyPublicationArtifacts(
        publication: MavenPublication,
        bundleDir: File,
        artifactId: String?,
        version: String?
    ) {
        // Copy each published artifact and generate checksums for non-signature files.
        publication.artifacts.forEach { artifact: MavenArtifact ->
            val file = artifact.file
            if (!file.exists()) return@forEach
            val ext = artifact.extension ?: ""
            val classifier = artifact.classifier
            val name = buildString {
                append("$artifactId-$version")
                if (!classifier.isNullOrBlank()) append("-$classifier")
                append(".$ext")
            }
            val target = bundleDir.resolve(name)
            file.copyTo(target = target, overwrite = true)

            if (ext != "asc") {
                val md5File = bundleDir.resolve("$name.md5")
                md5File.writeBytes(target.md5().toByteArray())
                val sha1File = bundleDir.resolve("$name.sha1")
                sha1File.writeBytes(target.sha1().toByteArray())
            }
        }
    }

    private fun Project.getPublicationsFiles(artifactId: String?, version: String?) {
        val bundleDir = layout.buildDirectory.dir("central-portal-bundle").get().asFile
        val pubFiles = layout.buildDirectory.files("publications/maven").asFileTree.files

        pubFiles.forEach { file ->
            // Normalize file names from Gradle outputs to Maven Central layout.
            val targetExt = when {
                file.name == "module.json.asc" -> "module.asc"
                file.name.endsWith(".pom.asc") -> "pom.asc"
                file.name.endsWith(".module.asc") -> "module.asc"
                file.name.endsWith(".aar.asc") -> "aar.asc"
                file.name.endsWith(".asc") -> "pom.asc"
                file.name.endsWith(".xml") || file.name.endsWith(".pom") -> "pom"
                file.name == "module.json" || file.name.endsWith(".module") -> "module"
                file.name.endsWith(".aar") -> "aar"
                else -> return@forEach
            }
            val updatedName = "$artifactId-$version.$targetExt"
            val target = bundleDir.resolve(updatedName)
            file.copyTo(target = target, overwrite = true)

            if (!targetExt.endsWith(".asc")) {
                val md5File = bundleDir.resolve("$updatedName.md5")
                md5File.writeBytes(target.md5().toByteArray())
                val sha1File = bundleDir.resolve("$updatedName.sha1")
                sha1File.writeBytes(target.sha1().toByteArray())
            }
        }
    }

    private fun Project.copySignatureFilesFromBuildOutputs(
        artifactId: String?,
        version: String?,
        extension: CentralPortalExtension
    ) {
        val bundleDir = layout.buildDirectory.dir("central-portal-bundle").get().asFile
        val flavorName = extension.flavorName

        // AAR signatures are produced under build/outputs/aar.
        val aarAscFiles = layout.buildDirectory.files("outputs/aar").asFileTree.files
            .filter { it.name.endsWith(".aar.asc") }
            .filter { file ->
                file.name.contains("-debug").not() &&
                    (flavorName?.let { file.name.contains(it) } ?: file.name.contains("release"))
            }
        aarAscFiles.forEach { file ->
            val target = bundleDir.resolve("$artifactId-$version.aar.asc")
            file.copyTo(target = target, overwrite = true)
        }

        // Javadoc/Sources signatures are produced under build/libs.
        val jarAscFiles = layout.buildDirectory.files("libs").asFileTree.files
            .filter { it.name.endsWith(".jar.asc") }
        jarAscFiles.forEach { file ->
            val suffix = when {
                file.name.contains("javadoc") -> "javadoc"
                file.name.contains("sources") -> "sources"
                else -> return@forEach
            }
            val target = bundleDir.resolve("$artifactId-$version-$suffix.jar.asc")
            file.copyTo(target = target, overwrite = true)
        }
    }

    private fun upload(
        username: String,
        password: String,
        bundleFile: File,
        title: String,
        publishingType: PublishingType = PublishingType.USER_MANAGED
    ) {
        // Central Portal upload API (multipart bundle upload).
        val loggerInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val token = Credentials.basic(username, password)

        val client = OkHttpClient.Builder().addInterceptor(loggerInterceptor)
            .callTimeout(5, TimeUnit.MINUTES).build()

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
            "bundle",
            bundleFile.name,
            bundleFile.asRequestBody("application/octet-stream".toMediaType())
        ).build()

        val url = HttpUrl.Builder().scheme("https").host("central.sonatype.com")
            .addPathSegments("api/v1/publisher/upload")
            .addQueryParameter("publishingType", publishingType.type)
            .addQueryParameter("name", title)
            .build()

        val request =
            Request.Builder().url(url).addHeader("Authorization", token).post(requestBody)
                .build()

        val maxAttempts = 5
        for (attempt in 1..maxAttempts) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        println("✅ Upload successful: $responseBody")
                        return
                    } else {
                        println(
                            "❌ Upload failed (attempt $attempt/$maxAttempts): " +
                                "${response.code} ${response.message}\n$responseBody"
                        )
                    }
                }
            } catch (ex: Exception) {
                println("❌ Upload failed (attempt $attempt/$maxAttempts): ${ex.message}")
                if (attempt == maxAttempts) throw ex
            }
            if (attempt < maxAttempts) {
                Thread.sleep(2000L * attempt)
            }
        }
    }
}