// =============================================
// УТИЛИТЫ
// =============================================
// Вспомогательные функции, не привязанные
// к конкретному модулю: форматирование даты,
// переключение темы оформления.
// =============================================

// =============================================
// formatDate(dateStr)
// Преобразует ISO-дату (строку) в человеко-
// читаемый формат, локализованный под ru-RU.
// Пример: "2026-07-06T12:00:00" → "06.07.2026, 12:00:00"
// =============================================
function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
        var d = new Date(dateStr);
        return d.toLocaleString('ru-RU');
    } catch (e) {
        return dateStr;
    }
}

// =============================================
// toggleTheme()
// Переключает между тёмной и светлой темой.
// Добавляет/убирает класс light-theme на body.
// Сохраняет выбор в localStorage, чтобы тема
// сохранялась между перезагрузками.
// =============================================
function toggleTheme() {
    var body = document.body;
    body.classList.toggle('light-theme');
    var btn = document.querySelector('.theme-btn');
    btn.textContent = body.classList.contains('light-theme') ? '\u263E' : '\u2600';
    localStorage.setItem('messenger_theme', body.classList.contains('light-theme') ? 'light' : 'dark');
}

// =============================================
// updateThemeBtn()
// Обновляет иконку кнопки темы при загрузке
// главной страницы, чтобы она соответствовала
// сохранённой теме.
// =============================================
function updateThemeBtn() {
    var btn = document.querySelector('.theme-btn');
    if (btn) {
        btn.textContent = document.body.classList.contains('light-theme') ? '\u263E' : '\u2600';
    }
}
