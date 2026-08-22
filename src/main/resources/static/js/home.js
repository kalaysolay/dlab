(() => {
    const slider = document.querySelector("[data-home-slider]");
    if (!slider) {
        return;
    }

    const slides = Array.from(slider.querySelectorAll("[data-home-slide]"));
    const dots = Array.from(slider.querySelectorAll("[data-home-slide-to]"));
    const counter = slider.querySelector("[data-home-slide-counter]");
    const previousButton = slider.querySelector("[data-home-slide-prev]");
    const nextButton = slider.querySelector("[data-home-slide-next]");
    let activeIndex = 0;

    /**
     * Переключает только презентационные слайды на публичной главной.
     * Автопрокрутки намеренно нет: пользователь сам управляет контентом,
     * а страница не создаёт лишнего движения и не зависит от таймеров.
     */
    function showSlide(requestedIndex) {
        activeIndex = (requestedIndex + slides.length) % slides.length;

        slides.forEach((slide, index) => {
            const active = index === activeIndex;
            slide.classList.toggle("is-active", active);
            slide.setAttribute("aria-hidden", String(!active));
        });

        dots.forEach((dot, index) => {
            const active = index === activeIndex;
            dot.classList.toggle("is-active", active);
            dot.setAttribute("aria-pressed", String(active));
        });

        if (counter) {
            const current = String(activeIndex + 1).padStart(2, "0");
            const total = String(slides.length).padStart(2, "0");
            counter.textContent = `${current} / ${total}`;
        }
    }

    dots.forEach((dot) => {
        dot.addEventListener("click", () => showSlide(Number(dot.dataset.homeSlideTo)));
    });

    previousButton?.addEventListener("click", () => showSlide(activeIndex - 1));
    nextButton?.addEventListener("click", () => showSlide(activeIndex + 1));
})();
