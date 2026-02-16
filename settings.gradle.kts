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
    }
}

rootProject.name = "agent-browser-kotlin"
include(":app")
include(":agent-browser-kotlin")

// Real-agent (tool-loop) validation uses OpenAgentic SDK as an included build.
// This keeps the dependency reproducible without publishing artifacts.
// NOTE: When this repo is used as an included build from another Gradle build, do NOT include nested builds.
if (gradle.parent == null) {
    includeBuild("third_party/openagentic-sdk-kotlin") {
        dependencySubstitution {
            substitute(module("me.lemonhall.openagentic:openagentic-sdk-kotlin")).using(project(":"))
        }
    }
}
 
