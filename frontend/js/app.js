// ========================================
// Логика приложения Messenger
// ========================================

// Константы
var WS_URL = 'ws://localhost:7070/websocket';

// Состояние приложения
var AppState = {
    currentChannel: null,          // текущий выбранный канал
    channelsPage: 1,               // текущая страница списка каналов
    channelsSearch: '',            // поисковый запрос по каналам
    messagesPage: 1,               // текущая страница сообщений
    membersPage: 1,                // текущая страница списка участников
    currentView: 'chat',           // 'chat' или 'info' (о канале)
    membersCache: {},              // кеш участников: channelCode -> [{userCode, name, surname}]
    unreadChannels: {}             // помеченные каналы с непрочитанными: channelCode -> true
};

// ========================================
// ИНИЦИАЛИЗАЦИЯ
// ========================================

// Запуск при после загрузки страницы
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

    showAuthPage();
});

// ========================================
// СТРАНИЦА АВТОРИЗАЦИИ
// ========================================

// Показывает страницу входа/регистрации
function showAuthPage() {
    document.getElementById('authPage').style.display = 'flex';
    document.getElementById('mainPage').style.display = 'none';
    showLoginForm();
}

// Показывает форму входа
function showLoginForm() {
    document.getElementById('loginForm').style.display = 'block';
    document.getElementById('registerForm').style.display = 'none';
    document.getElementById('tabLogin').className = 'tab active';
    document.getElementById('tabRegister').className = 'tab';
}

// Показывает форму регистрации
function showRegisterForm() {
    document.getElementById('loginForm').style.display = 'none';
    document.getElementById('registerForm').style.display = 'block';
    document.getElementById('tabLogin').className = 'tab';
    document.getElementById('tabRegister').className = 'tab active';
}

// Обработчик входа
function handleLogin() {
    var login = document.getElementById('loginInput').value.trim();
    var password = document.getElementById('passwordInput').value;

    if (!login || !password) {
        showAuthError('Заполните логин и пароль');
        return;
    }

    Api.connect(WS_URL, function() {
        // При подключении отправляем запрос LOGIN
        Api.send({
            action: 'LOGIN',
            login: login,
            password: password
        }, function(response) {
            if (response.status === 'SUCCESS') {
                // Сохраняем токен и данные пользователя
                Api.token = response.payload.token;
                Api.userCode = response.payload.userCode;

                localStorage.setItem('messenger_token', Api.token);
                localStorage.setItem('messenger_userCode', Api.userCode);

                showMainPage();
            } else {
                showAuthError(response.error || 'Ошибка входа');
            }
        });
    }, function() {
        showAuthError('Потеряно соединение с сервером');
    });
}

// Обработчик регистрации
function handleRegister() {
    var login = document.getElementById('regLoginInput').value.trim();
    var password = document.getElementById('regPasswordInput').value;
    var name = document.getElementById('regNameInput').value.trim();
    var surname = document.getElementById('regSurnameInput').value.trim();

    if (!login || !password || !name) {
        showAuthError('Заполните логин, пароль и имя');
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

                localStorage.setItem('messenger_token', Api.token);
                localStorage.setItem('messenger_userCode', Api.userCode);
                localStorage.setItem('messenger_userName', Api.userName);
                localStorage.setItem('messenger_userSurname', Api.userSurname);

                showMainPage();
            } else {
                showAuthError(response.error || 'Ошибка регистрации');
            }
        });
    }, function() {
        showAuthError('Потеряно соединение с сервером');
    });
}

// Показывает ошибку на странице авторизации
function showAuthError(msg) {
    document.getElementById('authError').textContent = msg;
    document.getElementById('authError').style.display = 'block';
}

// Очищает ошибку
function clearAuthError() {
    document.getElementById('authError').style.display = 'none';
}

// ========================================
// ГЛАВНАЯ СТРАНИЦА
// ========================================

// Переход к главной странице
function showMainPage() {
    document.getElementById('authPage').style.display = 'none';
    document.getElementById('mainPage').style.display = 'flex';

    // Обновляем иконку темы
    updateThemeBtn();

    // Обновляем данные пользователя в шапке
    document.getElementById('userDisplayName').textContent =
        (Api.userName || 'Пользователь') + ' ' + (Api.userSurname || '');
    document.getElementById('userCodeDisplay').textContent = 'Код: ' + Api.userCode;

    // Подписываемся на push-события
    Api.onPush = handlePushEvent;

    // Загружаем список каналов
    loadChannels();

    // Показываем приветствие в правой панели
    showWelcome();
}

