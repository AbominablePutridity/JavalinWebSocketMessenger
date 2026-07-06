// ========================================
// API-слой для WebSocket-соединения
// ========================================

var Api = {
    ws: null,
    connected: false,
    callbacks: {},              // requestId -> callback
    onPush: null,               // callback для push-событий (без requestId)
    onClose: null,
    onReconnect: null,          // callback для восстановления сессии после переподключения
    token: null,
    userCode: null,
    userName: null,
    userSurname: null,
    login: null,                // для восстановления сессии (не сохраняется на диск)
    password: null,             // для восстановления сессии (не сохраняется на диск)
    _reqSeq: 0,                 // счётчик для requestId
    _authenticated: false,      // true после успешного LOGIN/REGISTER
    _reconnectUrl: null,
    _reconnectAttempts: 0,
    _maxReconnectAttempts: 20,
    _reconnectDelay: 1000,      // начальная задержка (ms)
    _intentionalDisconnect: false,
    _heartbeatTimer: null,
    _heartbeatInterval: 30000   // 30 секунд
};

Api.connect = function(url, onOpen, onClose) {
    Api.onClose = onClose;
    Api.callbacks = {};
    Api._reconnectUrl = url;
    Api._reconnectAttempts = 0;
    Api._intentionalDisconnect = false;
    Api._authenticated = false;

    Api._createWebSocket(url, onOpen);
};

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

        if (Api._intentionalDisconnect) return;

        if (Api._authenticated) {
            Api._tryReconnect();
        } else if (Api.onClose) {
            Api.onClose();
        }
    };

    Api.ws.onerror = function() {
        // onerror всегда предшествует onclose, всё обрабатывается там
    };

    // При получении сообщения — ищем колбэк по requestId или вызываем onPush
    Api.ws.onmessage = function(event) {
        var response;
        try {
            response = JSON.parse(event.data);
        } catch (e) {
            console.error('Не удалось распарсить ответ:', event.data);
            return;
        }

        // Если есть requestId — ищем колбэк
        if (response.requestId != null && Api.callbacks[response.requestId]) {
            var callback = Api.callbacks[response.requestId];
            delete Api.callbacks[response.requestId];
            callback(response);
        } else if (Api.onPush) {
            // Иначе — это push-событие
            Api.onPush(response);
        } else {
            console.warn('Нет обработчика для:', response.action);
        }
    };
};

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
