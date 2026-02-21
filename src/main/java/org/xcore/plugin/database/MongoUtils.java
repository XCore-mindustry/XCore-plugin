package org.xcore.plugin.database;

import arc.math.Mathf;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.conversions.Bson;

import static com.mongodb.client.model.Filters.regex;

public class MongoUtils {

    public static <T> PagedDataResult<T> search(MongoCollection<T> collection, String field, String value, int limit, int page) {
        var filter = regex(field, value, "i");

        long matchedDocs = collection.countDocuments(filter);
        if (matchedDocs == 0) return null;

        int totalPages = Mathf.ceil((float) matchedDocs / limit);

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        FindIterable<T> results = pagedData(filter, collection, limit, page);

        return new PagedDataResult<>((int) matchedDocs, totalPages, results);
    }

    public static <T> FindIterable<T> pagedData(Bson filter, MongoCollection<T> collection, int limit, int page) {
        int skips = (page - 1) * limit;
        return collection.find(filter).skip(skips).limit(limit);
    }
}
