plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(libs.jsr305)
    implementation(libs.kotlinx.serialization.json) // Keep if used or remove
    testImplementation("junit:junit:4.13.2")
}
