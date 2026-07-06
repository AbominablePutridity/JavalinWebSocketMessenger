// =============================================
// АВТОРИЗАЦИЯ — шаблон и логика
// =============================================
// Этот модуль отвечает за страницу входа
// и регистрации. Реализует простую SPA-на-
// вигацию двумя вкладками: "Вход" и "Реги-
// страция". При успешном входе/регистрации
// получает JWT-токен и переходит на главную.
// =============================================

var Auth = {};

// =============================================
// renderPage()
// Возвращает HTML-строку с формой авторизации:
//   ┌─────────────────────┐
//   │      Messenger       │
//   ├───────┬─────────────┤
//   │ Вход  │ Регистрация │  ← табы
//   ├───────┴─────────────┤
//   │ [Логин]             │
//   │ [Пароль]            │
//   │ [Войти]             │
//   └─────────────────────┘
// =============================================
Auth.renderPage = function() {
    return '' +
        '<div class="auth-page">' +
            '<div class="auth-container">' +
                '<h1>Messenger</h1>' +
                '<div class="tabs">' +
                    '<div id="tabLogin" class="tab active">Вход</div>' +
                    '<div id="tabRegister" class="tab">Регистрация</div>' +
                '</div>' +
                '<div id="authError" class="auth-error" style="display:none;"></div>' +
                '<div id="loginForm" class="auth-form">' +
                    '<input id="loginInput" type="text" placeholder="Логин" autocomplete="username">' +
                    '<input id="passwordInput" type="password" placeholder="Пароль" autocomplete="current-password">' +
                    '<button id="loginBtn">Войти</button>' +
                '</div>' +
                '<div id="registerForm" class="auth-form" style="display:none;">' +
                    '<input id="regLoginInput" type="text" placeholder="Логин" autocomplete="off">' +
                    '<input id="regPasswordInput" type="password" placeholder="Пароль (мин. 4 символа)" autocomplete="new-password">' +
                    '<input id="regNameInput" type="text" placeholder="Имя" autocomplete="off">' +
                    '<input id="regSurnameInput" type="text" placeholder="Фамилия (опционально)" autocomplete="off">' +
                    '<button id="registerBtn">Зарегистрироваться</button>' +
                '</div>' +
            '</div>' +
        '</div>';
};

// =============================================
// initPage()
// Навешивает обработчики кликов на табы и кнопки.
// Вызывается после рендера страницы авторизации.
// =============================================
Auth.initPage = function() {
    document.getElementById('tabLogin').onclick = function() { Auth.showLoginForm(); Auth.clearError(); };
    document.getElementById('tabRegister').onclick = function() { Auth.showRegisterForm(); Auth.clearError(); };
    document.getElementById('loginBtn').onclick = Auth.handleLogin;
    document.getElementById('registerBtn').onclick = Auth.handleRegister;
};

// =============================================
// showLoginForm / showRegisterForm
// Переключают видимость формы и активный таб.
// =============================================
Auth.showLoginForm = function() {
    document.getElementById('loginForm').style.display = 'block';
    document.getElementById('registerForm').style.display = 'none';
    document.getElementById('tabLogin').className = 'tab active';
    document.getElementById('tabRegister').className = 'tab';
};

Auth.showRegisterForm = function() {
    document.getElementById('loginForm').style.display = 'none';
    document.getElementById('registerForm').style.display = 'block';
    document.getElementById('tabLogin').className = 'tab';
    document.getElementById('tabRegister').className = 'tab active';
};

// =============================================
// showError / clearError
// Показывают/скрывают блок с сообщением об ошибке.
// =============================================
Auth.showError = function(msg) {
    var el = document.getElementById('authError');
    if (el) {
        el.textContent = msg;
        el.style.display = 'block';
    }
};

Auth.clearError = function() {
    var el = document.getElementById('authError');
    if (el) el.style.display = 'none';
};

// =============================================
// handleLogin()
// 1. Открывает WebSocket-соединение
// 2. Отправляет LOGIN с логином и паролем
// 3. При успехе: сохраняет токен, userCode,
//    логин и пароль в Api (для восстановления),
//    записывает в localStorage, переходит на главную
// Соединение после входа остаётся открытым.
// =============================================
Auth.handleLogin = function() {
    var login = document.getElementById('loginInput').value.trim();
    var password = document.getElementById('passwordInput').value;

    if (!login || !password) {
        Auth.showError('Заполните логин и пароль');
        return;
    }

    Api.connect(WS_URL, function() {
        Api.send({
            action: 'LOGIN',
            login: login,
            password: password
        }, function(response) {
            if (response.status === 'SUCCESS') {
                Api.token = response.payload.token;
                Api.userCode = response.payload.userCode;
                Api.login = login;
                Api.password = password;
                Api._authenticated = true;

                localStorage.setItem('messenger_token', Api.token);
                localStorage.setItem('messenger_userCode', Api.userCode);

                App.showMainPage();
            } else {
                Auth.showError(response.error || 'Ошибка входа');
            }
        });
    }, function() {
        Auth.showError('Потеряно соединение с сервером');
    });
};

// =============================================
// handleRegister()
// Аналогично handleLogin, но:
//   1. Валидирует поля (логин, пароль >= 4, имя)
//   2. Отправляет REGISTER с доп. полями name/surname
//   3. При успехе сохраняет также name и surname
// =============================================
Auth.handleRegister = function() {
    var login = document.getElementById('regLoginInput').value.trim();
    var password = document.getElementById('regPasswordInput').value;
    var name = document.getElementById('regNameInput').value.trim();
    var surname = document.getElementById('regSurnameInput').value.trim();

    if (!login || !password || !name) {
        Auth.showError('Заполните логин, пароль и имя');
        return;
    }

    Api.connect(WS_URL, function() {
        Api.send({
            action: 'REGISTER',
            login: login,
            password: password,
            name: name,
            surname: surname
        }, function(response) {
            if (response.status === 'SUCCESS') {
                Api.token = response.payload.token;
                Api.userCode = response.payload.userCode;
                Api.userName = response.payload.name;
                Api.userSurname = response.payload.surname || '';
                Api.login = login;
                Api.password = password;
                Api._authenticated = true;

                localStorage.setItem('messenger_token', Api.token);
                localStorage.setItem('messenger_userCode', Api.userCode);
                localStorage.setItem('messenger_userName', Api.userName);
                localStorage.setItem('messenger_userSurname', Api.userSurname);

                App.showMainPage();
            } else {
                Auth.showError(response.error || 'Ошибка регистрации');
            }
        });
    }, function() {
        Auth.showError('Потеряно соединение с сервером');
    });
};
