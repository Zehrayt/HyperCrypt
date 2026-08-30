document.addEventListener("DOMContentLoaded", () => {
  const basePath = "../"; // Anasayfa -> Frontend

  const headerLoaded = fetch(basePath + "header/header.html")
    .then((res) => res.text())
    .then((data) => {
      document.getElementById("header").innerHTML = data;
      const toggleBtn = document.querySelector(".menu-toggle");
      const nav = document.querySelector("#primary-navigation");
      if (toggleBtn && nav) {
        toggleBtn.addEventListener("click", () => {
          const isActive = nav.classList.toggle("active");
          toggleBtn.setAttribute("aria-expanded", isActive ? "true" : "false");

          // Header'ın yüksekliğini ayarla (mobilde)
          if (window.matchMedia("(max-width: 768px)").matches) {
            const header = document.querySelector("header");
            if (isActive) {
              header.style.flexWrap = "wrap";
            } else {
              // Kısa bir gecikme ile wrap özelliğini kaldır
              setTimeout(() => {
                header.style.flexWrap = "nowrap";
              }, 300);
            }
          }
        });

        // Menü açıkken linke tıklanınca kapat
        nav.querySelectorAll("a").forEach((link) => {
          link.addEventListener("click", () => {
            if (window.matchMedia("(max-width: 768px)").matches) {
              nav.classList.remove("active");
              toggleBtn.setAttribute("aria-expanded", "false");
              const header = document.querySelector("header");
              setTimeout(() => {
                header.style.flexWrap = "nowrap";
              }, 300);
            }
          });
        });

        // Ekran boyutu değiştiğinde menüyü kapat ve header'ı sıfırla
        window.addEventListener("resize", () => {
          if (window.matchMedia("(min-width: 769px)").matches) {
            nav.classList.remove("active");
            toggleBtn.setAttribute("aria-expanded", "false");
            const header = document.querySelector("header");
            header.style.flexWrap = "nowrap";
          }
        });
      }
    })
    .catch((err) => console.error("Header yüklenemedi:", err));

  const footerLoaded = fetch(basePath + "footer/footer.html")
    .then((res) => res.text())
    .then((data) => (document.getElementById("footer").innerHTML = data))
    .catch((err) => console.error("Footer yüklenemedi:", err));

  // Header ve footer eklendikten sonra i18n.js'e haber ver
  Promise.all([headerLoaded, footerLoaded]).then(() => {
    document.dispatchEvent(new CustomEvent("partialsLoaded"));
  });
});
