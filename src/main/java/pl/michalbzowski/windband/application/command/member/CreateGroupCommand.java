package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

@Data
public class CreateGroupCommand {
    private String name;
    private String description;
}
