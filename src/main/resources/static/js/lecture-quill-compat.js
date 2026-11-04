/* Сохраняет семантику таблиц и формул при преобразовании HTML в модель Quill 2.
 * Расширения регистрируются до создания редакторов, чтобы undo/redo и повторное
 * открытие использовали те же данные, что и обычное редактирование. */
(function () {
    const Quill = window.Quill;
    if (typeof Quill !== 'function') return;
    const Parchment = Quill.import('parchment');
    const Formula = Quill.import('formats/formula');
    const Video = Quill.import('formats/video');
    const Delta = Quill.import('delta');

    // Quill знает только TD. Отдельный формат строки сохраняет признак заголовка
    // на ячейке; сервер превращает её обратно в TH, не полагаясь на жирный шрифт.
    Quill.register(new Parchment.Attributor('lecture-header', 'data-lecture-header', {
        scope: Parchment.Scope.BLOCK_ATTRIBUTE,
        whitelist: ['true']
    }), true);
    // Стандартная ячейка Quill знает только номер строки. Ограниченные атрибуты
    // сохраняют объединения в Delta и истории изменений, не заменяя таблицу
    // непрозрачной картинкой. Scope переносится через TD обратно в TH на сервере.
    for (const attribute of ['colspan', 'rowspan']) {
        Quill.register(new Parchment.Attributor(`lecture-${attribute}`, attribute, {
            scope: Parchment.Scope.BLOCK_ATTRIBUTE,
            whitelist: Array.from({length: 32}, (_, index) => String(index + 1))
        }), true);
    }
    Quill.register(new Parchment.Attributor('lecture-scope', 'data-lecture-scope', {
        scope: Parchment.Scope.BLOCK_ATTRIBUTE,
        whitelist: ['col', 'row', 'colgroup', 'rowgroup']
    }), true);
    for (const [name, attribute, whitelist] of [
        ['lecture-tone', 'data-lecture-tone', ['definition', 'example', 'warning', 'summary']],
        ['lecture-part', 'data-lecture-part', ['prompt', 'answer']],
        ['lecture-question', 'data-lecture-question', undefined],
        ['lecture-section', 'data-lecture-section', ['goals', 'sources']]
    ]) {
        // Это форматы строк, а не непрозрачные embeds: текст вопроса, ответа и
        // выделения можно исправлять обычными средствами визуального редактора.
        Quill.register(new Parchment.Attributor(name, attribute, {
            scope: Parchment.Scope.BLOCK_ATTRIBUTE, ...(whitelist ? {whitelist} : {})
        }), true);
    }

    class LectureFormula extends Formula {
        static create(value) {
            const latex = typeof value === 'string' ? value : value.latex;
            const node = super.create(latex);
            const display = typeof value === 'object' && value.display === 'block';
            node.setAttribute('data-display', display ? 'block' : 'inline');
            window.katex.render(latex, node, {throwOnError: false, strict: 'warn', trust: false, displayMode: display});
            return node;
        }

        static value(node) {
            // Старые записи с классом абзаца также читаются; новые данные хранят
            // режим на самом embed, поэтому форматирование абзаца его не стирает.
            const parent = node.parentElement;
            // В ранее сохранённых AI-лекциях Quill уже мог удалить класс абзаца.
            // Повторяем прежнее поведение reader для одиночной формулы в P;
            // явный data-display="inline" всегда имеет приоритет над эвристикой.
            const legacyStandalone = parent?.tagName === 'P' && parent.children.length === 1 && parent.textContent.trim() === '';
            const display = node.getAttribute('data-display') === 'block'
                || (!node.hasAttribute('data-display') && (parent?.classList.contains('lecture-formula-block')
                    || parent?.classList.contains('lecture-formula') || legacyStandalone));
            return {latex: node.getAttribute('data-value') || '', display: display ? 'block' : 'inline'};
        }

        html() {
            // Стандартный Formula.html ожидает строку. Здесь значение составное,
            // поэтому копирование должно переносить исходник, а не [object Object].
            const value = LectureFormula.value(this.domNode);
            const node = document.createElement('span');
            node.className = 'ql-formula';
            node.setAttribute('data-value', value.latex);
            node.setAttribute('data-display', value.display);
            return node.outerHTML;
        }
    }
    Quill.register('formats/formula', LectureFormula, true);

    class LectureVideo extends Video {
        static create(value) {
            // Штатная кнопка и ранее сохранённые Delta передают строку. Объект
            // нужен только для сохранения локализованного названия при вставке HTML.
            const node = super.create(typeof value === 'string' ? value : value.src);
            node.title = typeof value === 'object' && value.title ? value.title : 'YouTube video player';
            node.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
            node.setAttribute('allow', "encrypted-media; picture-in-picture; fullscreen; camera 'none'; microphone 'none'; display-capture 'none'");
            return node;
        }

        static value(node) {
            return {src: node.getAttribute('src'), title: node.getAttribute('title') || 'YouTube video player'};
        }

        html() {
            const node = document.createElement('iframe');
            const value = LectureVideo.value(this.domNode);
            node.className = 'ql-video';
            node.src = value.src;
            node.title = value.title;
            return node.outerHTML;
        }
    }
    Quill.register('formats/video', LectureVideo, true);

    window.installLectureQuillCompat = function (quill) {
        quill.clipboard.addMatcher('[data-lecture-question], [data-lecture-tone], [data-lecture-section]', (node, delta) => {
            const formats = {};
            const question = node.getAttribute('data-lecture-question') || '';
            const part = node.getAttribute('data-lecture-part');
            if (/^[a-z0-9][a-z0-9-]{0,127}$/.test(question) && ['prompt', 'answer'].includes(part)) {
                formats['lecture-question'] = question;
                formats['lecture-part'] = part;
            }
            const tone = node.getAttribute('data-lecture-tone');
            if (['definition', 'example', 'warning', 'summary'].includes(tone)) formats['lecture-tone'] = tone;
            const section = node.getAttribute('data-lecture-section');
            if (['goals', 'sources'].includes(section)) formats['lecture-section'] = section;
            return delta.compose(new Delta().retain(delta.length(), formats));
        });
        quill.clipboard.addMatcher('TH, TD', (node, delta) => {
            // Без собственного перевода строки два соседних TH сливаются ещё
            // до обработки TR. Формат table назначит штатный matcher строки.
            let result = delta;
            const last = result.ops[result.ops.length - 1];
            if (typeof last?.insert !== 'string' || !last.insert.endsWith('\n')) result = result.concat(new Delta().insert('\n'));
            const formats = {};
            if (node.tagName === 'TH' || node.getAttribute('data-lecture-header') === 'true') formats['lecture-header'] = 'true';
            for (const attribute of ['colspan', 'rowspan']) {
                const value = node.getAttribute(attribute);
                if (/^(?:[1-9]|[12][0-9]|3[0-2])$/.test(value || '')) formats[`lecture-${attribute}`] = value;
            }
            const scope = node.getAttribute('scope') || node.getAttribute('data-lecture-scope');
            if (['col', 'row', 'colgroup', 'rowgroup'].includes(scope)) formats['lecture-scope'] = scope;
            return result.compose(new Delta().retain(result.length(), formats));
        });
    };
}());
