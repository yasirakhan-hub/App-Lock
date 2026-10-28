pluginManagement {
  repositories {
    google()
    maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
    maven { url = uri("https://mirrors.huaweicloud.com/repository/maven/") }
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
    maven { url = uri("https://mirrors.huaweicloud.com/repository/maven/") }
    mavenCentral()
  }
}

rootProject.name = "Smart AppLock"

include(":app")
