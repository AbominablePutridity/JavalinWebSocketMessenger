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
