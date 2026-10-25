plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi-scratchpad:5.3.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.jsoup:jsoup:1.18.3")

    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath").orNull
        if (!localPath.isNullOrBlank() && file(localPath).exists()) {
            local(localPath)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        // The scaffold generator is compiled into the plugin distribution. It is
        // capability code, not a machine-local executable or user-owned asset.
        java.srcDir("../api-test-scaffold-generator/src/main/java")
        // Thin UI calls the independently versioned initializer; no initialization rules in the interface generator.
        java.srcDir("../automation-project-initializer/src/main/java")
        resources.srcDir("../api-test-scaffold-generator/src/main/resources")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }

    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild = "242"
    }

    buildSearchableOptions {
        enabled = false
    }

    named("prepareJarSearchableOptions") {
        enabled = false
    }

    named("jarSearchableOptions") {
        enabled = false
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.aitestflow"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description = """
            <p>AI Test Flow orchestrates governed, traceable test assets directly inside IntelliJ IDEA.</p>
            <p>支持 PRD、TAD/TRD/STD、测试用例、HTTP/Dubbo 接口规格、TestNG 脚手架、Web ATC、反馈和 Manifest 全链路生成。</p>
            <p>Git-first 无状态能力层：输入、证据、产物与反馈全部进入项目版本管理；提供批量处理、质量门禁和冲突保护。</p>
        """.trimIndent()
        vendor {
            name = "AI Test Flow"
        }
    }
}
