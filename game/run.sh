#!/bin/sh
# Compila y ejecuta SIN Maven (solo necesitas un JDK 17 o superior)
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java") || exit 1
cp -r src/main/resources/* out/
java -Xmx1g -cp out com.pacman3d.Main
