pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "org.gradle.toolchains" && requested.id.name == "foojay-resolver-convention") {
                useVersion("")
            }
        }
    }
}

rootProject.name = "task2"
