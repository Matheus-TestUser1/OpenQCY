plugins {
    kotlin("jvm") version "2.3.20"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("../android/app/src/main/kotlin")
            kotlin.include("com/httpkiwi/melocontrol/PeatsReadProtocol.kt")
        }
        test {
            kotlin.srcDir("../android/app/src/test/kotlin")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
