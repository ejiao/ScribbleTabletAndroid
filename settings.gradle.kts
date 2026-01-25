pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Onyx/BOOX SDK repository
        maven {
            url = uri("https://repo.boox.com/repository/maven-public/")
            isAllowInsecureProtocol = true
        }
    }
}

rootProject.name = "ScribbleTabletAndroid"
include(":app")
