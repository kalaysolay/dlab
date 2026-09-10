/* Просмотр сохраняет позицию лекции. Pointer Events поддерживают перемещение
 * одним пальцем и масштабирование двумя без внешней библиотеки. */
(() => {
    const images = document.querySelectorAll('.lecture-content img');
    if (!images.length) return;
    const dialog = document.createElement('dialog');
    dialog.className = 'lecture-image-viewer';
    dialog.setAttribute('aria-label', 'Просмотр изображения');
    dialog.innerHTML = '<div class="lecture-image-controls"><button type="button" data-action="out" aria-label="Уменьшить">−</button><button type="button" data-action="in" aria-label="Увеличить">+</button><button type="button" data-action="reset">Сбросить</button><button type="button" data-action="close" autofocus>Закрыть ×</button></div><div class="lecture-image-stage"><img alt="" draggable="false"></div>';
    document.body.append(dialog);
    const stage = dialog.querySelector('.lecture-image-stage');
    const picture = stage.querySelector('img');
    const pointers = new Map();
    let scale = 1, x = 0, y = 0, opener, previousOverflow;
    const paint = () => {
        // Ограничиваем перемещение краями изображения, чтобы оно не исчезало за экраном.
        const maxX = Math.max(0, (picture.clientWidth * scale - stage.clientWidth) / 2);
        const maxY = Math.max(0, (picture.clientHeight * scale - stage.clientHeight) / 2);
        x = Math.max(-maxX, Math.min(maxX, x));
        y = Math.max(-maxY, Math.min(maxY, y));
        picture.style.transform = `translate(${x}px, ${y}px) scale(${scale})`;
    };
    const zoom = value => { scale = Math.max(1, Math.min(5, value)); paint(); };
    images.forEach(image => {
        image.tabIndex = 0;
        image.setAttribute('role', 'button');
        image.setAttribute('aria-haspopup', 'dialog');
        const open = event => {
            event.preventDefault();
            opener = image;
            picture.src = image.currentSrc || image.src;
            picture.alt = image.alt;
            scale = 1; x = 0; y = 0;
            previousOverflow = document.body.style.overflow;
            document.body.style.overflow = 'hidden';
            dialog.showModal();
            paint();
        };
        image.addEventListener('click', open);
        image.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') open(event);
        });
    });
    picture.addEventListener('load', paint);
    window.addEventListener('resize', paint);
    dialog.addEventListener('close', () => {
        pointers.clear();
        document.body.style.overflow = previousOverflow;
        opener?.focus({ preventScroll: true });
    });
    dialog.querySelector('.lecture-image-controls').addEventListener('click', event => {
        const action = event.target.closest('button')?.dataset.action;
        if (action === 'close') dialog.close();
        if (action === 'in') zoom(scale * 1.5);
        if (action === 'out') zoom(scale / 1.5);
        if (action === 'reset') { x = 0; y = 0; zoom(1); }
    });
    const distance = () => {
        const [a, b] = Array.from(pointers.values());
        return Math.hypot(a.x - b.x, a.y - b.y);
    };
    stage.addEventListener('pointerdown', event => {
        pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
        stage.setPointerCapture(event.pointerId);
    });
    stage.addEventListener('pointermove', event => {
        const previous = pointers.get(event.pointerId);
        if (!previous) return;
        const oldDistance = pointers.size === 2 ? distance() : 0;
        pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
        if (pointers.size === 2 && oldDistance > 0) zoom(scale * distance() / oldDistance);
        else if (pointers.size === 1) {
            x += event.clientX - previous.x;
            y += event.clientY - previous.y;
            paint();
        }
    });
    ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(type =>
        stage.addEventListener(type, event => pointers.delete(event.pointerId)));
    stage.addEventListener('wheel', event => {
        event.preventDefault();
        zoom(scale * (event.deltaY < 0 ? 1.15 : 1 / 1.15));
    }, { passive: false });
    stage.addEventListener('dblclick', () => zoom(scale > 1 ? 1 : 2));
})();
