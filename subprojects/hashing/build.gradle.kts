plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools for creating secure hashes for files and other content"

gradlebuildJava.usedInWorkers() // org.gradle.internal.nativeintegration.filesystem.Stat is used in workers

dependencies {
    api(libs.jsr305)

    implementation(project(":base-annotations"))
    implementation(libs.guava)
}
