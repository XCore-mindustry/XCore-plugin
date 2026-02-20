package org.xcore.plugin.config;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.xcore.plugin.common.BiMap;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;

public class BiMapTypeAdapterFactory implements TypeAdapterFactory {
    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != BiMap.class || !(type.getType() instanceof ParameterizedType parameterizedType)) {
            return null;
        }

        var typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length != 2) {
            return null;
        }

        var keyType = TypeToken.get(typeArguments[0]).getRawType();
        var valueType = TypeToken.get(typeArguments[1]).getRawType();
        if (keyType != String.class || (valueType != Long.class && valueType != long.class)) {
            return null;
        }

        var valueAdapter = gson.getAdapter(TypeToken.get(typeArguments[1]));
        return (TypeAdapter<T>) createStringBiMapAdapter((TypeAdapter<Long>) valueAdapter);
    }

    private TypeAdapter<BiMap<String, Long>> createStringBiMapAdapter(TypeAdapter<Long> valueAdapter) {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, BiMap<String, Long> biMap) throws IOException {
                if (biMap == null) {
                    out.nullValue();
                    return;
                }

                out.beginObject();
                for (var entry : biMap.entrySet()) {
                    if (entry.getKey() == null) {
                        throw new IOException("BiMap keys cannot be null when writing JSON objects");
                    }

                    out.name(entry.getKey());
                    valueAdapter.write(out, entry.getValue());
                }
                out.endObject();
            }

            @Override
            public BiMap<String, Long> read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }

                var biMap = new BiMap<String, Long>();
                in.beginObject();
                while (in.hasNext()) {
                    var key = in.nextName();
                    var value = valueAdapter.read(in);
                    biMap.put(key, value);
                }
                in.endObject();
                return biMap;
            }
        };
    }
}
