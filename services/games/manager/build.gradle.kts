dependencies {
    compileOnly(project(":shared"))

    compileOnly(project(":services:api"))
    implementation(project(":services:games:game-models"))
}
