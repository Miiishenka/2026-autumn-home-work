package company.vk.edu.distrib.compute.miiishenka.dao;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import company.vk.edu.distrib.compute.Dao;

public class PersistentDao<T> implements Dao<T> {
    private final String fileName;
    private final Map<String, T> storage;
    private final Function<T, String> serialize;

    public PersistentDao(
            String fileName,
            Function<T, String> serialize,
            Function<String, T> deserialize
    ) throws IOException {
        this.fileName = fileName;
        this.serialize = serialize;
        this.storage = new ConcurrentHashMap<>();
        try (RandomAccessFile file = new RandomAccessFile(fileName, "rw")) {
            if (file.length() == 0) {
                return;
            }
            int size = file.readInt();
            for (int i = 0; i < size; i++) {
                storage.put(file.readUTF(), deserialize.apply(file.readUTF()));
            }
        }
    }

    public static PersistentDao<String> stringDao(String fileName) throws IOException {
        return new PersistentDao<>(
                fileName,
                Function.identity(),
                Function.identity()
        );
    }

    public static PersistentDao<byte[]> byteArrayDao(String fileName) throws IOException {
        return new PersistentDao<>(
                fileName,
                Base64.getEncoder()::encodeToString,
                Base64.getDecoder()::decode
        );
    }

    @Override
    public T get(String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        T value = storage.get(key);
        if (value == null) {
            throw new NoSuchElementException("No value for key: " + key);
        }
        return value;
    }

    @Override
    public synchronized void upsert(String key, T value) throws IllegalArgumentException, IOException {
        storage.put(key, value);
        persist();
    }

    @Override
    public synchronized void delete(String key) throws IllegalArgumentException, IOException {
        storage.remove(key);
        persist();
    }

    @Override
    public synchronized void close() throws IOException {
        persist();
    }

    private void persist() throws IOException {
        Path target = Path.of(fileName).toAbsolutePath();
        Path temporary = Files.createTempFile(
                target.getParent(),
                target.getFileName().toString(),
                ".tmp"
        );

        try {
            try (RandomAccessFile file = new RandomAccessFile(temporary.toFile(), "rw")) {
                file.writeInt(storage.size());
                for (var entry : storage.entrySet()) {
                    file.writeUTF(entry.getKey());
                    file.writeUTF(serialize.apply(entry.getValue()));
                }
                file.getFD().sync();
            }
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
