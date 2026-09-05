# 1. Aşama: Build (Resmi Java 21 Maven İmajı)
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# 2. Aşama: Run (Java 21 Runtime)
# NOT: Önceden "eclipse-temurin:21-jdk-alpine" kullanılıyordu. Alpine, glibc
# yerine musl libc kullanır ve glibc'nin dinamik linker'ı olan
# "ld-linux-x86-64.so.2" dosyasını içermez. ONNX Runtime'ın (RuleSuggestionEngine
# / SuggestionModelScorer tarafından kullanılan onnxruntime-java) native
# "libonnxruntime.so" kütüphanesi glibc'ye bağlı derlendiğinden, Alpine
# üzerinde "UnsatisfiedLinkError: ... Error loading shared library
# ld-linux-x86-64.so.2: No such file or directory" hatasıyla uygulama HİÇ
# başlamıyordu (Render deploy loglarında görülen tam hata buydu). glibc tabanlı
# bir imaja geçerek gerçek kök nedeni çözüyoruz.
FROM eclipse-temurin:21-jre
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx300m","-Xms256m","-jar","/app.jar"]