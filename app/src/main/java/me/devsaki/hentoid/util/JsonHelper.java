package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.Map;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.enums.AttributeType;
import timber.log.Timber;

/**
 * Created by avluis on 06/05/2016.
 * JSON related utility class
 */
public class JsonHelper {

    private JsonHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final String JSON_MIME_TYPE = "application/json";

    public static final Type MAP_STRINGS = Types.newParameterizedType(Map.class, String.class, String.class);

    private static final Moshi MOSHI = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .add(new AttributeType.AttributeTypeAdapter())
            .build();

    public static <K> String serializeToJson(K o, Type type) {
        JsonAdapter<K> jsonAdapter = MOSHI.adapter(type);

        return jsonAdapter.toJson(o);
    }

    /**
     * Serialize and save the object contents to a json file in the given directory.
     * The JSON file is created if it doesn't exist
     *
     * @param object Object to be serialized and saved
     * @param dir    Existing folder to save the JSON file to
     * @param <K>    Type of the object to save
     * @throws IOException If anything happens during file I/O
     */
    public static <K> DocumentFile createJson(@NonNull final Context context, K object, Type type, @NonNull DocumentFile dir) throws IOException {
        DocumentFile file = FileHelper.findOrCreateDocumentFile(context, dir, JSON_MIME_TYPE, Consts.JSON_FILE_NAME_V2);
        if (null == file)
            throw new IOException("Failed creating file " + Consts.JSON_FILE_NAME_V2 + " in " + dir.getUri().getPath());

        try (OutputStream output = FileHelper.getOutputStream(context, file)) {
            if (output != null) updateJson(object, type, output);
            else Timber.w("JSON file creation failed for %s", file.getUri().getPath());
        }
        return file;
    }

    /**
     * Serialize and save the object contents to an existing file using the JSON format
     *
     * @param object Object to be serialized and saved
     * @param file   Existing file to save to
     * @param <K>    Type of the object to save
     * @throws IOException If anything happens during file I/O
     */
    static <K> void updateJson(@NonNull final Context context, K object, Type type, @Nonnull DocumentFile file) throws IOException {
        if (!file.exists()) return;

        try (OutputStream output = FileHelper.getOutputStream(context, file)) {
            if (output != null) updateJson(object, type, output);
            else Timber.w("JSON file creation failed for %s", file.getUri());
        } catch (FileNotFoundException e) {
            Timber.e(e);
        }
    }

    private static <K> void updateJson(K object, Type type, @Nonnull OutputStream output) throws IOException {
        byte[] bytes = serializeToJson(object, type).getBytes();
        output.write(bytes);
        FileHelper.sync(output);
        output.flush();
    }

    public static <T> T jsonToObject(@NonNull final Context context, DocumentFile f, Class<T> type) throws IOException {
        return jsonToObject(readJsonString(context, f), type);
    }

    public static <T> T jsonToObject(@NonNull final Context context, @NonNull DocumentFile f, Type type) throws IOException {
        return jsonToObject(readJsonString(context, f), type);
    }

    private static String readJsonString(@NonNull final Context context, @NonNull DocumentFile f) {
        StringBuilder json = new StringBuilder();
        String sCurrentLine;
        boolean isFirst = true;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(FileHelper.getInputStream(context, f)))) {
            while ((sCurrentLine = br.readLine()) != null) {
                if (isFirst) {
                    // Strip UTF-8 BOMs if any
                    if (sCurrentLine.charAt(0) == '\uFEFF')
                        sCurrentLine = sCurrentLine.substring(1);
                    isFirst = false;
                }
                json.append(sCurrentLine);
            }
        } catch (Exception e) {
            Timber.e(e, "Error while reading %s", f.getUri().toString());
        }
        return json.toString();
    }

    public static <T> T jsonToObject(String s, Class<T> type) throws IOException {
        JsonAdapter<T> jsonAdapter = MOSHI.adapter(type);

        return jsonAdapter.lenient().fromJson(s);
    }

    public static <T> T jsonToObject(String s, Type type) throws IOException {
        JsonAdapter<T> jsonAdapter = MOSHI.adapter(type);

        return jsonAdapter.lenient().fromJson(s);
    }
}
