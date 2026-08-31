/**
 * HyperCrypt - basit i18n (çoklu dil) motoru
 * ------------------------------------------
 * Kullanım: Çevrilecek elemana data-i18n="anahtar" ekle.
 * Bu script o elemanın içeriğini, seçili dile göre
 * lang/tr.json veya lang/en.json'daki karşılığıyla değiştirir.
 */
(function () {
  var SUPPORTED_LANGS = ["tr", "en"];
  var DEFAULT_LANG = "tr";
  var STORAGE_KEY = "hc_lang";

  var dict = {};
  var currentLang = localStorage.getItem(STORAGE_KEY) || DEFAULT_LANG;
  if (SUPPORTED_LANGS.indexOf(currentLang) === -1) currentLang = DEFAULT_LANG;

  // Bu script her sayfada .../Frontend/<SayfaKlasoru>/ içinden çağrılacağı
  // için lang klasörüne yol her zaman bir üst dizinden.
  function langBasePath() {
    return "../lang/";
  }

  function fetchDict(lang) {
    return fetch(langBasePath() + lang + ".json").then(function (res) {
      if (!res.ok) throw new Error("Dil dosyası yüklenemedi: " + lang);
      return res.json();
    });
  }

  function applyTranslations() {
    document.querySelectorAll("[data-i18n]").forEach(function (el) {
      var key = el.getAttribute("data-i18n");
      if (Object.prototype.hasOwnProperty.call(dict, key)) {
        if (el.tagName === "TITLE") {
          document.title = dict[key];
        } else {
          el.innerHTML = dict[key];
        }
      }
    });

    // placeholder / title / aria-label gibi attribute çevirileri
    document.querySelectorAll("[data-i18n-attr]").forEach(function (el) {
      var spec = el.getAttribute("data-i18n-attr");
      spec.split(";").forEach(function (pair) {
        var parts = pair.split(":");
        var attr = parts[0];
        var key = parts[1];
        if (attr && key && Object.prototype.hasOwnProperty.call(dict, key)) {
          el.setAttribute(attr, dict[key]);
        }
      });
    });

    document.documentElement.lang = currentLang;

    // Eğer sayfada MathJax varsa (örn. Hiperhalkalar sayfası),
    // çeviri sonrası formülleri yeniden işlet
    if (window.MathJax && typeof window.MathJax.typesetPromise === "function") {
      window.MathJax.typesetPromise();
    }
  }

  function setLanguage(lang) {
    if (SUPPORTED_LANGS.indexOf(lang) === -1) lang = DEFAULT_LANG;
    return fetchDict(lang).then(function (data) {
      dict = data;
      currentLang = lang;
      localStorage.setItem(STORAGE_KEY, lang);
      applyTranslations();
      document.dispatchEvent(
        new CustomEvent("languageChanged", { detail: { lang: lang } }),
      );
    });
  }

  // JS içinden dinamik metin çevirisi için: t('anahtar', 'varsayılan metin')
  window.t = function (key, fallback) {
    if (Object.prototype.hasOwnProperty.call(dict, key)) return dict[key];
    return fallback !== undefined ? fallback : key;
  };

  window.HyperCryptI18n = {
    setLanguage: setLanguage,
    getLanguage: function () {
      return currentLang;
    },
  };

  // Dil değiştirici butonlara tıklamayı dinle
  document.addEventListener("click", function (e) {
    var btn = e.target.closest(".lang-switch-btn");
    if (btn) {
      var lang = btn.getAttribute("data-lang");
      setLanguage(lang);
    }
  });

  // header/footer sonradan DOM'a eklendiğinde çeviriyi tekrar uygula
  document.addEventListener("partialsLoaded", function () {
    applyTranslations();
  });

  document.addEventListener("DOMContentLoaded", function () {
    setLanguage(currentLang);
  });
})();