// ========================================
// СВОРАЧИВАНИЕ ПАНЕЛИ СОЗДАНИЯ КАНАЛА
// ========================================

function toggleCreateChannel() {
    var panel = document.getElementById('createChannelPanel');
    var isHidden = panel.style.display === 'none';
    panel.style.display = isHidden ? 'flex' : 'none';
}

// ========================================
// РАБОТА С КАНАЛАМИ
// ========================================

// Загрузка списка каналов с сервера
var _lastChannelsReq = 0;

function loadChannels() {
    var data = {
        action: 'SEARCH_CHANNELS',
        page: AppState.channelsPage,
        size: 50
    };

    // Если есть поисковый запрос — добавляем
    if (AppState.channelsSearch) {
        data.name = AppState.channelsSearch;
    }

    // Обновляем номер страницы
    document.getElementById('channelPageInfo').textContent = AppState.channelsPage;

    var reqId = ++_lastChannelsReq;

    Api.send(data, function(response) {
        if (response.status === 'SUCCESS') {
            // Игнорируем устаревшие ответы (запрос был перебит новым поиском)
            if (reqId === _lastChannelsReq) {
                renderChannelList(response.payload.channels);
            }
        }
    });
}

// Поиск каналов (срабатывает при вводе в поле поиска)
function searchChannels() {
    AppState.channelsPage = 1;
    AppState.channelsSearch = document.getElementById('channelSearch').value.trim();
    loadChannels();
}

// Переключение страницы каналов
function prevChannelsPage() {
    if (AppState.channelsPage > 1) {
        AppState.channelsPage--;
        loadChannels();
    }
}

function nextChannelsPage() {
    AppState.channelsPage++;
    loadChannels();
}

// Отрисовка списка каналов в левой панели
function renderChannelList(channels) {
    var container = document.getElementById('channelList');
    container.innerHTML = '';

    if (!channels || channels.length === 0) {
        container.innerHTML = '<div class="channel-empty">Нет каналов</div>';
        return;
    }

    for (var i = 0; i < channels.length; i++) {
        var ch = channels[i];
        var btn = document.createElement('button');
        btn.className = 'channel-btn';
        btn.setAttribute('data-code', ch.code);
        btn.textContent = ch.name;

        // Подсвечиваем выбранный канал
        if (AppState.currentChannel && AppState.currentChannel.code === ch.code) {
            btn.classList.add('active');
        }

        btn.addEventListener('click', (function(channel) {
            return function() {
                selectChannel(channel);
            };
        })(ch));

        container.appendChild(btn);
    }
}

// ========================================
// НЕПРОЧИТАННЫЕ СООБЩЕНИЯ
// ========================================

function markChannelUnread(channelCode) {
    if (AppState.unreadChannels[channelCode]) return;
    AppState.unreadChannels[channelCode] = true;
    var btn = document.querySelector('.channel-btn[data-code="' + channelCode + '"]');
    if (btn) btn.classList.add('has-unread');
}

function clearChannelUnread(channelCode) {
    delete AppState.unreadChannels[channelCode];
    var btn = document.querySelector('.channel-btn[data-code="' + channelCode + '"]');
    if (btn) btn.classList.remove('has-unread');
}

// Выбор канала — открываем чат
function selectChannel(channel) {
    // Снимаем пометку о непрочитанном
    clearChannelUnread(channel.code);

    AppState.currentChannel = channel;
    AppState.currentView = 'chat';
    AppState.messagesPage = 1;

    // Очищаем сообщения сразу, чтобы не было видно старого чата
    document.getElementById('messagesContainer').innerHTML = '';
    document.getElementById('messagesPageInfo').textContent = '1';

    // Подсвечиваем в списке
    var btns = document.querySelectorAll('.channel-btn');
    for (var i = 0; i < btns.length; i++) {
        btns[i].classList.remove('active');
    }
    // Находим кнопку с таким названием и подсвечиваем
    var allBtns = document.querySelectorAll('.channel-btn');
    for (var i = 0; i < allBtns.length; i++) {
        if (allBtns[i].textContent === channel.name) {
            allBtns[i].classList.add('active');
            break;
        }
    }

    // Загружаем участников канала для отображения имён
    loadChannelMembers(channel.code);

    // Показываем чат
    showChatView();
}

