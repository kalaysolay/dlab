(function () {
    const mainPreviewText = document.querySelector('[data-push-preview-text]');
    const mainPreviewTarget = document.querySelector('[data-push-preview-target]');

    function bindForm(form) {
        const text = form.querySelector('textarea[maxlength="120"]');
        const counter = form.querySelector('[data-push-counter]');
        const target = form.querySelector('.push-target-select, select[name="targetScreen"]');
        const subjectField = form.querySelector('.push-subject-field');

        function updateText() {
            if (!text || !counter) return;
            const value = text.value || '';
            counter.textContent = value.length;
            if (form.classList.contains('push-form') && mainPreviewText) {
                mainPreviewText.textContent = value || 'Текст push появится здесь';
            }
        }

        function updateTarget() {
            if (!target) return;
            const option = target.options[target.selectedIndex];
            if (form.classList.contains('push-form') && mainPreviewTarget) {
                mainPreviewTarget.textContent = option ? option.textContent : '';
            }
            if (subjectField) {
                subjectField.classList.toggle('hidden-field', target.value !== 'SUBJECT_TEST');
            }
        }

        if (text) {
            text.addEventListener('input', updateText);
            updateText();
        }
        if (target) {
            target.addEventListener('change', updateTarget);
            updateTarget();
        }
    }

    document.querySelectorAll('.push-form, .push-inline-form').forEach(bindForm);
})();
