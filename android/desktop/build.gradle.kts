plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(21) }
val lwjgl = "3.3.4"
dependencies {
    implementation(project(":core"))
    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-egl:$lwjgl")
    implementation("org.lwjgl:lwjgl-opengles:$lwjgl")
    runtimeOnly("org.lwjgl:lwjgl:$lwjgl:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengles:$lwjgl:natives-linux")
}
application { mainClass.set("vn.paperworld.phidoi.desktop.MainKt") }
tasks.named<JavaExec>("run") {
    environment("LD_LIBRARY_PATH", "/opt/pw-browsers/chromium-1194/chrome-linux")
    environment("VK_ICD_FILENAMES", "/opt/pw-browsers/chromium-1194/chrome-linux/vk_swiftshader_icd.json")
    workingDir = rootProject.projectDir
    maxHeapSize = "4g"
    System.getProperties().forEach { (k, v) -> if (k.toString().startsWith("pd.")) systemProperty(k.toString().removePrefix("pd."), v) }
    args = (project.findProperty("args") as String?)?.split(" ") ?: emptyList()
}