// ========================================
// РАБОТА С СООБЩЕНИЯМИ
// ========================================

// Загрузка сообщений для текущего канала
function loadMessages() {
    if (!AppState.currentChannel) return;

    var channelCode = AppState.currentChannel.code;

    var data = {
        action: 'SEARCH_MESSAGES',
        channelCode: channelCode,
        page: AppState.messagesPage,
        size: 50
    };

    // Обновляем номер страницы сообщений
    document.getElementById('messagesPageInfo').textContent = AppState.messagesPage;

    Api.send(data, function(response) {
        if (response.status === 'SUCCESS') {
            // Проверяем, что канал не переключили за время запроса
            if (AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                renderMessages(response.payload);
            }
        }
    });
}

// Пагинация сообщений
function prevMessagesPage() {
    if (AppState.messagesPage > 1) {
        AppState.messagesPage--;
        loadMessages();
    }
}

function nextMessagesPage() {
    AppState.messagesPage++;
    loadMessages();
}

// Отрисовка сообщений в правой панели
function renderMessages(data) {
    var container = document.getElementById('messagesContainer');
    container.innerHTML = '';

    var messages = data.messages || [];

    if (messages.length === 0) {
        container.innerHTML = '<div class="msg-empty">Нет сообщений</div>';
        return;
    }

    // Переворачиваем: сервер отдаёт DESC (новые первые), а мы показываем хронологически
    messages = messages.reverse();

    for (var i = 0; i < messages.length; i++) {
        var msg = messages[i];
        var isOwner = (msg.userCode === Api.userCode);

        var msgDiv = document.createElement('div');
        msgDiv.className = 'message' + (isOwner ? ' my-message' : '');
        msgDiv.setAttribute('data-msg-id', msg.id);

        // Имя отправителя (берём из кеша участников)
        var senderName = getUserName(msg.userCode);
        var senderSpan = document.createElement('div');
        senderSpan.className = 'msg-sender';
        senderSpan.textContent = senderName;

        // Текст сообщения
        var textSpan = document.createElement('div');
        textSpan.className = 'msg-text';
        textSpan.textContent = msg.text;

        // Дата
        var dateSpan = document.createElement('div');
        dateSpan.className = 'msg-date';
        dateSpan.textContent = formatDate(msg.dateSend);

        msgDiv.appendChild(senderSpan);
        msgDiv.appendChild(textSpan);
        msgDiv.appendChild(dateSpan);

        // Кнопки редактирования/удаления (только для своих сообщений)
        if (isOwner) {
            var actions = document.createElement('div');
            actions.className = 'msg-actions';

            var editBtn = document.createElement('button');
            editBtn.textContent = '✎';
            editBtn.className = 'msg-btn';
            editBtn.title = 'Редактировать';
            editBtn.addEventListener('click', (function(msgId, currentText) {
                return function() {
                    editMessage(msgId, currentText);
                };
            })(msg.id, msg.text));

            var delBtn = document.createElement('button');
            delBtn.textContent = '✕';
            delBtn.className = 'msg-btn delete';
            delBtn.title = 'Удалить';
            delBtn.addEventListener('click', (function(msgId) {
                return function() {
                    if (confirm('Удалить сообщение?')) {
                        deleteMessage(msgId);
                    }
                };
            })(msg.id));

            actions.appendChild(editBtn);
            actions.appendChild(delBtn);
            msgDiv.appendChild(actions);
        }

        container.appendChild(msgDiv);
    }

    // Скролл вниз к последним сообщениям
    container.scrollTop = container.scrollHeight;
}

