// =============================================
// КАНАЛЫ — шаблон и логика левой панели
// =============================================
// Этот модуль управляет списком каналов в левой
// панели: отображение, поиск, создание, паги-
// нация, отметки "непрочитано". Каналы загру-
// жаются с сервера через SEARCH_CHANNELS.
// У каждого канала есть код (UUID), имя, опи-
// сание, владелец.
// =============================================

var Channels = {};

// =============================================
// initLeftPanel()
// Навешивает обработчики на кнопки и поля ввода
// левой панели.
// =============================================
Channels.initLeftPanel = function() {
    document.getElementById('createChannelToggle').onclick = Channels.toggleCreate;
    document.getElementById('createChannelBtn').onclick = Channels.create;
    document.getElementById('createChannelCancel').onclick = Channels.toggleCreate;
    document.getElementById('channelSearch').oninput = Channels.search;
    document.getElementById('channelsPrevBtn').onclick = Channels.prevPage;
    document.getElementById('channelsNextBtn').onclick = Channels.nextPage;
};

// =============================================
// toggleCreate()
// Показывает/скрывает форму создания канала.
// =============================================
Channels.toggleCreate = function() {
    var panel = document.getElementById('createChannelPanel');
    var isHidden = panel.style.display === 'none';
    panel.style.display = isHidden ? 'flex' : 'none';
};

// =============================================
// load()
// Отправляет SEARCH_CHANNELS на сервер.
// Использует _lastChannelsReq для защиты от
// гонок: если пришёл ответ от устаревшего
// запроса — он игнорируется.
// =============================================
Channels.load = function() {
    var data = {
        action: 'SEARCH_CHANNELS',
        page: AppState.channelsPage,
        size: 50
    };

    if (AppState.channelsSearch) {
        data.name = AppState.channelsSearch;
    }

    document.getElementById('channelPageInfo').textContent = AppState.channelsPage;

    var reqId = ++_lastChannelsReq;

    Api.send(data, function(response) {
        if (response.status === 'SUCCESS') {
            // Проверяем, не устарел ли запрос
            if (reqId === _lastChannelsReq) {
                Channels.renderList(response.payload.channels);
            }
        }
    });
};

// =============================================
// search()
// Вызывается при вводе текста в поле поиска.
// Сбрасывает страницу на 1 и перезагружает.
// =============================================
Channels.search = function() {
    AppState.channelsPage = 1;
    AppState.channelsSearch = document.getElementById('channelSearch').value.trim();
    Channels.load();
};

// =============================================
// prevPage / nextPage
// Листание страниц списка каналов.
// =============================================
Channels.prevPage = function() {
    if (AppState.channelsPage > 1) {
        AppState.channelsPage--;
        Channels.load();
    }
};

Channels.nextPage = function() {
    AppState.channelsPage++;
    Channels.load();
};

// =============================================
// renderList(channels)
// Отрисовывает кнопки каналов в левой панели.
// Каждая кнопка при клике вызывает
// App.selectChannel(). Текущий активный
// канал подсвечивается классом 'active'.
// =============================================
Channels.renderList = function(channels) {
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

        if (AppState.currentChannel && AppState.currentChannel.code === ch.code) {
            btn.classList.add('active');
        }

        btn.onclick = (function(channel) {
            return function() { App.selectChannel(channel); };
        })(ch);

        container.appendChild(btn);
    }
};

// =============================================
// markUnread / clearUnread
// Управляют визуальной меткой "непрочитано"
// на кнопке канала (класс 'has-unread').
// Используется push-handler при получении
// нового сообщения в неактивном канале.
// =============================================
Channels.markUnread = function(channelCode) {
    if (AppState.unreadChannels[channelCode]) return;
    AppState.unreadChannels[channelCode] = true;
    var btn = document.querySelector('.channel-btn[data-code="' + channelCode + '"]');
    if (btn) btn.classList.add('has-unread');
};

Channels.clearUnread = function(channelCode) {
    delete AppState.unreadChannels[channelCode];
    var btn = document.querySelector('.channel-btn[data-code="' + channelCode + '"]');
    if (btn) btn.classList.remove('has-unread');
};

// =============================================
// create()
// Отправляет CREATE_CHANNEL на сервер.
// При успехе обновляет список каналов
// (сбрасывает на первую страницу).
// =============================================
Channels.create = function() {
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
            AppState.channelsPage = 1;
            Channels.load();
            alert('Канал создан');
        } else {
            alert('Ошибка: ' + response.error);
        }
    });
};
