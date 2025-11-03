plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.kii.camera"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.runtime.compose)

    // CameraX dependencies
    api(libs.androidx.camera.core)
    api(libs.androidx.camera.camera2)
    api(libs.androidx.camera.lifecycle)
    api(libs.androidx.camera.view)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Maven Publishing Configuration
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.kii"
            artifactId = "camera"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("KII Camera")
                description.set("Preset-based CameraX library with real-time frame streaming and customizable Compose UI")
                url.set("https://github.com/yourusername/kii-camera")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("chaebin")
                        name.set("Chaebin")
                        email.set("your.email@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yourusername/kii-camera.git")
                    developerConnection.set("scm:git:ssh://github.com:yourusername/kii-camera.git")
                    url.set("https://github.com/yourusername/kii-camera")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri("${project.buildDir}/repo")
        }
        // GitHub Packages 또는 Maven Central 설정은 필요시 추가
        // maven {
        //     name = "GitHubPackages"
        //     url = uri("https://maven.pkg.github.com/yourusername/kii-camera")
        //     credentials {
        //         username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
        //         password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        //     }
        // }
    }
}