// ========================================
// API-слой для WebSocket-соединения
// ========================================

var Api = {
    ws: null,
    connected: false,
    callbacks: {},           // requestId -> callback
    onPush: null,            // callback для push-событий (без requestId)
    onClose: null,
    token: null,
    userCode: null,
    userName: null,
    userSurname: null,
    _reqSeq: 0               // счётчик для requestId
};

Api.connect = function(url, onOpen, onClose) {
    Api.onClose = onClose;
    Api.callbacks = {};

    Api.ws = new WebSocket(url);

    Api.ws.onopen = function() {
        Api.connected = true;
        if (onOpen) onOpen();
    };

    Api.ws.onclose = function() {
        Api.connected = false;
        Api.callbacks = {};
        if (Api.onClose) Api.onClose();
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
    if (Api.ws) {
        Api.ws.close();
        Api.ws = null;
        Api.connected = false;
        Api.callbacks = {};
    }
};
