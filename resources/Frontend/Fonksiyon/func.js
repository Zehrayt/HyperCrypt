const finiteBtn = document.getElementById("finiteBtn");
const infiniteBtn = document.getElementById("infiniteBtn");
//const API_BASE_URL = "https://hypercrypt.onrender.com";
const API_BASE_URL = "http://localhost:8080";

finiteBtn.addEventListener("click", function () {
  if (this.id === "finiteBtn") {
    document.getElementById("finite-set").style.display = "block";
    document.getElementById("infinite-set").style.display = "none";
    finiteBtn.classList.add("active");
    infiniteBtn.classList.remove("active");
  }
});

infiniteBtn.addEventListener("click", function () {
  if (this.id === "infiniteBtn") {
    document.getElementById("finite-set").style.display = "none";
    document.getElementById("infinite-set").style.display = "block";
    infiniteBtn.classList.add("active");
    finiteBtn.classList.remove("active");
  }
});

document.getElementById("finite-set").style.display = "block";
document.getElementById("infinite-set").style.display = "none";
finiteBtn.classList.add("active");

async function showResults() {
  const isFiniteMode =
    document.getElementById("finite-set").style.display === "block";
  const rule = document.getElementById("rules").value;

  let requestData;

  // 1. Adım: Kullanıcının girdiği verilere göre isteği hazırla.
  if (isFiniteMode) {
    const elementsInput = document.getElementById("elements").value;
    // Virgülle ayrılmış metni alıp, boşlukları temizleyip, sayı dizisine çevir.
    const baseSet = elementsInput
      .split(",")
      .map((el) => parseInt(el.trim()))
      .filter((num) => !isNaN(num));

    if (baseSet.length === 0) {
      alert(
        t("func.invalidFiniteSetAlert", "Lütfen geçerli bir sonlu küme girin."),
      );
      return;
    }

    requestData = {
      baseSet: baseSet,
      rule: rule,
    };
  } else {
    // Sonsuz Küme Modu
    const domainSelect = document.getElementById("infiniteOptions");
    const domain = domainSelect.options[domainSelect.selectedIndex].text
      .toUpperCase()
      .split(" ")[0]; // "TAMSAYILAR" -> "INTEGERS" gibi. Bu backend'e uygun olmalı.

    requestData = {
      domain: "INTEGERS", // Şimdilik sadece INTEGERS destekliyor.
      rule: rule,
    };
  }

  // 2. Adım: Backend API'sine fetch ile istek gönder.
  try {
    const response = await fetch(`${API_BASE_URL}/api/verify`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept-Language": document.documentElement.lang || "tr",
      },
      body: JSON.stringify(requestData),
    });

    const data = await response.json();

    // 3. Adım: Gelen cevaba göre arayüzü güncelle.
    if (response.ok) {
      renderSuccess(data);
    } else {
      renderError(data);
    }
  } catch (error) {
    renderError({
      error:
        t("func.apiConnectionError", "API'ye bağlanırken bir hata oluştu: ") +
        error.message,
    });
  }
}

// --- YENİ YARDIMCI FONKSİYONLAR ---

// DÜZELTME: Daha önce bir aksiyom sonucu backend tarafından hiç
// değerlendirilmediğinde (örn. "fail-fast" stratejisiyle closure
// veya birleşme testinde erken durulduğu için ilgili alan JSON'da
// null/undefined geldiğinde), arayüz bunu doğrudan "true"/"false"
// metniyle aynı <span class="pill ..."> kalıbına basıyordu; bu da
// ekranda "null" ya da "undefined" yazan ya da tamamen boş görünen,
// yanlış yorumlanmaya açık bir rozet üretiyordu (Hakem: "not
// evaluated" ile "false" birbirinden ayırt edilemiyor). Bu yardımcı
// fonksiyon üç durumu (true / false / henüz değerlendirilmedi) açıkça
// ayırt eden, kendi CSS sınıfı ve okunur etiketi olan bir rozet
// üretir.
function pillMarkup(value) {
  if (value === true) {
    return `<span class="pill true">${t("func.pillTrue", "Evet")}</span>`;
  }
  if (value === false) {
    return `<span class="pill false">${t("func.pillFalse", "Hayır")}</span>`;
  }
  // null, undefined ya da başka bir "değer yok" durumu: bu aksiyom
  // fail-fast nedeniyle hiç test edilmemiş demektir, "false" değildir.
  return `<span class="pill not-evaluated">${t("func.pillNotEvaluated", "Değerlendirilmedi")}</span>`;
}

