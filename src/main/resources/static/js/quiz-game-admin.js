/** Показывает типизированный источник формы игры. Сервер сохраняет и проверяет только выбранный тип. */
(() => {
    const type = document.querySelector('[data-game-type]');
    if (!type) return;
    const sections = document.querySelectorAll('[data-game-source]');
    /** Скрытые настройки не отправляются; без JS сервер всё равно не принимает чужую типовую конфигурацию. */
    function showSelectedSource() {
        sections.forEach(section => {
            const selected = section.dataset.gameSource === type.value;
            section.hidden = !selected;
            section.querySelectorAll('input, select, textarea').forEach(field => { field.disabled = !selected; });
        });
    }
    type.addEventListener('change', showSelectedSource);
    showSelectedSource();
})();
