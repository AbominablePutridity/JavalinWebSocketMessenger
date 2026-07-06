// =============================================
// Messenger — ядро приложения
// =============================================
// Этот модуль — диспетчер и точка входа.
// Он определяет:
//   — WS_URL (адрес WebSocket-сервера)
//   — AppState (глобальное состояние SPA)
//   — App (навигация: страницы, логин/логаут,
//      выбор канала, восстановление сессии)
//
// Порядок загрузки при старте:
//   1. DOMContentLoaded → проверка localStorage
//   2. Сохранённый токен → подставляется в Api
//   3. Показывается страница авторизации (Auth)
//   4. После входа — главная страница (App.showMainPage)
// =============================================

// =============================================
// Адрес WebSocket-сервера.
// Сервер (Javalin) слушает на порту 7070,
// путь — /websocket.
// =============================================
var WS_URL = 'ws://localhost:7070/websocket';

// =============================================
// AppState — глобальное состояние SPA
// Здесь хранится всё, что нужно помнить между
// переключениями вкладок и экранов.
// =============================================
var AppState = {
    currentChannel: null,       // объект канала, выбранного в данный момент
    channelsPage: 1,            // текущая страница в списке каналов
    channelsSearch: '',         // строка поиска по каналам
    messagesPage: 1,            // текущая страница сообщений в чате
    messagesSearch: '',         // строка поиска по сообщениям
    membersPage: 1,             // текущая страница списка участников
    currentView: 'chat',        // 'chat' | 'info'
    membersCache: {},           // { channelCode -> [member, ...] } кеш участников
    unreadChannels: {}          // { channelCode -> true } для визуальной метки "непрочитано"
};

// =============================================
// _lastChannelsReq — счётчик для защиты от гонок
// при загрузке списка каналов. Если пришёл ответ
// от старого запроса — он игнорируется.
// =============================================
var _lastChannelsReq = 0;

// =============================================
// При загрузке страницы — инициализация
// =============================================
document.addEventListener('DOMContentLoaded', function() {
    // Пробуем восстановить сессию из localStorage
    var savedToken = localStorage.getItem('messenger_token');
    var savedUserCode = localStorage.getItem('messenger_userCode');
    var savedUserName = localStorage.getItem('messenger_userName');
    var savedUserSurname = localStorage.getItem('messenger_userSurname');

    if (savedToken && savedUserCode) {
        Api.token = savedToken;
        Api.userCode = savedUserCode;
        Api.userName = savedUserName;
        Api.userSurname = savedUserSurname;
    }

    // Восстанавливаем тему
    if (localStorage.getItem('messenger_theme') === 'light') {
        document.body.classList.add('light-theme');
    }

    // Стартуем со страницы входа
    App.showAuthPage();
});

// =============================================
// App — навигация и жизненный цикл
// =============================================
var App = {};

// =============================================
// showAuthPage()
// Рендерит страницу входа/регистрации.
// =============================================
App.showAuthPage = function() {
    var template = document.getElementById('auth-template');
    document.getElementById('app').innerHTML = template.innerHTML;
    Auth.initPage();
};

// =============================================
// showMainPage()
// Строит главный экран приложения.
// Компоновка:
//   ┌─────────────────────────────────┐
//   │  Header: Messenger | Имя | ☀ │
//   ├──────────┬──────────────────────┤
//   │  Каналы  │  Welcome / Chat /    │
//   │  (левый  │  Инфо о канале       │
//   │  панель) │  (правая панель)     │
//   └──────────┴──────────────────────┘
//
// После рендера инициализирует все подмодули
// и подключает push-обработчик.
// =============================================
App.showMainPage = function() {
    var template = document.getElementById('main-template');
    document.getElementById('app').innerHTML = template.innerHTML;
    App._initMainPage();
};

