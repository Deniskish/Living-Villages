package dev.livingvillages.command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.persistence.VillageSavedData;
import dev.livingvillages.progression.*;
import dev.livingvillages.world.ProgressionService;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
public final class VillageCommands {
    private VillageCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("livingvillages")
                .then(Commands.literal("village")
                        .then(Commands.literal("list").executes(ctx->{
                            for(Village v:VillageSavedData.get(ctx.getSource().getLevel()).world.villages.values())
                                say(ctx.getSource(),v.id+" "+v.name+" "+v.center);return 1;
                        }))
                        .then(Commands.literal("inspect").executes(ctx->{
                            Village v=nearest(ctx.getSource());if(v==null)return 0;
                            say(ctx.getSource(),v.name+" id="+v.id+" population="+v.population()+" beds="+v.beds+" tier="+v.tier.label()
                                    +" technologies="+v.technologies.keySet()+" palette="+v.palette);
                            for(Goal goal:v.goals)say(ctx.getSource(),goal.toString());
                            for(Plan p:v.plans.values())if(!p.state().terminal())say(ctx.getSource(),p.id+" "+p.kind+" "+p.state());return 1;
                        })))
                // Read-only: tiers and technologies change only through the simulation.
                .then(Commands.literal("progression").then(Commands.literal("inspect").executes(ctx->{
                    Village v=nearest(ctx.getSource());
                    if(v==null) { ctx.getSource().sendFailure(Component.literal("No registered village contains this position"));return 0; }
                    var stock=dev.livingvillages.resource.Inventories.stock(ctx.getSource().getLevel(),v);
                    var s=ProgressionService.inspect(ctx.getSource().getLevel(),v,stock);
                    say(ctx.getSource(),v.name+" tier="+v.tier.label()+" qualifies="+(s.currentTier()==null?"none":s.currentTier().label())
                            +" next="+(s.nextTier()==null?"none":s.nextTier().label())+" evaluatedAt="+s.evaluatedAt());
                    Diagnostics.lines(ProgressionDiagnostics.tier(s)).forEach(line->say(ctx.getSource(),line));
                    for(CapabilityStatus c:s.capabilities())say(ctx.getSource(),c.line());
                    for(TechnologyStatus t:s.technologies()) {
                        say(ctx.getSource(),t.technology().branch()+" "+t.line());
                        if(t.stage()==TechnologyStatus.Stage.LOCKED)
                            Diagnostics.lines(ProgressionDiagnostics.technology(t,s.evaluatedAt())).stream().skip(1).forEach(line->say(ctx.getSource(),line));
                    }
                    return 1;
                })))
                .then(Commands.literal("citizen").then(Commands.literal("inspect").then(Commands.argument("target",EntityArgument.entity())
                        .executes(ctx->{
                            UUID id=EntityArgument.getEntity(ctx,"target").getUUID();
                            for(Village v:VillageSavedData.get(ctx.getSource().getLevel()).world.villages.values()) {
                                Citizen c=v.citizens.get(id);
                                if(c!=null) { say(ctx.getSource(),c.id+" "+c.name+" "+c.role+" level="+c.level()+" XP="+c.experience+
                                        " task="+c.currentTask+" skills="+c.skills+" statistics="+c.statistics);return 1; }
                            }return 0;
                        }))))
                .then(Commands.literal("plan").then(Commands.literal("inspect").then(Commands.argument("id",StringArgumentType.word())
                        .executes(ctx->{
                            UUID id;
                            try { id=UUID.fromString(StringArgumentType.getString(ctx,"id")); }
                            catch(IllegalArgumentException ex) { ctx.getSource().sendFailure(Component.literal("Invalid plan UUID"));return 0; }
                            for(Village v:VillageSavedData.get(ctx.getSource().getLevel()).world.villages.values()) {
                                Plan p=v.plans.get(id);if(p==null)continue;
                                Diagnostics.lines(Diagnostics.tree(p,v.plans)).forEach(line->say(ctx.getSource(),line));
                                say(ctx.getSource(),"requirements="+p.requirements+" reservations="+p.reservations+" progress="+p.progress+
                                        " lastProgress="+p.lastProgressTick+" retry="+p.nextRetryTick+" site="+p.site);
                                return 1;
                            }return 0;
                        }))))
                .then(Commands.literal("debug").requires(s->s.hasPermission(2))
                        .then(Commands.literal("on").executes(ctx->{ctx.getSource().getPlayerOrException().getPersistentData().putBoolean("livingvillages:debug",true);say(ctx.getSource(),"Living Villages debug enabled");return 1;}))
                        .then(Commands.literal("off").executes(ctx->{ctx.getSource().getPlayerOrException().getPersistentData().putBoolean("livingvillages:debug",false);say(ctx.getSource(),"Living Villages debug disabled");return 1;}))));
    }
    private static void say(CommandSourceStack source,String text) { source.sendSuccess(()->Component.literal(text),false); }
    private static Village nearest(CommandSourceStack source) {
        Pos p=new Pos((int)source.getPosition().x,(int)source.getPosition().y,(int)source.getPosition().z);
        return VillageSavedData.get(source.getLevel()).world.villages.values().stream()
                .filter(v->v.contains(p)).min(Comparator.comparingLong(v->v.center.distanceSquared(p))).orElse(null);
    }
}
