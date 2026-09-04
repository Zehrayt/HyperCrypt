document.addEventListener("DOMContentLoaded", () => {
  // DİL BUTONLARI
  const langButtons = document.querySelectorAll(".lang-switch-btn");

  function updateLanguageButton(lang) {
    langButtons.forEach((button) => {
      button.classList.toggle("active", button.dataset.lang === lang);
    });
  }

  const savedLang = localStorage.getItem("language") || "tr";

  // Sayfa açıldığında aktif dili göster
  updateLanguageButton(savedLang);

  // Dil butonlarına tıklama
  langButtons.forEach((button) => {
    button.addEventListener("click", () => {
      const selectedLang = button.dataset.lang;

      localStorage.setItem("language", selectedLang);

      updateLanguageButton(selectedLang);

      // i18n.js'in dinleyebileceği event
      document.dispatchEvent(
        new CustomEvent("languageChanged", {
          detail: { language: selectedLang },
        }),
      );
    });
  });

  const modeSwitcher = document.getElementById("modeSwitcher");
  const customMapMode = document.getElementById("customMapMode");
  const binaryMode = document.getElementById("binaryMode");

  const customMessageInput = document.getElementById("customMessageInput");
  const qKeyboard = document.getElementById("qKeyboard");
  const customRuleInput = document.getElementById("customRuleInput");
  const customEncryptedPreview = document.getElementById(
    "customEncryptedPreview",
  );
  const sendCustomButton = document.getElementById("sendCustomButton");
  const customCommunicationLog = document.getElementById(
    "customCommunicationLog",
  );

  const binaryMessageInput = document.getElementById("binaryMessageInput");
  const binaryRuleInput = document.getElementById("binaryRuleInput");
  const binaryPreviewOutput = document.getElementById("binaryPreviewOutput");
  const sendBinaryButton = document.getElementById("sendBinaryButton");
  const binaryCommunicationLog = document.getElementById(
    "binaryCommunicationLog",
  );

  // Klavye tuşları (QWERTY düzeni)
  const qwertyLayout = [
    "Q",
    "W",
    "E",
    "R",
    "T",
    "Y",
    "U",
    "I",
    "O",
    "P",
    "A",
    "S",
    "D",
    "F",
    "G",
    "H",
    "J",
    "K",
    "L",
    "Z",
    "X",
    "C",
    "V",
    "B",
    "N",
    "M",
    " ", // Boşluk tuşu
  ];

  // Anahtar haritalamalarını tutacak obje
  const keyMappings = {}; // { 'Q': 12, 'W': 5, ... }

  // Dil değişince yeniden çizebilmek için son sonuçları saklıyoruz
  let lastCustomResult = null;
  let lastBinaryResult = null;

  // --- Yardımcı Fonksiyonlar ---

  // Custom Map Mod: Klavye oluşturma
  function createQKeyboard() {
    qKeyboard.innerHTML = ""; // Önceki klavyeyi temizle
    qwertyLayout.forEach((char) => {
      const keyElement = document.createElement("div");
      keyElement.classList.add("key");
      keyElement.textContent = char;
      keyElement.dataset.char = char; // Hangi karaktere ait olduğunu tutar

      // Her tuş için sayı girişi input'u oluştur
      const input = document.createElement("input");
      input.type = "number";
      input.classList.add("key-value-input");
      input.placeholder = "?";
      input.style.display = "none"; // Başlangıçta gizli
      input.addEventListener("click", (e) => e.stopPropagation()); // Inputa tıklayınca tuşu tetiklememesi için
      input.addEventListener("change", (e) => {
        const val = parseInt(e.target.value);
        if (!isNaN(val)) {
          keyMappings[char] = val;
          keyElement.classList.add("active"); // Anahtar atandığında yeşil yap
        } else {
          delete keyMappings[char]; // Değer boşsa veya sayı değilse anahtarı kaldır
          keyElement.classList.remove("active");
        }
      });
      keyElement.appendChild(input);

      keyElement.addEventListener("click", () => {
        // Diğer tuşların inputlarını gizle
        qKeyboard.querySelectorAll(".key-value-input").forEach((otherInput) => {
          if (otherInput !== input) {
            otherInput.style.display = "none";
          }
        });

        // Tıklanan tuşun input'unu göster/gizle
        input.style.display = input.style.display === "none" ? "block" : "none";
        if (input.style.display === "block") {
          input.focus(); // Açıldığında odaklan
        }
      });

      qKeyboard.appendChild(keyElement);
    });
  }

  // Custom Map Mod: Önizlemeyi güncelleme
  // Custom Map Mod: Önizlemeyi güncelleme (DÖNGÜSEL ZİNCİRLEME - TAM SAYILAR KÜMESİ)
  function updateCustomPreview() {
    const message = customMessageInput.value.toUpperCase();
    const rule = customRuleInput.value;
    let encryptedPreview = "";

    // Sadece değeri atanmış (klavyede yeşil yanan) harfleri zincire al
    let validChars = [];
    for (const char of message) {
      if (keyMappings[char] !== undefined) {
        validChars.push(char);
      }
    }

    let resultIndex = 0;

    for (const char of message) {
      if (keyMappings[char] !== undefined) {
        let a = keyMappings[char]; // Kullanıcının girdiği sayı (Örn: 1680)

        // Zincirdeki BİR SONRAKİ harfi bul (Döngüsel)
        let nextValidChar = validChars[(resultIndex + 1) % validChars.length];
        let b = keyMappings[nextValidChar]; // Sonraki harfin sayısı (Örn: 500)

        try {
          // Kuralı 'a' ve 'b' değişkenleriyle çalıştır
          const func = new Function("a", "b", "return " + rule);
          let result = func(a, b);

          // Mod 29'u SİLDİK! Sonuç 5 milyon bile çıksa direkt yazdırıyoruz.
          encryptedPreview += `${result} `;
        } catch (e) {
          console.error("Kural hatası:", e);
          encryptedPreview += `[HATA] `;
        }
        resultIndex++;
      } else {
        encryptedPreview += `${char} `; // Boşlukları olduğu gibi bırak
      }
    }
    customEncryptedPreview.textContent = encryptedPreview.trim();
  }

  // GERÇEK ÇÖZÜMLEME (Custom Map Mod - Döngüsel Zincir)
  //
  // Şifreleme sırasında her token, encrypted_i = rule(value_i, value_{i+1})
  // olarak üretilir (value_i = zincirdeki i. harfin keyMappings değeri).
  // Alıcı tarafında bu zinciri geriye çözmek için, zincirin İLK değerinin
  // (value_0) bilindiği varsayılır -- tıpkı bir blok şifredeki paylaşılan bir
  // "IV" (initialization vector) gibi, bu tek değer Alice ve Bob arasında ayrıca
  // (güvenli bir kanaldan) paylaşılmış kabul edilir. Bu bilinen değerden
  // başlayarak, keyMappings'in küçük değer evreninde (tipik olarak <=27 aday)
  // her adım için "rule(value_i, aday) === encrypted_i" koşulunu sağlayan aday
  // taranarak value_{i+1} bulunur ve zincir ileri yönde çözülür.
  //
  // Kuralın ikinci argümanında birebir (enjektif) olmadığı durumlarda bir
  // adımda birden fazla aday bulunabilir; bu durum GİZLENMEZ, sonuçta
  // "ambiguous" olarak işaretlenir ve arayüzde açıkça raporlanır. Zincirin son
  // halkası, döngüsel olarak ilk değere geri kapanmalıdır (value_L === value_0);
  // kapanmazsa şifre çözme "chain-does-not-close" olarak başarısız sayılır.
  function decryptCustomChain(cipherValues, ruleStr, domainValues, knownFirstValue) {
    if (knownFirstValue === null || knownFirstValue === undefined) {
      return { ok: false, reason: "no-known-first-value" };
    }
    let func;
    try {
      func = new Function("a", "b", "return " + ruleStr);
    } catch (e) {
      return { ok: false, reason: "rule-parse-error" };
    }
    const values = [knownFirstValue];
    let ambiguous = false;
    for (let i = 0; i < cipherValues.length; i++) {
      const candidates = [];
      for (const cand of domainValues) {
        try {
          if (func(values[i], cand) === cipherValues[i]) candidates.push(cand);
        } catch (e) {
          /* bu aday kuralı hata verdirdiyse atla */
        }
      }
      if (candidates.length === 0) {
        return { ok: false, reason: "no-consistent-value", step: i };
      }
      if (candidates.length > 1) ambiguous = true;
      const next = candidates[0];
      if (i < cipherValues.length - 1) {
        values.push(next);
      } else if (next !== values[0]) {
        return { ok: false, reason: "chain-does-not-close", step: i };
      }
    }
    return { ok: true, values, ambiguous };
  }

  // Binary Mod: Önizlemeyi güncelleme
  function updateBinaryPreview() {
    const message = binaryMessageInput.value;
    const rule = binaryRuleInput.value;
    let binaryMessage = "";
    let processedBinary = "";

    if (!message) {
      binaryPreviewOutput.innerHTML = "";
      return;
    }

    // Mesajı binary'ye çevir
    for (let i = 0; i < message.length; i++) {
      const charCode = message.charCodeAt(i);
      binaryMessage += charCode.toString(2).padStart(8, "0") + " ";
    }
    binaryMessage = binaryMessage.trim();

    // Binary string'i bit bit işleyelim
    let currentBitString = "";
    for (let i = 0; i < binaryMessage.length; i++) {
      const char = binaryMessage[i];
      if (char === " ") {
        processedBinary += " ";
        continue;
      }
      const bit = parseInt(char); // '0' veya '1'
      let processedBit = bit;
      try {
        // Güvenli eval yerine daha kontrollü bir yaklaşım
        const func = new Function("bit", "return " + rule);
        processedBit = func(bit);
        // Sadece 0 veya 1 olduğundan emin olalım
        processedBit =
          processedBit === 0 || processedBit === 1 ? processedBit : bit ^ 1; // Geçersizse XOR 1 yap
      } catch (e) {
        console.error("Binary kural hatası:", e);
        processedBit = bit ^ 1; // Hata durumunda varsayılan XOR 1
      }
      processedBinary += processedBit;
    }

    binaryPreviewOutput.innerHTML = `
     <p><span class="step-label">${t("enc.originalBinaryLabel", "Orijinal Binary:")}</span> <span class="message-content">${binaryMessage}</span></p>
      <p><span class="step-label">${t("enc.processedBinaryLabel", "Kural Uygulanmış Binary:")}</span> <span class="message-content">${processedBinary.trim()}</span></p>
    `;
  }

  // --- Event Dinleyicileri ---

  // Mod değiştirme
  modeSwitcher.addEventListener("change", () => {
    if (modeSwitcher.checked) {
      customMapMode.classList.add("hidden");
      binaryMode.classList.remove("hidden");
      updateBinaryPreview(); // Binary moda geçince önizlemeyi güncelle
    } else {
      binaryMode.classList.add("hidden");
      customMapMode.classList.remove("hidden");
      updateCustomPreview(); // Custom Map moda geçince önizlemeyi güncelle
    }
  });

  // Custom Map Mod input ve kural değişikliklerini dinle
  customMessageInput.addEventListener("input", updateCustomPreview);
  customRuleInput.addEventListener("input", updateCustomPreview);

  // Custom Map Mod: Şifrele ve Gönder
  sendCustomButton.addEventListener("click", () => {
    const originalMessage = customMessageInput.value;
    const encryptedMessage = customEncryptedPreview.textContent; // Önizlemedeki hali al
    const rule = customRuleInput.value;

    if (!originalMessage || Object.keys(keyMappings).length === 0) {
      alert(
        t(
          "enc.customValidationAlert",
          "Lütfen bir mesaj girin ve en az bir tuşa anahtar atayın.",
        ),
      );
      return;
    }
    if (!rule.trim()) {
      alert(t("enc.ruleValidationAlert", "Lütfen bir kural girin."));
      return;
    }

    // GERÇEK ÇÖZÜMLEME: zincirdeki sayısal token'ları ve harfe geçmeyen
    // (literal) token'ları ayır, bilinen ilk değerden (Alice'in gönderdiği
    // ilk geçerli harfin keyMappings değeri -- IV benzeri paylaşılan tohum)
    // başlayarak zinciri gerçekten decryptCustomChain ile çöz.
    const upperOriginal = originalMessage.toUpperCase();
    const firstValidChar = [...upperOriginal].find(
      (c) => keyMappings[c] !== undefined,
    );
    const knownFirstValue =
      firstValidChar !== undefined ? keyMappings[firstValidChar] : null;

    const tokens = encryptedMessage.split(" ").filter((tok) => tok !== "");
    const cipherValues = [];
    const tokenIsNumeric = [];
    for (const tok of tokens) {
      if (!isNaN(tok)) {
        cipherValues.push(Number(tok));
        tokenIsNumeric.push(true);
      } else {
        tokenIsNumeric.push(false);
      }
    }
    const domainValues = [...new Set(Object.values(keyMappings))];
    const decryptResult = decryptCustomChain(
      cipherValues,
      rule,
      domainValues,
      knownFirstValue,
    );

    let reconstructedMessage = null;
    let ambiguous = false;
    if (decryptResult.ok) {
      ambiguous = decryptResult.ambiguous;
      const valueToChar = {};
      for (const [c, v] of Object.entries(keyMappings)) {
        if (!(v in valueToChar)) valueToChar[v] = c; // birden fazla harf aynı değeri paylaşırsa ilkini kullan (belirsizlik notuyla)
      }
      let vi = 0;
      const chars = tokenIsNumeric.map((isNum) =>
        isNum ? valueToChar[decryptResult.values[vi++]] : null,
      );
      let ti = 0;
      reconstructedMessage = tokens
        .map((tok, idx) => (tokenIsNumeric[idx] ? chars[idx] : tok))
        .join(" ");
    }

    lastCustomResult = {
      originalMessage,
      encryptedMessage,
      rule,
      knownFirstValue,
      decryptResult,
      reconstructedMessage,
      ambiguous,
    };
    renderCustomLog();
    alert(t("enc.successAlert", "Mesaj başarıyla şifrelendi ve gönderildi!"));
  });

  function renderCustomLog() {
    if (!lastCustomResult) return;
    const {
      originalMessage,
      encryptedMessage,
      rule,
      decryptResult,
      reconstructedMessage,
      ambiguous,
    } = lastCustomResult;

    let decryptionLine;
    if (decryptResult && decryptResult.ok) {
      const suffix = ambiguous
        ? ` ${t("enc.decryptAmbiguousNote", "(UYARI: kural enjektif değil; gösterilen çözüm birden fazla olası çözümden biridir.)")}`
        : "";
      decryptionLine = `${t("enc.decryptedResultLabel", "Çözülen Mesaj:")} <strong>${reconstructedMessage}</strong>${suffix}`;
    } else {
      const reason = decryptResult ? decryptResult.reason : "unknown";
      decryptionLine = t(
        "enc.decryptFailedNote",
        "Zincir bu kural ve anahtar haritasıyla çözülemedi (sebep: {reason}) -- kural bu zincirleme için tersine çevrilebilir değil.",
      ).replace("{reason}", reason);
    }

    customCommunicationLog.innerHTML = `
      <div>
        <span class="step-label">${t("enc.sentMessageLabel", "Gönderilen Mesaj (Orijinal):")}</span>
        <span class="message-content">${originalMessage}</span>
      </div>
      <div>
        <span class="step-label">${t("enc.encryptedChainLabel", "Şifrelenmiş Mesaj (Döngüsel Zincir):")}</span>
        <span class="message-content">${encryptedMessage}</span>
      </div>
      <div>
        <span class="step-label">${t("enc.decryptionLabel", "Alıcı Tarafında Çözümleme (Decryption):")}</span>
        <span class="message-content">${t("enc.chainExplain1", 'Bu mesaj <strong>"Döngüsel Zincirleme"</strong> yöntemiyle şifrelenmiştir. Her harf bir sonrakine cebirsel olarak bağlıdır (Örn: a ο b).')}</span>
        <span class="message-content">${t("enc.chainExplain2b", "Alıcı, kuralı ve paylaşılan anahtar haritasını bilir; zincirin bilinen ilk değerinden başlayarak ileri yönde çözer.")}</span>
        <span class="message-content">${decryptionLine}</span>
      </div>
    `;
  }

  // Binary Mod input ve kural değişikliklerini dinle
  binaryMessageInput.addEventListener("input", updateBinaryPreview);
  binaryRuleInput.addEventListener("input", updateBinaryPreview);

  // Binary Mod: Şifrele ve Gönder
  sendBinaryButton.addEventListener("click", () => {
    const originalMessage = binaryMessageInput.value;
    const rule = binaryRuleInput.value;

    if (!originalMessage) {
      alert(t("enc.messageValidationAlert", "Lütfen bir mesaj girin."));
      return;
    }
    if (!rule.trim()) {
      alert(t("enc.ruleValidationAlert", "Lütfen bir kural girin."));
      return;
    }

    // Binary preview'den işlenmiş binary mesajı alalım
    // NOT: ":last-of-type" burada YANLIŞ sonuç veriyordu -- her ".message-content"
    // span'ı kendi ayrı <p> ebeveyni içinde bulunduğundan, ikisi de kendi
    // ebeveyni içinde "son (ve tek) örnek" sayılıyor ve querySelector,
    // DOM sırasına göre İLK eşleşeni (yani orijinal/şifrelenmemiş binary'yi)
    // döndürüyordu. Bunun yerine tüm ".message-content" span'larını alıp
    // GERÇEKTEN sonuncusunu (Kural Uygulanmış Binary) seçiyoruz.
    const previewContents = binaryPreviewOutput.querySelectorAll(
      ".message-content",
    );
    const previewContent =
      previewContents.length > 0
        ? previewContents[previewContents.length - 1]
        : null;
    const processedBinaryMessage = previewContent
      ? previewContent.textContent
      : "";

    // GERÇEK ÇÖZÜMLEME: alıcı, şifreli bitlere AYNI bit-kuralını yeniden
    // uygular. Bu, kuralın kendi kendinin tersi (bir involution) olduğu
    // durumlarda -- örn. varsayılan "bit ^ 1" kuralı -- orijinal mesajı
    // matematiksel olarak doğru biçimde geri verir (involution ^ involution =
    // özdeşlik). Kural involutif DEĞİLSE (örn. sabit "0" döndüren bir kural),
    // bu ikinci uygulama orijinali geri getirmez; bu durum gizlenmeden,
    // yeniden hesaplanan sonucun orijinal mesajla eşleşip eşleşmediği
    // karşılaştırılarak arayüzde açıkça raporlanır.
    let reDecryptedBinary = "";
    for (let i = 0; i < processedBinaryMessage.length; i++) {
      const ch = processedBinaryMessage[i];
      if (ch === " ") {
        reDecryptedBinary += " ";
        continue;
      }
      const bit = parseInt(ch);
      let recoveredBit = bit;
      try {
        const func = new Function("bit", "return " + rule);
        recoveredBit = func(bit);
        recoveredBit =
          recoveredBit === 0 || recoveredBit === 1 ? recoveredBit : bit ^ 1;
      } catch (e) {
        recoveredBit = bit ^ 1;
      }
      reDecryptedBinary += recoveredBit;
    }

    let decryptedMessage = "";
    const binaryBlocks = reDecryptedBinary
      .split(" ")
      .filter((block) => block.length === 8);
    for (const block of binaryBlocks) {
      const charCode = parseInt(block, 2);
      decryptedMessage += String.fromCharCode(charCode);
    }
    const isInvolution = decryptedMessage === originalMessage;

    lastBinaryResult = {
      originalMessage,
      processedBinaryMessage,
      decryptedMessage,
      isInvolution,
    };
    renderBinaryLog();
    alert(t("enc.successAlert", "Mesaj başarıyla şifrelendi ve gönderildi!"));
  });

  function renderBinaryLog() {
    if (!lastBinaryResult) return;
    const {
      originalMessage,
      processedBinaryMessage,
      decryptedMessage,
      isInvolution,
    } = lastBinaryResult;
    const involutionNote = isInvolution
      ? ""
      : ` ${t("enc.binaryNotInvolutionNote", "(UYARI: bu kural involution değil; aynı kuralı yeniden uygulamak orijinal mesajı geri vermedi.)")}`;
    binaryCommunicationLog.innerHTML = `
      <div>
        <span class="step-label">${t("enc.sentTextMessageLabel", "Gönderilen Mesaj (Orijinal Metin):")}</span>
        <span class="message-content">${originalMessage}</span>
      </div>
      <div>
        <span class="step-label">${t("enc.encryptedBinaryLabel", "Şifrelenmiş Mesaj (Binary):")}</span>
        <span class="message-content">${processedBinaryMessage}</span>
      </div>
      <div>
        <span class="step-label">${t("enc.recipientDecryptionLabel", "Alıcı Tarafında Çözümleme:")}</span>
        <span class="message-content">${t("enc.binaryDecryptExplain2", "Alıcı aynı bit-kuralını şifreli bitlere yeniden uygular; kural involutifse (örn. varsayılan XOR 1) bu orijinali geri verir.")}</span>
        <span class="message-content">${t("enc.decryptedTextLabel", "Çözülen Mesaj (Metin):")} <strong>${decryptedMessage}</strong>${involutionNote}</span>
      </div>
    `;
  }

  // --- Başlangıç Durumu ---
  createQKeyboard(); // Custom Map modu için klavyeyi oluştur
  updateCustomPreview(); // İlk yüklemede önizlemeyi güncelle

  // Dil değişince: önizlemeleri ve varsa iletişim log'larını yeniden çiz
  document.addEventListener("languageChanged", function () {
    if (binaryMessageInput.value) updateBinaryPreview();
    renderCustomLog();
    renderBinaryLog();
  });
});

// Pop-up functions
function openPopup() {
  const popup = document.getElementById("infoPopup");
  popup.classList.add("show");
  document.body.style.overflow = "hidden"; // Prevent background scrolling
}

function closePopup() {
  const popup = document.getElementById("infoPopup");
  popup.classList.remove("show");
  document.body.style.overflow = "auto"; // Restore scrolling
}

// Close popup when clicking outside
document.addEventListener("click", function (event) {
  const popup = document.getElementById("infoPopup");
  if (event.target === popup) {
    closePopup();
  }
});

// Close popup with Escape key
document.addEventListener("keydown", function (event) {
  if (event.key === "Escape") {
    closePopup();
  }
});
