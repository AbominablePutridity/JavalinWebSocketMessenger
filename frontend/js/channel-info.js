// =============================================
// СТРАНИЦА "О КАНАЛЕ" — шаблон и логика
// =============================================
// Этот модуль отображает информацию о выбранном
// канале: название, описание, дату создания,
// владельца, список участников. Владелец канала
// видит дополнительные инструменты: редактиро-
// вание названия/описания, добавление/удаление
// участников, удаление канала.
// =============================================

var ChannelInfo = {};

ChannelInfo.MEMBERS_PAGE_SIZE = 20;

// =============================================
// renderInfoPanel()
// Возвращает HTML панели "О канале":
//   ┌──────────────────────────┐
//   │ ← Назад к чату           │
//   │                          │
//   │ О канале                 │
//   │ Название: Friends        │
//   │ Описание: Для друзей     │
//   │ Создан: 06.07.2026      │
//   │ Владелец: Alice          │
//   │                          │
//   │ Участники                │
//   │ ┌──────────────────────┐ │
//   │ │ Alice (abcd123...)  ✕│ │
//   │ │ Bob (efgh456...)    ✕│ │
//   │ └──────────────────────┘ │
//   │  ← 1 →                  │
//   │                          │
//   │ [Управление каналом]     │
//   │   (только для владельца) │
//   └──────────────────────────┘
// =============================================
ChannelInfo.renderInfoPanel = function() {
    return '' +
        '<div id="channelInfoPanel" class="panel channel-info-panel" style="display:none;">' +
            '<div class="info-header">' +
                '<button id="backToChatBtn" class="back-btn">&larr; Назад к чату</button>' +
            '</div>' +
            '<h2>О канале</h2>' +
            '<div class="info-section">' +
                '<div class="info-row">' +
                    '<span class="info-label">Название:</span>' +
                    '<span id="infoName" class="info-value"></span>' +
                '</div>' +
                '<div class="info-row">' +
                    '<span class="info-label">Описание:</span>' +
                    '<span id="infoDescription" class="info-value"></span>' +
                '</div>' +
                '<div class="info-row">' +
                    '<span class="info-label">Дата создания:</span>' +
                    '<span id="infoDate" class="info-value"></span>' +
                '</div>' +
                '<div class="info-row">' +
                    '<span class="info-label">Владелец:</span>' +
                    '<span id="infoOwner" class="info-value"></span>' +
                '</div>' +
            '</div>' +
            '<h3>Участники</h3>' +
            '<div id="membersList" class="members-list"></div>' +
            '<div class="pagination pagination-members">' +
                '<button id="membersPrevBtn">&larr;</button>' +
                '<span id="membersPageInfo">1</span>' +
                '<button id="membersNextBtn">&rarr;</button>' +
            '</div>' +
            '<div id="ownerTools" class="owner-tools" style="display:none;">' +
                '<h3>Управление каналом</h3>' +
                '<div class="tool-section">' +
                    '<h4>Редактировать канал</h4>' +
                    '<input id="editName" type="text" placeholder="Название">' +
                    '<input id="editDescription" type="text" placeholder="Описание">' +
                    '<button id="updateChannelBtn">Сохранить</button>' +
                '</div>' +
                '<div class="tool-section">' +
                    '<h4>Добавить участника</h4>' +
                    '<input id="addMemberInput" type="text" placeholder="Код пользователя">' +
                    '<button id="addMemberBtn">Добавить</button>' +
                '</div>' +
                '<div class="tool-section">' +
                    '<h4>Опасная зона</h4>' +
                    '<button id="deleteChannelBtn" class="danger-btn">Удалить канал</button>' +
                '</div>' +
            '</div>' +
        '</div>';
};

