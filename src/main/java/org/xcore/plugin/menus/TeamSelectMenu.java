package org.xcore.plugin.menus;

import arc.util.Strings;
import mindustry.game.Team;
import mindustry.gen.Player;
import useful.menu.MenuFormatter;
import useful.menu.view.Action;
import useful.menu.view.Menu;
import useful.menu.view.MenuOption;

import static mindustry.Vars.state;
import static org.xcore.plugin.Utils.colorizedTeam;

public class TeamSelectMenu {
    public static Menu menu = new Menu();

    public static void init() {
        MenuFormatter.setFormatter((text, player, values) -> Strings.format(text, values));

        menu.transform(menu -> {
            var options = state.teams.getActive().map(data -> new MenuOption(colorizedTeam(data.team), Action.player(player -> {
                if (data.active()) {
                    player.team(data.team);
                    player.sendMessage(Strings.format("You chose the @[] team", colorizedTeam(data.team)));
                } else {
                    show(player, "Select team", "The selected command is no longer active. Try once more.");
                }
            }))).<MenuOption>toArray(MenuOption.class);

            menu.addOptionsRow(2, new MenuOption("Spectate", Action.player(player -> {
                player.team(Team.derelict);
                player.sendMessage("You are now spectating the game.");
            })));
            menu.addOptionsRow(2, options);
        });
    }

    public static void show(Player player) {
        show(player, "Select team", "Your team lost, select the team you want to play for.");
    }

    public static void show(Player player, String title, String content) {
        menu.show(player, menu -> {
            menu.title(title);
            menu.content(content);
        });
    }
}
