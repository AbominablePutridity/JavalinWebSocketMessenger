// =============================================
// PUSH-СОБЫТИЯ (от сервера без запроса)
// =============================================
// Этот модуль обрабатывает push-уведомления,
// которые сервер отправляет всем участникам
// канала при изменениях. Push-события приходят
// через тот же WebSocket, но без requestId,
// поэтому Api.ws.onmessage направляет их
// в Api.onPush → PushHandler.handle.
//
// Типы push-событий:
//   NEW_MESSAGE       — новое сообщение в канале
//   MESSAGE_UPDATED   — сообщение изменено
//   MESSAGE_DELETED   — сообщение удалено
//   CHANNEL_ADDED     — пользователя добавили в канал
//   CHANNEL_UPDATED   — канал изменён
//   CHANNEL_DELETED   — канал удалён
//   REMOVED_FROM_CHANNEL — пользователя удалили из канала
// =============================================

var PushHandler = {};

// =============================================
// handle(response)
// Диспетчер: определяет тип события по полю
// action и вызывает соответствующий обработчик.
// =============================================
PushHandler.handle = function(response) {
    var action = response.action;
    var payload = response.payload;

    switch (action) {
        case 'NEW_MESSAGE':
            PushHandler.newMessage(payload);
            break;
        case 'MESSAGE_UPDATED':
            PushHandler.messageUpdated(payload);
            break;
        case 'MESSAGE_DELETED':
            PushHandler.messageDeleted(payload);
            break;
        case 'CHANNEL_ADDED':
            PushHandler.channelAdded(payload);
            break;
        case 'CHANNEL_UPDATED':
            PushHandler.channelUpdated(payload);
            break;
        case 'CHANNEL_DELETED':
            PushHandler.channelDeleted(payload);
            break;
        case 'REMOVED_FROM_CHANNEL':
            PushHandler.removedFromChannel(payload);
            break;
    }
};

// =============================================
// newMessage(payload)
// Если пользователь смотрит этот канал на 1-й
// странице сообщений — добавляет новое сообще-
// ние в DOM (без перезагрузки). Иначе ставит
// метку "непрочитано" на кнопке канала.
// =============================================
PushHandler.newMessage = function(payload) {
    var isCurrent = AppState.currentChannel && AppState.currentChannel.code === payload.channelCode;
    var isShowingLatest = AppState.currentView === 'chat' && AppState.messagesPage === 1;

    if (isCurrent && isShowingLatest) {
        var container = document.getElementById('messagesContainer');
        var emptyMsg = container.querySelector('.msg-empty');
        if (emptyMsg) container.innerHTML = '';

        var msgDiv = Chat.createElement(payload);
        container.appendChild(msgDiv);
        container.scrollTop = container.scrollHeight;
    } else {
        Channels.markUnread(payload.channelCode);
    }
};

// =============================================
// messageUpdated(payload)
// Находит сообщение по data-msg-id в DOM
// и обновляет его текст без перезагрузки.
// =============================================
PushHandler.messageUpdated = function(payload) {
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
};

// =============================================
// messageDeleted(payload)
// Удаляет сообщение из DOM по data-msg-id.
// =============================================
PushHandler.messageDeleted = function(payload) {
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
};

// =============================================
// channelAdded(payload)
// Пользователя добавили в новый канал —
// перезагружаем список каналов.
// =============================================
PushHandler.channelAdded = function(payload) {
    Channels.load();
};

// =============================================
// channelUpdated(payload)
// Канал изменён — обновляем название/описание
// на месте, если он сейчас выбран, и
// перезагружаем список каналов.
// =============================================
PushHandler.channelUpdated = function(payload) {
    if (AppState.currentChannel && AppState.currentChannel.code === payload.channelCode) {
        AppState.currentChannel.name = payload.name;
        AppState.currentChannel.description = payload.description;

        var chatTitle = document.getElementById('chatChannelName');
        if (chatTitle) chatTitle.textContent = payload.name;

        document.getElementById('infoName').textContent = payload.name;
        document.getElementById('infoDescription').textContent = payload.description || '(нет описания)';
    }
    Channels.load();
};

// =============================================
// channelDeleted(payload)
// Текущий канал удалён — переключаемся на
// welcome и перезагружаем список.
// =============================================
PushHandler.channelDeleted = function(payload) {
    if (AppState.currentChannel && AppState.currentChannel.code === payload.channelCode) {
        AppState.currentChannel = null;
        App.showWelcome();
    }
    Channels.load();
};

// =============================================
// removedFromChannel(payload)
// Пользователя удалили из канала — показываем
// alert, переключаем на welcome, перезагружаем
// список каналов.
// =============================================
PushHandler.removedFromChannel = function(payload) {
    if (AppState.currentChannel && AppState.currentChannel.code === payload.channelCode) {
        AppState.currentChannel = null;
        App.showWelcome();
        alert('Вас удалили из канала');
    }
    Channels.load();
};
