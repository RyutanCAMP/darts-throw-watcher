// FirestoreRest.kt（Android の API を使わない部分）を PC の JVM で動かして、Firestore エミュレーターに対して確かめる。
// 使い方は jvm-test/README.md
plugins { kotlin("jvm") version "1.9.24"; application }
repositories { mavenCentral() }
sourceSets["main"].kotlin.srcDir("src/main/kotlin")
sourceSets["main"].kotlin.srcDir("../app/src/main/java/jp/ryutancamp/throwwatcher/rest")
application { mainClass.set("MainKt") }
