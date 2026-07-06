// =============================================
// API-слой для WebSocket-соединения
// =============================================
// Этот модуль — единственное окно в мир сервера.
// Все запросы к бэкенду идут через единственный
// WebSocket. Ответы сопоставляются с запросами
// через числовой requestId. Push-уведомления от
// сервера (новые сообщения, изменения каналов)
// приходят без requestId и направляются в onPush.
// =============================================

var Api = {
    ws: null,                       // нативный WebSocket-объект
    connected: false,               // флаг: открыто ли соединение
    callbacks: {},                  // { requestId -> function } для сопоставления ответов с запросами
    onPush: null,                   // callback для push-событий от сервера (без requestId)
    onClose: null,                  // callback при закрытии соединения (кроме intentional)
    onReconnect: null,              // callback после успешного переподключения (нужен для реавторизации)
    token: null,                    // JWT-токен (вкладывается в каждый запрос)
    userCode: null,                 // UUID текущего пользователя
    userName: null,                 // имя пользователя (для отображения)
    userSurname: null,              // фамилия
    login: null,                    // логин (хранится только в памяти для восстановления сессии)
    password: null,                 // пароль (хранится только в памяти, не на диске!)
    _reqSeq: 0,                     // счётчик для генерации уникальных requestId
    _authenticated: false,          // true после успешного LOGIN/REGISTER (влияет на поведение при разрыве)
    _reconnectUrl: null,            // URL для переподключения
    _reconnectAttempts: 0,          // сколько раз уже пытались переподключиться
    _maxReconnectAttempts: 20,      // максимум попыток переподключения
    _reconnectDelay: 1000,          // начальная задержка перед первой попыткой (ms)
    _intentionalDisconnect: false,  // true, если disconnect() вызван намеренно (логаут)
    _heartbeatTimer: null,          // таймер heartbeat'а
    _heartbeatInterval: 30000       // интервал отправки PING (30 секунд)
};

// =============================================
// connect(url, onOpen?, onClose?)
// Инициирует WebSocket-соединение.
// - url: адрес WebSocket-сервера
// - onOpen: вызывается при успешном открытии
// - onClose: вызывается при закрытии (если не удалось переподключиться)
// Все старые колбэки сбрасываются.
// =============================================
Api.connect = function(url, onOpen, onClose) {
    Api.onClose = onClose;
    Api.callbacks = {};
    Api._reconnectUrl = url;
    Api._reconnectAttempts = 0;
    Api._intentionalDisconnect = false;
    Api._authenticated = false;

    Api._createWebSocket(url, onOpen);
};

// =============================================
// _createWebSocket(url, onOpen?)
// Внутренний метод — создаёт нативный WebSocket.
// Если ранее было соединение — аккуратно закрывает его.
// После открытия запускает heartbeat.
// При получении сообщения диспетчеризует:
//   - если есть requestId → ищем колбэк в Api.callbacks
//   - иначе → onPush (серверное push-событие)
// =============================================
Api._createWebSocket = function(url, onOpen) {
    if (Api.ws) {
        Api.ws.onopen = null;
        Api.ws.onclose = null;
        Api.ws.onmessage = null;
        Api.ws.onerror = null;
        Api.ws.close();
    }

    Api._stopHeartbeat();
    Api.ws = new WebSocket(url);

    Api.ws.onopen = function() {
        Api.connected = true;
        Api._reconnectAttempts = 0;
        Api._startHeartbeat();
        if (onOpen) onOpen();
    };

    Api.ws.onclose = function() {
        Api.connected = false;
        Api._stopHeartbeat();
        Api.callbacks = {};

        // Если разрыв намеренный — ничего не делаем
        if (Api._intentionalDisconnect) return;

        // Если были авторизованы — пытаемся восстановить соединение
        if (Api._authenticated) {
            Api._tryReconnect();
        } else if (Api.onClose) {
            Api.onClose();
        }
    };

    Api.ws.onerror = function() {
        // ошибка всегда предшествует закрытию, логика вся в onclose
    };

    // =============================================
    // Обработка входящих сообщений с сервера
    // =============================================
    Api.ws.onmessage = function(event) {
        var response;
        try {
            response = JSON.parse(event.data);
        } catch (e) {
            console.error('Не удалось распарсить ответ:', event.data);
            return;
        }

        // Если сообщение — ответ на наш запрос (есть requestId)
        if (response.requestId != null && Api.callbacks[response.requestId]) {
            var callback = Api.callbacks[response.requestId];
            delete Api.callbacks[response.requestId];
            callback(response);
        } else if (Api.onPush) {
            // Иначе это push-уведомление от сервера
            Api.onPush(response);
        } else {
            console.warn('Нет обработчика для:', response.action);
        }
    };
};

