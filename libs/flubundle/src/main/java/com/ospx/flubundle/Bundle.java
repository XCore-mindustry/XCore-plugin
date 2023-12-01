package com.ospx.flubundle;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Log;
import fluent.bundle.FluentBundle;
import fluent.bundle.FluentResource;
import fluent.functions.cldr.CLDRFunctionFactory;
import fluent.syntax.parser.FTLParser;
import fluent.syntax.parser.FTLStream;
import mindustry.mod.Mod;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

import static mindustry.Vars.mods;

public class Bundle {
    public static Bundle INSTANCE = new Bundle();

    private ObjectMap<Locale, FluentBundle> sources = new ObjectMap<>();

    public void addSource(Class<? extends Mod> main) {
        addSource(mods.getMod(main).root.child("bundles"));
    }

    public void addSource(Fi directory) {
        directory.walk(fi -> {
            if (!fi.extEquals("ftl")) return;

            var codes = fi.nameWithoutExtension().split("_");
            var locale = codes.length == 2 ? new Locale(codes[1]) : new Locale(codes[1], codes[2]);

            FluentResource resource = FTLParser.parse(FTLStream.of(fi.readString()));

            if (resource.hasErrors()) {
                Log.err("Error parsing " + fi.name() + ": ");
                for (var error : resource.errors()) {
                    Log.err(error);
                }
            }

            sources.put(locale, FluentBundle.builder(locale, CLDRFunctionFactory.INSTANCE)
                    .addResource(resource)
                    .build());
        });
    }


    public String format(String id, Map<String, Object> args, Locale locale) {
        return sources.get(locale).format(id, args);
    }

    public Bundle() {
    }
}
