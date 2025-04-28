package com.furia.commands;

import com.furia.bot.FuriaBot;

public interface Command {
    void execute(Long chatId, FuriaBot bot);
}