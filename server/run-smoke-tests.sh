#!/usr/bin/env sh
set -eu

# Каталог результатов создается внутри target и не затрагивает исходные файлы.
mkdir -p target/smoke-classes

# Компилируются серверные классы и автономный набор быстрых тестов.
javac --release 17 -d target/smoke-classes \
  $(find src/main/java -name '*.java') \
  src/test/java/ru/audiomark/server/SmokeTestRunner.java

# Запускаются восемь проверок бизнес-логики.
java -cp target/smoke-classes ru.audiomark.server.SmokeTestRunner

