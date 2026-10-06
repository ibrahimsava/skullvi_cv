package org.example.skulvi_cv.cv;

import org.example.skulvi_cv.config.TalentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class CvStorageService {

    private final Path base;

    public CvStorageService(TalentProperties props) {
        this.base = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Le nom de fichier est généré : jamais celui fourni par l'utilisateur. */
    public String store(byte[] bytes) {
        String name = UUID.randomUUID() + ".pdf";
        try {
            Files.write(base.resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    public byte[] read(String name) {
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base)) throw new IllegalArgumentException("Chemin invalide");
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