// Отправка нового сообщения
function sendMessage() {
    if (!AppState.currentChannel) return;

    var textInput = document.getElementById('messageInput');
    var text = textInput.value.trim();

    if (!text) return;

    var channelCode = AppState.currentChannel.code;

    Api.send({
        action: 'CREATE_MESSAGE',
        channelCode: channelCode,
        text: text
    }, function(response) {
        if (response.status === 'SUCCESS') {
            textInput.value = '';
            // Загружаем сообщения только если канал не переключили
            if (AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                loadMessages();
            }
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
}

// Отправка по нажатию Enter
function handleMessageKey(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

// Редактирование сообщения
function editMessage(msgId, currentText) {
    var newText = prompt('Редактировать сообщение:', currentText);
    if (newText && newText.trim() && newText.trim() !== currentText) {
        var channelCode = AppState.currentChannel ? AppState.currentChannel.code : null;
        Api.send({
            action: 'UPDATE_MESSAGE',
            messageId: msgId,
            text: newText.trim()
        }, function(response) {
            if (response.status === 'SUCCESS') {
                if (channelCode && AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                    loadMessages();
                }
            } else {
                alert('Ошибка: ' + response.error);
            }
        });
    }
}

// Удаление сообщения
function deleteMessage(msgId) {
    var channelCode = AppState.currentChannel ? AppState.currentChannel.code : null;
    Api.send({
        action: 'DELETE_MESSAGE',
        messageId: msgId
    }, function(response) {
        if (response.status === 'SUCCESS') {
            if (channelCode && AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                loadMessages();
            }
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
}

// ========================================
// ПРОСМОТР ЧАТА
// ========================================

// Показывает окно чата с сообщениями
function showChatView() {
    if (!AppState.currentChannel) return;

    document.getElementById('rightPanel').style.display = 'flex';
    document.getElementById('channelInfoPanel').style.display = 'none';
    document.getElementById('welcomePanel').style.display = 'none';

    // Название канала
    document.getElementById('chatChannelName').textContent = AppState.currentChannel.name;

    // Кнопка "О канале"
    var infoBtn = document.getElementById('channelInfoBtn');
    infoBtn.onclick = function() {
        showChannelInfo();
    };

    loadMessages();
}

// Показывает приветственный экран (когда канал не выбран)
function showWelcome() {
    document.getElementById('rightPanel').style.display = 'none';
    document.getElementById('channelInfoPanel').style.display = 'none';
    document.getElementById('welcomePanel').style.display = 'flex';
}

// ========================================
// СТРАНИЦА "О КАНАЛЕ"
// ========================================

// Загрузка списка участников канала
function loadChannelMembers(channelCode) {
    Api.send({
        action: 'GET_CHANNEL_MEMBERS',
        channelCode: channelCode
    }, function(response) {
        if (response.status === 'SUCCESS') {
            AppState.membersCache[channelCode] = response.payload.members || [];
        }
    });
}

// Получение имени пользователя по его коду
function getUserName(userCode) {
    if (!AppState.currentChannel) return userCode;

    var members = AppState.membersCache[AppState.currentChannel.code] || [];
    for (var i = 0; i < members.length; i++) {
        if (members[i].userCode === userCode) {
            return members[i].name + ' ' + members[i].surname;
        }
    }
    // Если это текущий пользователь
    if (userCode === Api.userCode) {
        return (Api.userName || '') + ' ' + (Api.userSurname || '');
    }
    return userCode.substring(0, 8) + '...';
}

// Показывает страницу "О канале"
function showChannelInfo() {
    if (!AppState.currentChannel) return;

    AppState.currentView = 'info';

    document.getElementById('rightPanel').style.display = 'none';
    document.getElementById('welcomePanel').style.display = 'none';
    document.getElementById('channelInfoPanel').style.display = 'block';

    var ch = AppState.currentChannel;
    var isOwner = (ch.ownerCode === Api.userCode);

    // Основная информация
    document.getElementById('infoName').textContent = ch.name;
    document.getElementById('infoDescription').textContent = ch.description || '(нет описания)';
    document.getElementById('infoDate').textContent = formatDate(ch.creationDate);
    document.getElementById('infoOwner').textContent = getUserName(ch.ownerCode);

    // Инструменты владельца
    var ownerTools = document.getElementById('ownerTools');
    if (isOwner) {
        ownerTools.style.display = 'block';

        // Заполняем поля редактирования
        document.getElementById('editName').value = ch.name;
        document.getElementById('editDescription').value = ch.description || '';
    } else {
        ownerTools.style.display = 'none';
    }

    // Сбрасываем страницу участников
    AppState.membersPage = 1;

    // Загружаем список участников
    loadChannelMembers(ch.code);
    // Показываем участников (с небольшой задержкой, чтобы кеш обновился)
    setTimeout(function() {
        renderChannelMembers(ch.code);
    }, 200);

    // Кнопка "Назад к чату"
    document.getElementById('backToChatBtn').onclick = function() {
        showChatView();
    };
}

// Пагинация участников
function prevMembersPage() {
    if (AppState.membersPage > 1) {
        AppState.membersPage--;
        if (AppState.currentChannel) {
            renderChannelMembers(AppState.currentChannel.code);
        }
    }
}

function nextMembersPage() {
    AppState.membersPage++;
    if (AppState.currentChannel) {
        renderChannelMembers(AppState.currentChannel.code);
    }
}

// Отрисовка списка участников канала (с пагинацией по 20)
var MEMBERS_PAGE_SIZE = 20;

function renderChannelMembers(channelCode) {
    var container = document.getElementById('membersList');
    container.innerHTML = '';

    var allMembers = AppState.membersCache[channelCode] || [];

    if (allMembers.length === 0) {
        container.innerHTML = '<div class="member-item">Нет участников</div>';
        return;
    }

    // Пагинация
    var totalPages = Math.ceil(allMembers.length / MEMBERS_PAGE_SIZE);
    if (AppState.membersPage > totalPages) AppState.membersPage = totalPages;
    if (AppState.membersPage < 1) AppState.membersPage = 1;

    document.getElementById('membersPageInfo').textContent = AppState.membersPage;

    var start = (AppState.membersPage - 1) * MEMBERS_PAGE_SIZE;
    var end = Math.min(start + MEMBERS_PAGE_SIZE, allMembers.length);
    var pageMembers = allMembers.slice(start, end);

    for (var i = 0; i < pageMembers.length; i++) {
        var m = pageMembers[i];
        var div = document.createElement('div');
        div.className = 'member-item';
        div.textContent = m.name + ' ' + m.surname + ' (' + m.userCode.substring(0, 8) + '...)';

        // Если владелец и участник не сам владелец — кнопка удаления
        if (AppState.currentChannel && AppState.currentChannel.ownerCode === Api.userCode) {
            if (m.userCode !== Api.userCode) {
                var removeBtn = document.createElement('button');
                removeBtn.textContent = '✕';
                removeBtn.className = 'msg-btn delete';
                removeBtn.title = 'Удалить из канала';
                removeBtn.style.marginLeft = '10px';
                removeBtn.addEventListener('click', (function(memberCode) {
                    return function() {
                        if (confirm('Удалить участника из канала?')) {
                            removeMember(memberCode);
                        }
                    };
                })(m.userCode));
                div.appendChild(removeBtn);
            }
        }

        container.appendChild(div);
    }
}

// ========================================
// ИНСТРУМЕНТЫ ВЛАДЕЛЬЦА КАНАЛА
// ========================================

// Обновление информации о канале
function updateChannel() {
    if (!AppState.currentChannel) return;

    var name = document.getElementById('editName').value.trim();
    var description = document.getElementById('editDescription').value.trim();

    if (!name) {
        alert('Название канала не может быть пустым');
        return;
    }

    Api.send({
        action: 'UPDATE_CHANNEL',
        code: AppState.currentChannel.code,
        name: name,
        description: description
    }, function(response) {
        if (response.status === 'SUCCESS') {
            AppState.currentChannel.name = response.payload.name;
            AppState.currentChannel.description = response.payload.description;

            // Обновляем данные на странице
            document.getElementById('infoName').textContent = response.payload.name;
            document.getElementById('infoDescription').textContent = response.payload.description || '(нет описания)';

            // Обновляем название в списке каналов
            loadChannels();

            alert('Канал обновлён');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
}

// Добавление участника
function addMember() {
    if (!AppState.currentChannel) return;

    var memberCode = document.getElementById('addMemberInput').value.trim();
    if (!memberCode) {
        alert('Введите код пользователя');
        return;
    }

    Api.send({
        action: 'ADD_MEMBER',
        channelCode: AppState.currentChannel.code,
        memberCode: memberCode
    }, function(response) {
        if (response.status === 'SUCCESS') {
            document.getElementById('addMemberInput').value = '';
            // Перезагружаем участников
            loadChannelMembers(AppState.currentChannel.code);
            setTimeout(function() {
                renderChannelMembers(AppState.currentChannel.code);
            }, 200);
            alert('Участник добавлен');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
}

// Удаление участника
function removeMember(memberCode) {
    if (!AppState.currentChannel) return;

    Api.send({
        action: 'REMOVE_MEMBER',
        channelCode: AppState.currentChannel.code,
        memberCode: memberCode
    }, function(response) {
        if (response.status === 'SUCCESS') {
            loadChannelMembers(AppState.currentChannel.code);
            setTimeout(function() {
                renderChannelMembers(AppState.currentChannel.code);
            }, 200);
            alert('Участник удалён');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
}

// Удаление канала
function deleteCurrentChannel() {
    if (!AppState.currentChannel) return;
    if (!confirm('Вы уверены, что хотите удалить канал "' + AppState.currentChannel.name + '"? Это действие необратимо.')) return;

    Api.send({
        action: 'DELETE_CHANNEL',
        code: AppState.currentChannel.code
    }, function(response) {
        if (response.status === 'SUCCESS') {
            AppState.currentChannel = null;
            loadChannels();
            showWelcome();
            alert('Канал удалён');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
}

// Создание нового канала
function createChannel() {
    var name = document.getElementById('newChannelName').value.trim();
    if (!name) {
        alert('Введите название канала');
        return;
    }

    var description = document.getElementById('newChannelDesc').value.trim();

    Api.send({
        action: 'CREATE_CHANNEL',
        name: name,
        description: description
    }, function(response) {
        if (response.status === 'SUCCESS') {
            document.getElementById('newChannelName').value = '';
            document.getElementById('newChannelDesc').value = '';
            // Переключаемся на первую страницу и обновляем список
            AppState.channelsPage = 1;
            loadChannels();
            alert('Канал создан');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
}

// ========================================
// ВЫХОД
// ========================================

function logout() {
    localStorage.removeItem('messenger_token');
    localStorage.removeItem('messenger_userCode');
    localStorage.removeItem('messenger_userName');
    localStorage.removeItem('messenger_userSurname');

    Api.token = null;
    Api.userCode = null;
    Api.disconnect();

    // Сбрасываем состояние
    AppState.currentChannel = null;
    AppState.membersCache = {};
    AppState.channelsPage = 1;
    AppState.messagesPage = 1;
    AppState.membersPage = 1;

    showAuthPage();
}

// ========================================
// ФОРМАТИРОВАНИЕ ДАТЫ
// ========================================

// ========================================
// PUSH-СОБЫТИЯ (полученные от сервера без запроса)
// ========================================

function handlePushEvent(response) {
    var action = response.action;
    var payload = response.payload;

    switch (action) {
        case 'NEW_MESSAGE':
            handlePushNewMessage(payload);
            break;
        case 'MESSAGE_UPDATED':
            handlePushMessageUpdated(payload);
            break;
        case 'MESSAGE_DELETED':
            handlePushMessageDeleted(payload);
            break;
        case 'CHANNEL_ADDED':
            handlePushChannelAdded(payload);
            break;
        case 'CHANNEL_UPDATED':
            handlePushChannelUpdated(payload);
            break;
        case 'CHANNEL_DELETED':
            handlePushChannelDeleted(payload);
            break;
        case 'REMOVED_FROM_CHANNEL':
            handlePushRemovedFromChannel(payload);
            break;
    }
}

// Новое сообщение в канале
function handlePushNewMessage(payload) {
    var isCurrent = AppState.currentChannel && AppState.currentChannel.code === payload.channelCode;
    var isShowingLatest = AppState.currentView === 'chat' && AppState.messagesPage === 1;

    if (isCurrent && isShowingLatest) {
        var container = document.getElementById('messagesContainer');
        var emptyMsg = container.querySelector('.msg-empty');
        if (emptyMsg) container.innerHTML = '';

        var msgDiv = createMessageElement(payload);
        container.appendChild(msgDiv);
        container.scrollTop = container.scrollHeight;
    } else {
        // Канал не просматривается — помечаем непрочитанным
        markChannelUnread(payload.channelCode);
    }
}

// Сообщение отредактировано
function handlePushMessageUpdated(payload) {
    if (AppState.currentChannel && AppState.currentView === 'chat') {
        var container = document.getElementById('messagesContainer');
        var msgDivs = container.querySelectorAll('.message');
        for (var i = 0; i < msgDivs.length; i++) {
            var textDiv = msgDivs[i].querySelector('.msg-text');
            if (textDiv) {
                var dataId = msgDivs[i].getAttribute('data-msg-id');
                if (dataId == payload.id) {
                    textDiv.textContent = payload.text;
                    break;
                }
            }
        }
    }
}

// Сообщение удалено
function handlePushMessageDeleted(payload) {
    if (AppState.currentChannel && AppState.currentView === 'chat') {
        var container = document.getElementById('messagesContainer');
        var msgDivs = container.querySelectorAll('.message');
        for (var i = 0; i < msgDivs.length; i++) {
            var dataId = msgDivs[i].getAttribute('data-msg-id');
            if (dataId == payload.id) {
                msgDivs[i].remove();
                break;
            }
        }
        if (container.children.length === 0) {
            container.innerHTML = '<div class="msg-empty">Нет сообщений</div>';
        }
    }
}

// Добавлен новый канал (пользователя добавили)
function handlePushChannelAdded(payload) {
    loadChannels();
}

// Канал обновлён
function handlePushChannelUpdated(payload) {
    // Если это текущий канал — обновляем данные
    if (AppState.currentChannel && AppState.currentChannel.code === payload.channelCode) {
        AppState.currentChannel.name = payload.name;
        AppState.currentChannel.description = payload.description;

        // Обновляем заголовок чата
        var chatTitle = document.getElementById('chatChannelName');
        if (chatTitle) chatTitle.textContent = payload.name;

        // Если на странице информации — обновляем
        document.getElementById('infoName').textContent = payload.name;
        document.getElementById('infoDescription').textContent = payload.description || '(нет описания)';
    }
    loadChannels();
}

// Канал удалён
function handlePushChannelDeleted(payload) {
    if (AppState.currentChannel && AppState.currentChannel.code === payload.channelCode) {
        AppState.currentChannel = null;
        showWelcome();
    }
    loadChannels();
}

// Пользователя удалили из канала
function handlePushRemovedFromChannel(payload) {
    if (AppState.currentChannel && AppState.currentChannel.code === payload.channelCode) {
        AppState.currentChannel = null;
        showWelcome();
        alert('Вас удалили из канала');
    }
    loadChannels();
}

// ========================================
// ПЕРЕКЛЮЧЕНИЕ ТЕМЫ
// ========================================

function toggleTheme() {
    var body = document.body;
    body.classList.toggle('light-theme');
    var btn = document.querySelector('.theme-btn');
    btn.textContent = body.classList.contains('light-theme') ? '\u263E' : '\u2600';
    localStorage.setItem('messenger_theme', body.classList.contains('light-theme') ? 'light' : 'dark');
}

function updateThemeBtn() {
    var btn = document.querySelector('.theme-btn');
    if (btn) {
        btn.textContent = document.body.classList.contains('light-theme') ? '\u263E' : '\u2600';
    }
}

// Создание DOM-элемента сообщения
function createMessageElement(msg) {
    var isOwner = (msg.userCode === Api.userCode);
    var msgDiv = document.createElement('div');
    msgDiv.className = 'message' + (isOwner ? ' my-message' : '');
    msgDiv.setAttribute('data-msg-id', msg.id);

    var senderName = getUserName(msg.userCode);
    var senderSpan = document.createElement('div');
    senderSpan.className = 'msg-sender';
    senderSpan.textContent = senderName;

    var textSpan = document.createElement('div');
    textSpan.className = 'msg-text';
    textSpan.textContent = msg.text;

    var dateSpan = document.createElement('div');
    dateSpan.className = 'msg-date';
    dateSpan.textContent = formatDate(msg.dateSend);

    msgDiv.appendChild(senderSpan);
    msgDiv.appendChild(textSpan);
    msgDiv.appendChild(dateSpan);

    if (isOwner) {
        var actions = document.createElement('div');
        actions.className = 'msg-actions';

        var editBtn = document.createElement('button');
        editBtn.textContent = '\u270E';
        editBtn.className = 'msg-btn';
        editBtn.title = 'Редактировать';
        editBtn.addEventListener('click', (function(msgId, currentText) {
            return function() { editMessage(msgId, currentText); };
        })(msg.id, msg.text));

        var delBtn = document.createElement('button');
        delBtn.textContent = '\u2715';
        delBtn.className = 'msg-btn delete';
        delBtn.title = 'Удалить';
        delBtn.addEventListener('click', (function(msgId) {
            return function() {
                if (confirm('Удалить сообщение?')) deleteMessage(msgId);
            };
        })(msg.id));

        actions.appendChild(editBtn);
        actions.appendChild(delBtn);
        msgDiv.appendChild(actions);
    }

    return msgDiv;
}

// ========================================
// ФОРМАТИРОВАНИЕ ДАТЫ
// ========================================

function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
        var d = new Date(dateStr);
        return d.toLocaleString('ru-RU');
    } catch (e) {
        return dateStr;
    }
}