// =============================================
// initInfoPanel()
// Навешивает обработчики: назад, пагинация
// участников, обновление/добавление/удаление.
// =============================================
ChannelInfo.initInfoPanel = function() {
    document.getElementById('backToChatBtn').onclick = App.showChatView;
    document.getElementById('membersPrevBtn').onclick = ChannelInfo.prevMembersPage;
    document.getElementById('membersNextBtn').onclick = ChannelInfo.nextMembersPage;
    document.getElementById('updateChannelBtn').onclick = ChannelInfo.update;
    document.getElementById('addMemberBtn').onclick = ChannelInfo.addMember;
    document.getElementById('deleteChannelBtn').onclick = ChannelInfo.deleteCurrent;
};

// =============================================
// loadMembers(channelCode)
// Запрашивает список участников канала с сервера
// и кеширует результат в AppState.membersCache.
// =============================================
ChannelInfo.loadMembers = function(channelCode) {
    Api.send({
        action: 'GET_CHANNEL_MEMBERS',
        channelCode: channelCode
    }, function(response) {
        if (response.status === 'SUCCESS') {
            AppState.membersCache[channelCode] = response.payload.members || [];
        }
    });
};

// =============================================
// getUserName(userCode)
// По коду пользователя возвращает "Имя Фамилия".
// Сначала ищет в кеше участников текущего канала,
// потом — в данных текущего пользователя.
// Если не найдено — показывает первые 8 символов
// кода + "...", чтобы не показывать UUID целиком.
// =============================================
ChannelInfo.getUserName = function(userCode) {
    if (!AppState.currentChannel) return userCode;

    var members = AppState.membersCache[AppState.currentChannel.code] || [];
    for (var i = 0; i < members.length; i++) {
        if (members[i].userCode === userCode) {
            return members[i].name + ' ' + members[i].surname;
        }
    }
    if (userCode === Api.userCode) {
        return (Api.userName || '') + ' ' + (Api.userSurname || '');
    }
    return userCode.substring(0, 8) + '...';
};

// =============================================
// show()
// Переключает правую панель в режим "О канале".
// Заполняет поля информацией, показывает
// инструменты управления, если пользователь
// является владельцем канала.
// Загружает список участников и через 200ms
// отрисовывает его (даём время на ответ сервера).
// =============================================
ChannelInfo.show = function() {
    if (!AppState.currentChannel) return;

    AppState.currentView = 'info';

    document.getElementById('rightPanel').style.display = 'none';
    document.getElementById('welcomePanel').style.display = 'none';
    document.getElementById('channelInfoPanel').style.display = 'block';

    var ch = AppState.currentChannel;
    var isOwner = (ch.ownerCode === Api.userCode);

    document.getElementById('infoName').textContent = ch.name;
    document.getElementById('infoDescription').textContent = ch.description || '(нет описания)';
    document.getElementById('infoDate').textContent = formatDate(ch.creationDate);
    document.getElementById('infoOwner').textContent = ChannelInfo.getUserName(ch.ownerCode);

    var ownerTools = document.getElementById('ownerTools');
    if (isOwner) {
        ownerTools.style.display = 'block';
        document.getElementById('editName').value = ch.name;
        document.getElementById('editDescription').value = ch.description || '';
    } else {
        ownerTools.style.display = 'none';
    }

    AppState.membersPage = 1;

    ChannelInfo.loadMembers(ch.code);
    setTimeout(function() {
        ChannelInfo.renderMembers(ch.code);
    }, 200);
};

// =============================================
// prevMembersPage / nextMembersPage
// Пагинация списка участников (клиентская,
// т.к. сервер возвращает сразу всех).
// =============================================
ChannelInfo.prevMembersPage = function() {
    if (AppState.membersPage > 1) {
        AppState.membersPage--;
        if (AppState.currentChannel) {
            ChannelInfo.renderMembers(AppState.currentChannel.code);
        }
    }
};

ChannelInfo.nextMembersPage = function() {
    AppState.membersPage++;
    if (AppState.currentChannel) {
        ChannelInfo.renderMembers(AppState.currentChannel.code);
    }
};