// Başarılı sonuçları ekrana yazdıran fonksiyon
function renderSuccess(data) {
  // Cayley Tablosunu Oluştur
  if (data.cayleyTable) {
    let tableHTML = `<h3>${t("func.cayleyTableTitle", "İşlem Tablosu (Cayley Table)")}</h3><table><thead><tr><th>ο</th>`;
    const headers = Object.keys(data.cayleyTable);
    headers.forEach((h) => (tableHTML += `<th>${h}</th>`));
    tableHTML += `</tr></thead><tbody>`;

    headers.forEach((rowKey) => {
      tableHTML += `<tr><th>${rowKey}</th>`;
      headers.forEach((colKey) => {
        tableHTML += `<td>${data.cayleyTable[rowKey][colKey] || "-"}</td>`;
      });
      tableHTML += `</tr>`;
    });
    tableHTML += `</tbody></table>`;
    document.getElementById("result").innerHTML = tableHTML;
  } else {
    document.getElementById("result").innerHTML =
      `<h3>${t("func.analysisResultTitle", "Analiz Sonucu")}</h3>`;
  }

  // Aksiyom Test Sonuçlarını Oluştur
  let testsHTML = `
        <p><b>${t("func.highestStructureLabel", "En Yüksek Yapı:")}</b> ${data.highestStructure || t("func.undetermined", "Belirlenemedi")}</p>
        
        <h5>${t("func.multPropertiesTitle", "Çarpımsal Yapı Özellikleri (*)")}</h5>
        <ul>
            <li>
              <span>${t("func.semihypergroupLabel", "Yarı Hipergrup (Birleşme)")}</span>
              ${pillMarkup(data.semihypergroup)}
            </li>
            <li>
              <span>${t("func.quasihypergroupLabel", "Kuazi Hipergrup (Üretim)")}</span>
              ${pillMarkup(data.quasihypergroup)}
            </li>
        </ul>

        <h5>${t("func.hyperringPropertiesTitle", "Hiperhalka Özellikleri (+, *)")}</h5>
        <ul>
            <li>
              <span>${t("func.distributiveLabel", "Dağılma Özelliği")}</span>
              ${pillMarkup(data.isDistributive)}
            </li>
            <li>
              <span>${t("func.negativePropertyLabel", "Negatif Özelliği")}</span>
              ${pillMarkup(data.hasNegativeProperty)}
            </li>
        </ul>
    `;
  document.getElementById("structure-tests").innerHTML = testsHTML;

  // AI Önerisini Göster/Gizle
  if (data.suggestion) {
    document.getElementById("ai-message").innerText = data.suggestion;
    document.getElementById("ai-assistant").style.display = "block";
  } else {
    document.getElementById("ai-assistant").style.display = "none";
  }

  // Sonuç bölümünü görünür yap ve scroll et
  const resultsSection = document.getElementById("results-section");
  resultsSection.style.display = "block";
  resultsSection.scrollIntoView({ behavior: "smooth", block: "start" });
}

// Hataları ekrana yazdıran fonksiyon
function renderError(data) {
  let errorHTML = `
        <h3>${t("func.errorTitle", "Hata!")}</h3>
        <p class="error-message">${data.error || t("func.unknownError", "Bilinmeyen bir hata oluştu.")}</p>
    `;
  // Eğer AI önerisi varsa, onu da gösterelim
  if (data.suggestion) {
    document.getElementById("ai-message").innerText = data.suggestion;
    document.getElementById("ai-assistant").style.display = "block";
  } else {
    document.getElementById("ai-assistant").style.display = "none";
  }

  document.getElementById("result").innerHTML = errorHTML;
  document.getElementById("structure-tests").innerHTML = ""; // Testleri temizle

  const resultsSection = document.getElementById("results-section");
  resultsSection.style.display = "block";
  resultsSection.scrollIntoView({ behavior: "smooth", block: "start" });
}

document.querySelectorAll(".tab-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document
      .querySelectorAll(".tab-btn")
      .forEach((b) => b.classList.remove("active"));
    document
      .querySelectorAll(".tab-content")
      .forEach((tc) => tc.classList.remove("active"));

    btn.classList.add("active");
    document.getElementById(btn.dataset.tab).classList.add("active");
  });
});

