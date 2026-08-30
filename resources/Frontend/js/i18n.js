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
        el.innerHTML = dict[key];
      }
    });
    document.documentElement.lang = currentLang;
  }

  function setLanguage(lang) {
    if (SUPPORTED_LANGS.indexOf(lang) === -1) lang = DEFAULT_LANG;
    return fetchDict(lang).then(function (data) {
      dict = data;
      currentLang = lang;
      localStorage.setItem(STORAGE_KEY, lang);
      applyTranslations();
    });
  }

  window.HyperCryptI18n = {
    setLanguage: setLanguage,
    getLanguage: function () {
      return currentLang;
    },
  };

  // header/footer sonradan DOM'a eklendiğinde çeviriyi tekrar uygula
  document.addEventListener("partialsLoaded", function () {
    applyTranslations();
  });

  document.addEventListener("DOMContentLoaded", function () {
    setLanguage(currentLang);
  });
})();
