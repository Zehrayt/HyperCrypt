# HyperCrypt 
(English version below)

🇹🇷
Çarpımsal Hiperhalkaların Algoritmik Modellemesi ve Kriptografik Uygulamaları

Bu proje, TÜBİTAK 2209-A Üniversite Öğrencileri Araştırma Projeleri Destekleme Programı kapsamında Fatma Zehra AYTAŞ ve Safiyye Kalemci tarafından, danışmanımız Dr. Elif Özel Ay rehberliğinde geliştirdiğimiz bir web platformudur.

Amacımız, soyut cebirin en karmaşık konularından biri olan Hiperyapıları (Hyperstructures) sadece teorik kitaplarda bırakmamak; onları algoritmik olarak modelledikten sonra eğitim ve siber güvenlik alanlarında somut birer mühendislik aracına dönüştürmektir.

Özellikler
Platformumuz iki ana modülden oluşmaktadır:

1. Eğitim Modülü (Soyut Cebiri Somutlaştırmak)
Kullanıcıların kendi hiper-işlem kurallarını yazıp test edebildiği interaktif bir analiz motorudur. 
    Sonlu Kümeler (AxiomVerifier): Kullanıcının girdiği kuralın Kapanıklık, Birleşme, Dağılma, Üretim ve Negatif özelliklerini {Z}_n modüler aritmetiğinde "Brute-force" yöntemiyle test eder ve otomatik Cayley Tabloları oluşturur.
    Sonsuz Kümeler (Sembolik Analiz): Java Algebra System (JAS) kütüphanesi kullanılarak, kuralın polinom derecesi üzerinden gerçek matematiksel ispatlar (örneğin sıfıra bölme imkansızlığı) yapar.
    Doğrulanmış Kural Önerisi: Bir aksiyom sağlanmadığında, sistem katsayı kaydırma, operatör değişimi, terim çıkarma ve eksik değişken ekleme gibi stratejilerle küçük mutasyonlar üretir; her adayı AxiomVerifier ile fiilen doğrular ve yalnızca aksiyomları gerçekten sağladığı kanıtlanmış önerileri kullanıcıya sunar. İsteğe bağlı olarak, Colab'da AxiomVerifier'ın kendisi bir otomatik etiketleme kaynağı olarak kullanılarak eğitilmiş ve ONNX formatına dönüştürülmüş bir sınıflandırma modeli, bu adayların deneme sırasını önceliklendirir; modelin skoru yalnızca sırayı belirler, nihai doğrulama her zaman AxiomVerifier'da kalır.

2. Siber Güvenlik Modülü (Kriptografik Simülasyonlar)
Hiperhalkaların doğrusal olmayan (non-linear) cebirsel karmaşıklığının güvenlik protokollerine nasıl entegre edilebileceğini gösteren simülasyonlardır.
    Hiper Şifreleme (Custom Map & Binary Mode): Klasik Sezar şifrelemesini hiper-işlemlerle genelleştirir. İster karakter bazında, ister ASCII tablosu üzerinden bit-seviyesinde (Binary) şifreleme simülasyonu yapar.
    Hiper-Diffie-Hellman: Klasik anahtar değişim protokolündeki sabit üs alma işlemi yerine, deterministik kullanıcı tanımlı hiper-kuralların entegre edildiği bir konsept simülasyondur.
    
Kullanılan Teknolojiler
    Frontend: HTML, CSS, JavaScript 
    Backend: Java, Spring Boot
    Cebirsel Motorlar: JAS (Java Algebra System)
    Kural Ayrıştırıcı (Sandbox): Mozilla Rhino (Zararlı kod enjeksiyonlarına karşı özel kelime filtreli ve kısıtlı environment)
    Kural Önerisi / Model Çalıştırma: ONNX Runtime (Colab'da eğitilen önceliklendirme modeli için)


🇬🇧
HyperCryptAlgorithmic Modeling of Multiplicative Hyperrings and Their Cryptographic Applications

This project is a web platform developed by Fatma Zehra AYTAŞ and Safiyye Kalemci, under the supervision of our advisor Dr. Elif Özel Ay, within the scope of the TÜBİTAK 2209-A University Students Research Projects Support Program. 

Our goal is not to leave Hyperstructures, one of the most complex topics in abstract algebra, solely in theoretical textbooks. Instead, we aim to algorithmically model them and transform them into concrete engineering tools for the fields of education and cybersecurity.

Features
Our platform consists of two main modules:

1. Education Module (Concretizing Abstract Algebra) 
An interactive analysis engine where users can write and test their own hyper-operation rules.
    Finite Sets (AxiomVerifier): Tests the user-defined rule for Closure, Associativity, Distributivity, Reproduction, and Negative properties using a "Brute-force" method in {Z}_n modular arithmetic, and automatically generates Cayley Tables.
    Infinite Sets (Symbolic Analysis): Uses the Java Algebra System (JAS) library to perform actual mathematical proofs (e.g., the impossibility of division by zero) based on the polynomial degree of the rule.
    Verified Rule Suggestions: When an axiom is not satisfied, the system generates small mutations (coefficient shifts, operator swaps, term drops, missing-variable fixes) and actually re-verifies each candidate with AxiomVerifier, surfacing only suggestions proven to satisfy the axioms. An optional classifier — trained in Colab using AxiomVerifier itself as an automatic labeling oracle and exported to ONNX — prioritizes which candidates are tried first; its score only affects ordering, final verification always stays with AxiomVerifier.
    
2. Cybersecurity Module (Cryptographic Simulations) 
Simulations demonstrating how the non-linear algebraic complexity of hyperrings can be integrated into security protocols.
    Hyper Encryption (Custom Map & Binary Mode): Generalizes the classic Caesar cipher with hyper-operations. It simulates encryption either on a character basis or at the bit-level (Binary) using the ASCII table.
    Hyper-Diffie-Hellman: A conceptual simulation that integrates deterministic, user-defined hyper-rules instead of the standard exponentiation operation found in the classic key exchange protocol.
    
Technologies Used
    Frontend: HTML, CSS, JavaScript 
    Backend: Java, Spring Boot
    Algebraic Engines: JAS (Java Algebra System)
    Rule Parser (Sandbox): Mozilla Rhino (A restricted environment with custom keyword filters against malicious code injection)
    Rule Suggestion / Model Runtime: ONNX Runtime (for the Colab-trained prioritization model)