App._initMainPage = function() {
    updateThemeBtn();
    document.getElementById('userDisplayName').textContent =
        (Api.userName || 'Пользователь') + ' ' + (Api.userSurname || '');
    document.getElementById('userCodeDisplay').textContent = 'Код: ' + Api.userCode;
    document.getElementById('themeBtn').onclick = toggleTheme;
    document.getElementById('logoutBtn').onclick = App.logout;

    Channels.initLeftPanel();
    Chat.initChatPanel();
    ChannelInfo.initInfoPanel();

    Api.onPush = PushHandler.handle;
    Api.onReconnect = App.handleReconnect;

    Channels.load();
    App.showWelcome();
};

// =============================================
// showWelcome()
// Показывает приветственную панель (заглушку,
// когда не выбран ни один канал).
// =============================================
App.showWelcome = function() {
    document.getElementById('rightPanel').style.display = 'none';
    document.getElementById('channelInfoPanel').style.display = 'none';
    document.getElementById('welcomePanel').style.display = 'flex';
};

// =============================================
// showChatView()
// Переключает правую панель в режим чата
// для выбранного канала.
// =============================================
App.showChatView = function() {
    if (!AppState.currentChannel) return;

    document.getElementById('rightPanel').style.display = 'flex';
    document.getElementById('channelInfoPanel').style.display = 'none';
    document.getElementById('welcomePanel').style.display = 'none';

    document.getElementById('chatChannelName').textContent = AppState.currentChannel.name;
    document.getElementById('channelInfoBtn').onclick = ChannelInfo.show;

    Chat.load();
};

// =============================================
// selectChannel(channel)
// Вызывается при клике на канал в левой панели.
// Снимает метку "непрочитано", обновляет
// активное состояние кнопки, загружает сообщения
// и участников.
// =============================================
App.selectChannel = function(channel) {
    Channels.clearUnread(channel.code);

    AppState.currentChannel = channel;
    AppState.currentView = 'chat';
    AppState.messagesPage = 1;
    AppState.messagesSearch = '';

    document.getElementById('messagesContainer').innerHTML = '';
    document.getElementById('messagesPageInfo').textContent = '1';
    var searchEl = document.getElementById('messageSearch');
    if (searchEl) searchEl.value = '';

    // Подсвечиваем выбранный канал
    var btns = document.querySelectorAll('.channel-btn');
    for (var i = 0; i < btns.length; i++) {
        btns[i].classList.remove('active');
    }
    for (var i = 0; i < btns.length; i++) {
        if (btns[i].textContent === channel.name) {
            btns[i].classList.add('active');
            break;
        }
    }

    ChannelInfo.loadMembers(channel.code);
    App.showChatView();
};

// =============================================
// logout()
// Очищает localStorage, отключает WebSocket,
// сбрасывает состояние и возвращает на
// страницу входа.
// =============================================
App.logout = function() {
    localStorage.removeItem('messenger_token');
    localStorage.removeItem('messenger_userCode');
    localStorage.removeItem('messenger_userName');
    localStorage.removeItem('messenger_userSurname');

    Api.token = null;
    Api.userCode = null;
    Api.disconnect();

    AppState.currentChannel = null;
    AppState.membersCache = {};
    AppState.channelsPage = 1;
    AppState.messagesPage = 1;
    AppState.membersPage = 1;

    App.showAuthPage();
};

// =============================================
// handleReconnect()
// Вызывается после успешного переподключения
// WebSocket. Пытается повторно авторизоваться
// по сохранённым в памяти логину и паролю.
// Если не получается — разлогинивает.
// =============================================
App.handleReconnect = function() {
    if (!Api.login || !Api.password) {
        Auth.showError('Ошибка восстановления соединения');
        App.logout();
        return;
    }

    Api.send({
        action: 'LOGIN',
        login: Api.login,
        password: Api.password
    }, function(response) {
        if (response.status === 'SUCCESS') {
            Api.token = response.payload.token;
            Api._authenticated = true;
            Channels.load();
            if (AppState.currentChannel) {
                App.selectChannel(AppState.currentChannel);
            }
        } else {
            Auth.showError('Сессия истекла, выполните вход заново');
            App.logout();
        }
    });
};
