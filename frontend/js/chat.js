// =============================================
// СООБЩЕНИЯ — шаблон и логика чата
// =============================================
// Этот модуль отвечает за правую панель в режиме
// чата: отображение сообщений, отправка новых,
// редактирование/удаление своих, пагинация.
// Сообщения привязываются к userCode, поэтому
// можно определить, чьё сообщение — своё или
// чужое (для стилизации и отображения кнопок).
// =============================================

var Chat = {};

// =============================================
// renderChatPanel()
// Возвращает HTML панели чата:
//   ┌──────────────────────────┐
//   │  Название_канала  [О канале] │
//   ├──────────────────────────┤
//   │  [Сообщение 1]           │
//   │  [Сообщение 2]           │
//   │  ...                     │
//   ├──────────────────────────┤
//   │  ←  1  →            пагинация │
//   ├──────────────────────────┤
//   │  [Введите сообщение...]  │
//   │  [Отправить]             │
//   └──────────────────────────┘
// =============================================
Chat.renderChatPanel = function() {
    return '' +
        '<div id="rightPanel" class="panel right-content" style="display:none;">' +
            '<div class="chat-header">' +
                '<h2 id="chatChannelName" class="chat-title">Канал</h2>' +
                '<button id="channelInfoBtn" class="info-btn">О канале</button>' +
            '</div>' +
            '<div id="messagesContainer" class="messages-container"></div>' +
            '<div class="pagination pagination-msg">' +
                '<button id="messagesPrevBtn">&larr;</button>' +
                '<span id="messagesPageInfo">1</span>' +
                '<button id="messagesNextBtn">&rarr;</button>' +
            '</div>' +
            '<div class="message-input-area">' +
                '<textarea id="messageInput" class="message-input" placeholder="Введите сообщение..."></textarea>' +
                '<button class="send-btn">Отправить</button>' +
            '</div>' +
        '</div>';
};

// =============================================
// initChatPanel()
// Навешивает обработчики: кнопка "О канале",
// пагинация сообщений, отправка, Enter.
// =============================================
Chat.initChatPanel = function() {
    document.getElementById('channelInfoBtn').onclick = ChannelInfo.show;
    document.getElementById('messagesPrevBtn').onclick = Chat.prevPage;
    document.getElementById('messagesNextBtn').onclick = Chat.nextPage;
    document.querySelector('.send-btn').onclick = Chat.send;
    document.getElementById('messageInput').onkeydown = Chat.handleKey;
};

// =============================================
// load()
// Отправляет SEARCH_MESSAGES для текущего канала.
// Защита от гонок: если канал изменился до
// получения ответа — сообщения не вставляются.
// =============================================
Chat.load = function() {
    if (!AppState.currentChannel) return;

    var channelCode = AppState.currentChannel.code;

    var data = {
        action: 'SEARCH_MESSAGES',
        channelCode: channelCode,
        page: AppState.messagesPage,
        size: 50
    };

    document.getElementById('messagesPageInfo').textContent = AppState.messagesPage;

    Api.send(data, function(response) {
        if (response.status === 'SUCCESS') {
            // Проверяем, что канал не переключился
            if (AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                Chat.renderMessages(response.payload);
            }
        }
    });
};

// =============================================
// prevPage / nextPage
// Пагинация сообщений.
// =============================================
Chat.prevPage = function() {
    if (AppState.messagesPage > 1) {
        AppState.messagesPage--;
        Chat.load();
    }
};

Chat.nextPage = function() {
    AppState.messagesPage++;
    Chat.load();
};

// =============================================
// renderMessages(data)
// Очищает контейнер сообщений и рендерит
// новые. Сообщения приходят от сервера от
// новых к старым — переворачиваем (reverse),
// чтобы в DOM они шли сверху вниз.
// После рендера скроллим вниз.
// =============================================
Chat.renderMessages = function(data) {
    var container = document.getElementById('messagesContainer');
    container.innerHTML = '';

    var messages = data.messages || [];

    if (messages.length === 0) {
        container.innerHTML = '<div class="msg-empty">Нет сообщений</div>';
        return;
    }

    // Переворачиваем: от сервера идут от новых к старым,
    // а в DOM должны быть от старых (сверху) к новым (снизу)
    messages = messages.reverse();

    for (var i = 0; i < messages.length; i++) {
        container.appendChild(Chat.createElement(messages[i]));
    }

    container.scrollTop = container.scrollHeight;
};

// =============================================
// send()
// Отправляет CREATE_MESSAGE на сервер.
// При успехе очищает поле ввода и перезагружает
// сообщения.
// =============================================
Chat.send = function() {
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
            if (AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                Chat.load();
            }
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
};

// =============================================
// handleKey(event)
// Enter — отправить, Shift+Enter — новая строка.
// =============================================
Chat.handleKey = function(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        Chat.send();
    }
};

// =============================================
// edit(msgId, currentText)
// Запрашивает у пользователя новый текст
// (prompt) и отправляет UPDATE_MESSAGE.
// =============================================
Chat.edit = function(msgId, currentText) {
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
                    Chat.load();
                }
            } else {
                alert('Ошибка: ' + response.error);
            }
        });
    }
};

// =============================================
// delete(msgId)
// Отправляет DELETE_MESSAGE. Подтверждение
// запрашивается на уровень выше (в createElement).
// =============================================
Chat.delete = function(msgId) {
    var channelCode = AppState.currentChannel ? AppState.currentChannel.code : null;
    Api.send({
        action: 'DELETE_MESSAGE',
        messageId: msgId
    }, function(response) {
        if (response.status === 'SUCCESS') {
            if (channelCode && AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                Chat.load();
            }
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
};

// =============================================
// createElement(msg)
// Создаёт DOM-элемент сообщения.
// Если сообщение принадлежит текущему
// пользователю — добавляет кнопки ✎ и ✕.
// Формат:
//   ┌──────────────────┐
//   │ Имя Фамилия      │
//   │ Текст сообщения  │
//   │ 06.07.2026 ✎ ✕   │
//   └──────────────────┘
// =============================================
Chat.createElement = function(msg) {
    var isOwner = (msg.userCode === Api.userCode);
    var msgDiv = document.createElement('div');
    msgDiv.className = 'message' + (isOwner ? ' my-message' : '');
    msgDiv.setAttribute('data-msg-id', msg.id);

    var senderName = ChannelInfo.getUserName(msg.userCode);
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
        editBtn.onclick = (function(msgId, currentText) {
            return function() { Chat.edit(msgId, currentText); };
        })(msg.id, msg.text);

        var delBtn = document.createElement('button');
        delBtn.textContent = '\u2715';
        delBtn.className = 'msg-btn delete';
        delBtn.title = 'Удалить';
        delBtn.onclick = (function(msgId) {
            return function() {
                if (confirm('Удалить сообщение?')) Chat.delete(msgId);
            };
        })(msg.id);

        actions.appendChild(editBtn);
        actions.appendChild(delBtn);
        msgDiv.appendChild(actions);
    }

    return msgDiv;
};
