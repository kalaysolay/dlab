/* Вставка YouTube в позицию курсора. В HTML сохраняется обычный video embed Quill,
 * поэтому ролики переживают повторное открытие, редактирование и undo/redo. */
window.installLectureYouTube = function (quill) {
    const dialog = document.createElement('dialog');
    dialog.className = 'lecture-youtube-dialog';
    dialog.setAttribute('aria-label', 'Вставить видео YouTube');
    dialog.innerHTML = '<form><h2>Видео YouTube</h2><label>Ссылка на видео<input name="url" type="url" required placeholder="https://www.youtube.com/watch?v=…" style="width:100%"></label><p data-error role="alert" hidden>Введите ссылку на видео YouTube (обычную, youtu.be или Shorts).</p><div class="lecture-youtube-actions"><button type="button" data-cancel>Отмена</button><button type="submit">Вставить</button></div></form>';
    document.body.append(dialog);
    const input = dialog.querySelector('input');
    const error = dialog.querySelector('[data-error]');
    let range;
    quill.getModule('toolbar').addHandler('video', () => {
        range = quill.getSelection(true) || { index: quill.getLength() - 1, length: 0 };
        input.value = '';
        error.hidden = true;
        dialog.showModal();
        input.focus();
    });
    const button = quill.getModule('toolbar').container.querySelector('.ql-video');
    button.title = 'Вставить видео YouTube';
    button.setAttribute('aria-label', button.title);
    dialog.querySelector('[data-cancel]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => quill.setSelection(range.index, 0, 'silent'));
    dialog.querySelector('form').addEventListener('submit', event => {
        event.preventDefault();
        const url = window.lectureYouTubeUrl(input.value);
        if (!url) { error.hidden = false; input.focus(); return; }
        let index = Math.min(range.index, quill.getLength() - 1);
        quill.history.cutoff();
        // Отделяем блочный плеер от текста слева и оставляем строку для продолжения справа.
        const [, offset] = quill.getLine(index);
        if (offset > 0) { quill.insertText(index, '\n', 'user'); index++; }
        quill.insertEmbed(index, 'video', url, 'user');
        quill.insertText(index + 1, '\n', 'user');
        quill.history.cutoff();
        range = { index: index + 2 };
        dialog.close();
    });
};

/* Проверяем hostname целиком: адреса вроде youtube.com.example.org не являются YouTube.
 * Параметры share/autoplay не переносим; плеер запускается по нажатию ученика. */
window.lectureYouTubeUrl = function (value) {
    try {
        const url = new URL(value.trim());
        if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || url.port) return null;
        const host = url.hostname.toLowerCase();
        let id;
        if (host === 'youtu.be') id = url.pathname.slice(1);
        else if (['youtube.com', 'www.youtube.com', 'm.youtube.com', 'www.youtube-nocookie.com', 'youtube-nocookie.com'].includes(host)) {
            if (url.pathname === '/watch') id = url.searchParams.get('v');
            else id = url.pathname.match(/^\/(?:embed|shorts|live)\/([\w-]{11})\/?$/)?.[1];
        }
        return /^[\w-]{11}$/.test(id || '') ? 'https://www.youtube.com/embed/' + id : null;
    } catch { return null; }
};
