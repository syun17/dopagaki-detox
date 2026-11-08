import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 純 Kotlin のドメイン層。Android の API に依存させない（CLAUDE.md「アーキテクチャ」）
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
