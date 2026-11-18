#!/bin/sh
# Contrôle hors Android Studio : compile le code Android (hors interface) contre android-26.jar (Android 8.0)
# puis exécute les tests unitaires. Prérequis : kotlinc 2.0.x et android-26.jar (dossier platforms/android-26 du SDK).
# Usage : sh verification/verifier.sh /chemin/kotlinc /chemin/android-26.jar
set -e
KC="$1"; AJ="$2"; J=app/src/main/java/fr/polo/cockpit; OUT=$(mktemp -d)
"$KC/bin/kotlinc" -no-jdk -cp "$AJ" -d "$OUT/main" verification/*.kt $J/vehicle/*.kt $J/apps/AppLauncher.kt $J/weather/WeatherRepository.kt
for t in ProtocolTest ElmSimTest MergeTest; do
  "$KC/bin/kotlinc" -cp "$AJ:$OUT/main" verification/tests/$t.kt -d "$OUT/$t"
  java -cp "$OUT/$t:$OUT/main:$KC/lib/kotlin-stdlib.jar" ${t}Kt
done
