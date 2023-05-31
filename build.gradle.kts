group = "br.com.orientefarma.orienteIntegracaoOL"
version = "2.2.2"
description = "Integrador Pedidos OL"
val userHome = System.getProperty("user.home")
val lughVersion = "6.6.56"
val skwVersion = "4.14b262"

plugins {
    kotlin("jvm") version "1.5.31"
    kotlin("kapt") version "1.5.31"
}

buildscript{
    repositories{
        mavenCentral()

    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
    maven {
        url = uri("http://sankhyatec.mgcloud.net.br/api/v4/projects/173/packages/maven")
        name = "GitLab"
        metadataSources {
            artifact()
            mavenPom()
        }
        credentials(HttpHeaderCredentials::class.java) {
            name = "Private-Token"
            value = "YzDkSQZrWVnXYzG1RMQN"
        }
        authentication {
            create<HttpHeaderAuthentication>("header")
        }
    }

}

dependencies {

    //depedencias basicas
    implementation("br.com.sankhya", "mge-modelcore", skwVersion)
    implementation("br.com.sankhya", "mgeserv-model", skwVersion)
    implementation("javax.ejb","ejb-api","3.0")
    implementation("br.com.sankhya", "reflectdao", skwVersion)
    implementation("br.com.sankhya", "dwf", skwVersion)
    implementation("br.com.sankhya", "jape", skwVersion)
    implementation("br.com.sankhya", "sanutil", skwVersion)
    implementation("br.com.sankhya", "mge-param", skwVersion)
    implementation("br.com.sankhya", "sanws", skwVersion)
    implementation("br.com.sankhya", "cuckoo", skwVersion)
    implementation("javax.servlet", "servlet-api", "2.5")
    implementation("br.com.lughconsultoria", "lugh-lib", lughVersion )

}

tasks.withType(JavaCompile::class.java) {
    this.options.encoding = "ISO-8859-1"
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}