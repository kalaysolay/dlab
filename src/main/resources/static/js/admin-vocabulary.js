/* Редактор банка слов использует существующие SSR-формы и POST с CSRF, без отдельного API. */
(() => {
    'use strict';
    const dialog = document.getElementById('vocabulary-dialog');
    if (!dialog) return;
    const body = dialog.querySelector('[data-vocabulary-body]');
    const footer = dialog.querySelector('[data-vocabulary-footer]');
    const error = dialog.querySelector('[data-vocabulary-error]');
    const previous = dialog.querySelector('[data-vocabulary-prev]');
    const next = dialog.querySelector('[data-vocabulary-next]');
    let links = [];
    let index = 0;
    let currentUrl = '';
    let opener = null;
    let baseline = '';
    let busy = false;
    let tableChanged = false;

    // Сравниваем все формы: изменение аудитории и запрета пары тоже нельзя незаметно потерять.
    const snapshot = () => JSON.stringify(Array.from(body.querySelectorAll('form'),
        form => Array.from(new FormData(form).entries())));
    const dirty = () => baseline !== '' && snapshot() !== baseline;
    const canLeave = () => !busy && (!dirty() || window.confirm('Изменения не сохранены. Отменить их?'));

    function controls() {
        previous.disabled = busy || index <= 0;
        next.disabled = busy || index >= links.length - 1;
        dialog.querySelector('[data-vocabulary-position]').textContent = `${index + 1} / ${links.length}`;
        dialog.querySelectorAll('[data-vocabulary-close]').forEach(button => {
            if (button.tagName === 'BUTTON') button.disabled = busy;
        });
        body.inert = busy;
        footer.inert = busy;
        body.setAttribute('aria-busy', String(busy));
    }

    function showError(message) {
        error.textContent = message;
        error.hidden = false;
    }

    async function page(url, options) {
        const response = await fetch(url, { credentials: 'same-origin', ...options });
        if (!response.ok) throw new Error('Не удалось выполнить запрос. Проверьте соединение и повторите.');
        const document = new DOMParser().parseFromString(await response.text(), 'text/html');
        // Истёкшая сессия и отказ в доступе не должны выглядеть как успешное сохранение.
        if (!document.querySelector('#vocabulary-items, form.wide-form')) {
            throw new Error('Редактор недоступен. Возможно, нужно заново войти в аккаунт.');
        }
        return document;
    }

    function render(document) {
        const form = document.querySelector('form.wide-form');
        if (!form) throw new Error('Не удалось загрузить форму слова.');
        const panel = form.closest('section.panel');
        panel.querySelector('.page-head').remove();
        // В списке уже есть форма набора с sourceReference: label модалки должен вести к своему полю.
        panel.querySelectorAll('[id]').forEach(element => { element.id = `vocabulary-modal-${element.id}`; });
        panel.querySelectorAll('label[for]').forEach(label => { label.htmlFor = `vocabulary-modal-${label.htmlFor}`; });
        form.id = 'vocabulary-edit-form';
        const actions = form.querySelector('.actions');
        actions.querySelector('[type="submit"]').setAttribute('form', form.id);
        footer.replaceChildren(actions);
        body.replaceChildren(...Array.from(panel.childNodes));
        baseline = snapshot();
        const word = body.querySelector('[name="sourceText"]');
        dialog.querySelector('h2').textContent = word && word.value ? `Редакция: ${word.value}` : 'Редакция слова';
        body.scrollTop = 0;
    }

    async function open(url, position) {
        busy = true;
        error.hidden = true;
        controls();
        if (!dialog.open) {
            dialog.showModal();
            document.body.classList.add('modal-open');
        }
        try {
            const document = await page(url);
            render(document);
            currentUrl = url;
            index = position;
        } catch (failure) {
            showError(failure.message);
        } finally {
            busy = false;
            controls();
            if (!error.hidden) error.setAttribute('tabindex', '-1');
            (error.hidden ? body.querySelector('[name="sourceText"]') : error)?.focus({ preventScroll: true });
        }
    }

    async function refreshTable() {
        const updated = await page(window.location.href);
        const table = updated.querySelector('#vocabulary-items');
        if (!table) throw new Error('Запись сохранена, но таблицу обновить не удалось. Обновите страницу.');
        const selected = new Set(Array.from(document.querySelectorAll('#vocabulary-items input:checked'), input => input.value));
        table.querySelectorAll('input[type="checkbox"]').forEach(input => { input.checked = selected.has(input.value); });
        const scroll = window.scrollY;
        const horizontal = document.querySelector('#vocabulary-items').scrollLeft;
        document.querySelector('#vocabulary-items').replaceWith(table);
        table.scrollLeft = horizontal;
        window.scrollTo(window.scrollX, scroll);
        tableChanged = true;
    }

    document.addEventListener('click', event => {
        const link = event.target.closest('[data-vocabulary-edit]');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        if (!canLeave()) return;
        links = Array.from(document.querySelectorAll('#vocabulary-items .vocabulary-word'), anchor => anchor.href);
        opener = link;
        body.textContent = 'Загрузка записи…';
        footer.replaceChildren();
        baseline = '';
        tableChanged = false;
        index = links.indexOf(link.href);
        open(link.href, index);
    });

    dialog.addEventListener('click', event => {
        if (event.target.closest('[data-vocabulary-close]')) {
            event.preventDefault();
            if (canLeave()) dialog.close();
        }
    });
    previous.addEventListener('click', () => { if (canLeave()) open(links[index - 1], index - 1); });
    next.addEventListener('click', () => { if (canLeave()) open(links[index + 1], index + 1); });
    dialog.addEventListener('cancel', event => { if (!canLeave()) event.preventDefault(); });
    dialog.addEventListener('close', () => {
        document.body.classList.remove('modal-open');
        baseline = '';
        // После замены строк исходная ссылка уже отсоединена: восстанавливаем фокус по URL.
        const target = opener?.isConnected ? opener : Array.from(document.querySelectorAll('[data-vocabulary-edit]')).find(link => link.href === currentUrl);
        if (target) target.focus({ preventScroll: true });
        else if (tableChanged) document.querySelector('.filter-bar button')?.focus({ preventScroll: true });
    });
    window.addEventListener('beforeunload', event => {
        if (dialog.open && (dirty() || busy)) { event.preventDefault(); event.returnValue = ''; }
    });

    body.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy) return;
        const form = event.target;
        const data = new URLSearchParams(new FormData(form));
        const originalBaseline = baseline;
        busy = true;
        error.hidden = true;
        controls();
        let saved = false;
        try {
            // Fetch следует штатному redirect обратно к форме: ошибки и flash остаются серверными.
            const document = await page(form.action, { method: 'POST', body: data });
            render(document);
            saved = !body.querySelector('.alert.danger');
            if (!saved) baseline = originalBaseline;
            if (saved) await refreshTable();
        } catch (failure) {
            showError(saved ? 'Запись сохранена, но таблицу обновить не удалось. Обновите страницу.' : failure.message);
        } finally {
            busy = false;
            controls();
            const notice = body.querySelector('.alert.danger, .alert.success') || error;
            if (!notice.hidden) { notice.tabIndex = -1; notice.focus(); }
        }
    });
})();