// =============================================
// _tryReconnect()
// Экспоненциальная задержка переподключения.
// Формула: delay = 1000 * 1.5^(attempt-1), но не более 30 секунд.
// Максимум 20 попыток, после чего вызывается onClose.
// После успешного переподключения вызывается
// onReconnect для повторной авторизации.
// =============================================
Api._tryReconnect = function() {
    if (Api._reconnectAttempts >= Api._maxReconnectAttempts) {
        console.error('Не удалось переподключиться после ' + Api._maxReconnectAttempts + ' попыток');
        if (Api.onClose) Api.onClose();
        return;
    }

    Api._reconnectAttempts++;
    var delay = Math.min(Api._reconnectDelay * Math.pow(1.5, Api._reconnectAttempts - 1), 30000);

    setTimeout(function() {
        if (Api._intentionalDisconnect) return;

        Api._createWebSocket(Api._reconnectUrl, function() {
            if (Api.onReconnect) Api.onReconnect();
        });
    }, delay);
};

// =============================================
// _startHeartbeat / _stopHeartbeat
// Каждые 30 секунд отправляет PING, чтобы
// поддерживать WebSocket живым (некоторые
// прокси/балансировщики обрывают бездействующие
// соединения). При разрыве heartbeat гасится.
// =============================================
Api._startHeartbeat = function() {
    Api._stopHeartbeat();
    Api._heartbeatTimer = setInterval(function() {
        if (Api.ws && Api.ws.readyState === WebSocket.OPEN) {
            Api.ws.send(JSON.stringify({ action: 'PING' }));
        }
    }, Api._heartbeatInterval);
};

Api._stopHeartbeat = function() {
    if (Api._heartbeatTimer) {
        clearInterval(Api._heartbeatTimer);
        Api._heartbeatTimer = null;
    }
};

// =============================================
// send(data, callback?)
// Отправляет JSON-объект через WebSocket.
// Автоматически вкладывает JWT-токен в поле "token".
// Если передан callback — генерирует requestId
// и регистрирует колбэк для ответа.
// Если колбэк не передан — это fire-and-forget.
// =============================================
Api.send = function(data, callback) {
    if (!Api.ws || Api.ws.readyState !== WebSocket.OPEN) {
        console.error('WebSocket не подключён');
        return;
    }

    if (Api.token) {
        data.token = Api.token;
    }

    if (callback) {
        var reqId = ++Api._reqSeq;
        data.requestId = reqId;
        Api.callbacks[reqId] = callback;
    }

    Api.ws.send(JSON.stringify(data));
};

// =============================================
// disconnect()
// Намеренное закрытие соединения (логаут).
// Устанавливает флаг _intentionalDisconnect,
// чтобы _tryReconnect не сработал.
// Очищает все колбэки и останавливает heartbeat.
// =============================================
Api.disconnect = function() {
    Api._intentionalDisconnect = true;
    Api._authenticated = false;
    Api._stopHeartbeat();
    if (Api.ws) {
        Api.ws.close();
        Api.ws = null;
        Api.connected = false;
        Api.callbacks = {};
    }
};
