package com.mycompany.messenger;

import io.javalin.Javalin;

/**
 *
 * @author User
 */
public class Messenger {

    public static void main(String[] args) {

        // В Javalin 7 ВСЁ настраивается внутри create()
        Javalin app = Javalin.create(config -> {
            
            // 1. Настройка CORS (для Javalin 6 и 7)
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });

            // 2. Настройка Вебсокета (теперь через config.routes)
            config.routes.ws("/websocket", ws -> {
                
                ws.onConnect(ctx -> {
                    System.out.println("Клиент подключился: " + ctx.sessionId());
                    ctx.send("Привет от сервера Javalin 7!");
                });

                ws.onMessage(ctx -> {
                    System.out.println("Получено: " + ctx.message());
                    ctx.send("Сервер получил: " + ctx.message());
                });

                ws.onClose(ctx -> {
                    System.out.println("Клиент отключился: " + ctx.sessionId());
                });

                ws.onError(ctx -> {
                    System.err.println("Ошибка сессии: " + ctx.sessionId());
                });
            });
        }).start(7070); // Запускаем сервер в самом конце
        
    }
}