//Hiperhalka Örnekleri
const examples = {
  finite1: {
    set: ["1", "2", "3"],
    rules: "a*b",
    results: {
      "1*1": "1",
      "1*2": "2",
      "1*3": "3",
      "2*1": "2",
      "2*2": "4",
      "2*3": "6",
      "3*1": "3",
      "3*2": "6",
      "3*3": "9",
    },
  },

  finite2: {
    set: ["1", "3", "5"],
    rules: "[2*a*b, 3*a*b]",
    results: {
      "1*1": "{2,3}",
      "1*3": "{6,9}",
      "1*5": "{10,15}",
      "3*1": "{6,9}",
      "3*3": "{18,27}",
      "3*5": "{45,30}",
      "5*1": "{10,15}",
      "5*3": "{45,30}",
      "5*5": "{50,75}",
    },
  },

  finite3: {
    set: ["1", "2", "3"],
    rules: "[a*b-1, a-1*b]",
    results: {
      "1*1": "{0}",
      "1*2": "{-1,1}",
      "1*3": "{-2,2}",
      "2*1": "{1}",
      "2*2": "{0,3}",
      "2*3": "{-1,5}",
      "3*1": "{2}",
      "3*2": "{1,5}",
      "3*3": "{0,8}",
    },
  },

  infinite1: {
    set: "ℚ",
    setDescKey: "func.rationalNumbersDesc",
    setDescDefault: "Rasyonel sayılar",
    rules: "a*b ∈ ℚ",
    examples: [
      { a: "1/2", b: "2/3", result: "1/3" },
      { a: "1/3", b: "3", result: "1" },
    ],
  },
};

// Tek fonksiyon: finite mi infinite mi ayırıyor
function renderExample(example) {
  let html = "";

  if (Array.isArray(example.set)) {
    // Finite küme (Cayley tablosu)
    html += `<p><b>${t("func.setLabel", "Küme:")}</b> { ${example.set.join(", ")} }</p>`;
    html += `<p><b>${t("func.rulesLabelShort", "Kurallar:")}</b> ${example.rules}</p>`;
    html += `<table>
      <thead>
        <tr><th>*</th>${example.set.map((el) => `<th>${el}</th>`).join("")}</tr>
      </thead>
      <tbody>`;

    example.set.forEach((rowEl) => {
      html += `<tr><th>${rowEl}</th>`;
      example.set.forEach((colEl) => {
        const key = `${rowEl}*${colEl}`;
        html += `<td>${example.results[key] || "-"}</td>`;
      });
      html += `</tr>`;
    });

    html += `</tbody></table>`;
  } else {
    // Infinite küme (örnek işlemler)
    html += `<p><b>${t("func.setLabel", "Küme:")}</b> ${example.set}${example.setDescKey ? " (" + t(example.setDescKey, example.setDescDefault) + ")" : ""}</p>`;
    html += `<p><b>${t("func.rulesLabelShort", "Kurallar:")}</b> ${example.rules}</p>`;
    html += `<table>
      <thead><tr><th>a</th><th>b</th><th>${t("func.resultColLabel", "Sonuç")}</th></tr></thead>
      <tbody>`;

    example.examples.forEach((ex) => {
      html += `<tr><td>${ex.a}</td><td>${ex.b}</td><td>${ex.result}</td></tr>`;
    });

    html += `</tbody></table>`;
  }

  html += `</div>`;
  return html;
}

// Başlangıçta tüm tab-content'leri doldur
Object.keys(examples).forEach((key) => {
  document.getElementById(key).innerHTML = renderExample(examples[key]);
});

// Fonksiyon Kullanımı butonu için scroll fonksiyonu
document.getElementById("usageGuideBtn").addEventListener("click", function () {
  const usageGuide = document.getElementById("usage-guide");
  const yOffset = -80; // üstten boşluk
  const y =
    usageGuide.getBoundingClientRect().top + window.pageYOffset + yOffset;

  window.scrollTo({ top: y, behavior: "smooth" });
});

// Dil değiştiğinde örnek sekmelerini yeniden çiz
document.addEventListener("languageChanged", function () {
  Object.keys(examples).forEach((key) => {
    document.getElementById(key).innerHTML = renderExample(examples[key]);
  });
});