// =============================================
// renderMembers(channelCode)
// Отрисовывает список участников из кеша
// с клиентской пагинацией (20 на страницу).
// Владелец видит кнопки "✕" для удаления
// каждого участника (кроме себя).
// =============================================
ChannelInfo.renderMembers = function(channelCode) {
    var container = document.getElementById('membersList');
    container.innerHTML = '';

    var allMembers = AppState.membersCache[channelCode] || [];

    if (allMembers.length === 0) {
        container.innerHTML = '<div class="member-item">Нет участников</div>';
        return;
    }

    var totalPages = Math.ceil(allMembers.length / ChannelInfo.MEMBERS_PAGE_SIZE);
    if (AppState.membersPage > totalPages) AppState.membersPage = totalPages;
    if (AppState.membersPage < 1) AppState.membersPage = 1;

    document.getElementById('membersPageInfo').textContent = AppState.membersPage;

    var start = (AppState.membersPage - 1) * ChannelInfo.MEMBERS_PAGE_SIZE;
    var end = Math.min(start + ChannelInfo.MEMBERS_PAGE_SIZE, allMembers.length);
    var pageMembers = allMembers.slice(start, end);

    for (var i = 0; i < pageMembers.length; i++) {
        var m = pageMembers[i];
        var div = document.createElement('div');
        div.className = 'member-item';
        div.textContent = m.name + ' ' + m.surname + ' (' + m.userCode.substring(0, 8) + '...)';

        if (AppState.currentChannel && AppState.currentChannel.ownerCode === Api.userCode) {
            if (m.userCode !== Api.userCode) {
                var removeBtn = document.createElement('button');
                removeBtn.textContent = '\u2715';
                removeBtn.className = 'msg-btn delete';
                removeBtn.title = 'Удалить из канала';
                removeBtn.style.marginLeft = '10px';
                removeBtn.onclick = (function(memberCode) {
                    return function() {
                        if (confirm('Удалить участника из канала?')) {
                            ChannelInfo.removeMember(memberCode);
                        }
                    };
                })(m.userCode);
                div.appendChild(removeBtn);
            }
        }

        container.appendChild(div);
    }
};

// =============================================
// update()
// Отправляет UPDATE_CHANNEL для изменения
// названия и описания канала.
// Обновляет данные на месте и перезагружает
// список каналов (чтобы изменения отразились
// и в левой панели).
// =============================================
ChannelInfo.update = function() {
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

            document.getElementById('infoName').textContent = response.payload.name;
            document.getElementById('infoDescription').textContent = response.payload.description || '(нет описания)';

            Channels.load();
            alert('Канал обновлён');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
};

// =============================================
// addMember()
// Добавляет участника в канал по userCode.
// При успехе обновляет список участников.
// =============================================
ChannelInfo.addMember = function() {
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
            ChannelInfo.loadMembers(AppState.currentChannel.code);
            setTimeout(function() {
                ChannelInfo.renderMembers(AppState.currentChannel.code);
            }, 200);
            alert('Участник добавлен');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
};

// =============================================
// removeMember(memberCode)
// Удаляет участника из канала (только владелец).
// =============================================
ChannelInfo.removeMember = function(memberCode) {
    if (!AppState.currentChannel) return;

    Api.send({
        action: 'REMOVE_MEMBER',
        channelCode: AppState.currentChannel.code,
        memberCode: memberCode
    }, function(response) {
        if (response.status === 'SUCCESS') {
            ChannelInfo.loadMembers(AppState.currentChannel.code);
            setTimeout(function() {
                ChannelInfo.renderMembers(AppState.currentChannel.code);
            }, 200);
            alert('Участник удалён');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
};

// =============================================
// deleteCurrent()
// Удаляет канал целиком (только владелец).
// После удаления возвращает на welcome-экран
// и перезагружает список каналов.
// =============================================
ChannelInfo.deleteCurrent = function() {
    if (!AppState.currentChannel) return;
    if (!confirm('Вы уверены, что хотите удалить канал "' + AppState.currentChannel.name + '"? Это действие необратимо.')) return;

    Api.send({
        action: 'DELETE_CHANNEL',
        code: AppState.currentChannel.code
    }, function(response) {
        if (response.status === 'SUCCESS') {
            AppState.currentChannel = null;
            Channels.load();
            App.showWelcome();
            alert('Канал удалён');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
};
