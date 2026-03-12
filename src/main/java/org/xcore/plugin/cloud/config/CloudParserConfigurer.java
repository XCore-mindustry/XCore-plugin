package org.xcore.plugin.cloud.config;

import io.leangen.geantyref.TypeToken;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import org.incendo.cloud.parser.ParserParameters;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.AllTeams;
import org.xcore.plugin.cloud.annotation.AllowNegativeDuration;
import org.xcore.plugin.cloud.annotation.DefaultUnit;
import org.xcore.plugin.cloud.parser.LanguageParser;
import org.xcore.plugin.cloud.parser.MapParser;
import org.xcore.plugin.cloud.parser.PlayerParser;
import org.xcore.plugin.cloud.parser.SmartDurationParser;
import org.xcore.plugin.cloud.parser.TeamParser;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.service.TimeService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Singleton
public class CloudParserConfigurer {

    private final TimeService timeService;
    private final TranslatorLanguagesProvider translatorLanguagesProvider;

    @Inject
    public CloudParserConfigurer(TimeService timeService,
                                 TranslatorLanguagesProvider translatorLanguagesProvider) {
        this.timeService = timeService;
        this.translatorLanguagesProvider = translatorLanguagesProvider;
    }

    public void configure(MindustryCommandManager<XCoreSender> manager) {
        manager.parserRegistry().registerAnnotationMapper(
                AllTeams.class,
                (_, _) -> ParserParameters.single(AllTeams.PARAM, true)
        );

        manager.parserRegistry().registerParserSupplier(
                TypeToken.get(Team.class),
                params -> new TeamParser(params.get(AllTeams.PARAM, false))
        );

        manager.parserRegistry().registerAnnotationMapper(
                DefaultUnit.class,
                (annotation, type) -> ParserParameters.single(DefaultUnit.PARAM, annotation.value())
        );

        manager.parserRegistry().registerAnnotationMapper(
                AllowNegativeDuration.class,
                (_, _) -> ParserParameters.single(AllowNegativeDuration.PARAM, true)
        );

        manager.parserRegistry().registerParserSupplier(
                TypeToken.get(Duration.class),
                params -> new SmartDurationParser(
                        timeService,
                        params.get(DefaultUnit.PARAM, TimeUnit.DAYS),
                        params.get(AllowNegativeDuration.PARAM, false)
                )
        );

        manager.parserRegistry().registerNamedParser("language", LanguageParser.parser(translatorLanguagesProvider));
        manager.parserRegistry().registerParser(PlayerParser.parser());
        manager.parserRegistry().registerParser(MapParser.parser());
    }
}
