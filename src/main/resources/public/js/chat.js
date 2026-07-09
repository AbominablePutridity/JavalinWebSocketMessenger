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

    document.getElementById('messageSearch').oninput = Chat.search;

    // Открыть диалог выбора файлов при клике на кнопку скрепки
    document.getElementById('fileAttachBtn').onclick = function() {
        document.getElementById('fileInput').click();
    };

    // При выборе файлов — показываем их имена рядом с кнопкой
    document.getElementById('fileInput').onchange = function() {
        var namesEl = document.getElementById('fileNames');
        var files = this.files;
        if (files.length === 0) {
            namesEl.textContent = '';
            return;
        }
        var names = [];
        for (var i = 0; i < files.length; i++) {
            names.push(files[i].name);
        }
        namesEl.textContent = names.join(', ');
    };
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

    if (AppState.messagesSearch) {
        data.text = AppState.messagesSearch;
    }

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

Chat.search = function() {
    AppState.messagesPage = 1;
    AppState.messagesSearch = document.getElementById('messageSearch').value.trim();
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

// HTTP-база для загрузки файлов (адрес сервера, порт 7070)
// Не используем WS_URL напрямую — он определён в app.js (загружается позже)
var _httpBase = 'http://localhost:7070';

// =============================================
// send()
// Отправляет CREATE_MESSAGE на сервер.
// Если выбраны файлы — сначала загружает их
// через HTTP POST /api/files/upload, получает
// fileIds, и только потом отправляет сообщение
// с этими fileIds.
// При успехе очищает поле ввода, файлы и
// перезагружает сообщения.
// =============================================
Chat.send = function() {
    if (!AppState.currentChannel) return;

    var textInput = document.getElementById('messageInput');
    var text = textInput.value.trim();

    // Если нет ни текста, ни файлов — ничего не делаем
    var fileInput = document.getElementById('fileInput');
    var files = fileInput.files;
    if (!text && files.length === 0) return;

    var channelCode = AppState.currentChannel.code;

    // Функция, которая отправляет CREATE_MESSAGE после загрузки файлов
    var doSend = function(fileIds) {
        var msgData = {
            action: 'CREATE_MESSAGE',
            channelCode: channelCode,
            text: text
        };
        if (fileIds && fileIds.length > 0) {
            msgData.fileIds = fileIds;
        }

        Api.send(msgData, function(response) {
            if (response.status === 'SUCCESS') {
                textInput.value = '';
                fileInput.value = '';
                document.getElementById('fileNames').textContent = '';
                if (AppState.currentChannel && AppState.currentChannel.code === channelCode) {
                    Chat.load();
                }
            } else {
                alert('Ошибка: ' + response.error);
            }
        });
    };

    // Если есть файлы — загружаем их сначала
    if (files.length > 0) {
        var uploadedIds = [];
        var pending = files.length;

        for (var i = 0; i < files.length; i++) {
            (function(file) {
                var formData = new FormData();
                formData.append('file', file);
                if (Api.token) {
                    formData.append('token', Api.token);
                }

                fetch(_httpBase + '/api/files/upload', {
                    method: 'POST',
                    body: formData
                })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.status === 'SUCCESS' && data.payload) {
                        uploadedIds.push(data.payload.id);
                    }
                    pending--;
                    if (pending === 0) {
                        doSend(uploadedIds);
                    }
                })
                .catch(function() {
                    pending--;
                    if (pending === 0) {
                        doSend(uploadedIds);
                    }
                });
            })(files[i]);
        }
    } else {
        // Нет файлов — просто текст
        doSend([]);
    }
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
// formatFileSize(bytes)
// Форматирует размер в human-readable вид:
//   1024 → "1.0 KB"
//   1048576 → "1.0 MB"
// =============================================
function formatFileSize(bytes) {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
}

// =============================================
// createElement(msg)
// Создаёт DOM-элемент сообщения.
// Если сообщение принадлежит текущему
// пользователю — добавляет кнопки ✎ и ✕.
// Если есть прикреплённые файлы (msg.files) —
// отображает их как ссылки для скачивания.
// Формат:
//   ┌─────────────────────┐
//   │ Имя Фамилия         │
//   │ Текст сообщения     │
//   │ ┌─────────────────┐ │
//   │ │ 📎 photo.jpg    │ │  ← ссылка на скачивание
//   │ │   1.2 MB        │ │
//   │ └─────────────────┘ │
//   │ 06.07.2026 ✎ ✕      │
//   └─────────────────────┘
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

    msgDiv.appendChild(senderSpan);
    msgDiv.appendChild(textSpan);

    // Если есть прикреплённые файлы — добавляем их
    var files = msg.files;
    if (files && files.length > 0) {
        var filesDiv = document.createElement('div');
        filesDiv.className = 'msg-files';

        for (var i = 0; i < files.length; i++) {
            var f = files[i];
            var fileLink = document.createElement('a');
            fileLink.className = 'file-attachment';
            fileLink.href = _httpBase + '/api/files/' + f.id + '?token=' + encodeURIComponent(Api.token);
            fileLink.target = '_blank';
            fileLink.title = 'Скачать ' + f.fileName;

            var icon = document.createElement('span');
            icon.className = 'file-icon';
            icon.textContent = '\uD83D\uDCCE';

            var nameSpan = document.createElement('span');
            nameSpan.className = 'file-name';
            nameSpan.textContent = f.fileName;

            var sizeSpan = document.createElement('span');
            sizeSpan.className = 'file-size';
            sizeSpan.textContent = formatFileSize(f.fileSize);

            fileLink.appendChild(icon);
            fileLink.appendChild(nameSpan);
            fileLink.appendChild(sizeSpan);
            filesDiv.appendChild(fileLink);
        }

        msgDiv.appendChild(filesDiv);
    }

    var dateSpan = document.createElement('div');
    dateSpan.className = 'msg-date';
    dateSpan.textContent = formatDate(msg.dateSend);

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
