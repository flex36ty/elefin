import com.google.protobuf.gradle.*

plugins {
    id("java-library")
    alias(libs.plugins.protobuf)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":timeago-parser"))
    implementation(libs.nanojson)
    implementation(libs.jsoup)
    implementation(libs.jsr305)
    implementation(libs.protobuf.javalite)
    implementation(libs.rhino)
    implementation(libs.rhino.engine)
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                // Java builtin is added by default, so we utilize named() to configure it
                named("java") {
                    option("lite")
                }
            }
        }
    }
}
