pluginManagement {
    repositories {
        google()
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

rootProject.name = "HunTracK"

include(":app")
include(":domain")
include(":core:common")
include(":core:designsystem")
include(":core:data")
include(":core:location")
include(":feature:home")
include(":feature:map")
include(":feature:weather")
include(":feature:compass")
include(":feature:wind")
include(":feature:markers")
include(":feature:settings")
include(":feature:species")
include(":feature:calls")
include(":feature:permissions")
include(":feature:emergency")
include(":feature:calendar")
include(":feature:tides")
include(":feature:culls")
