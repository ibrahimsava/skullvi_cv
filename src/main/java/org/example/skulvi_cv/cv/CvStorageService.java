package org.example.skulvi_cv.cv;

import org.example.skulvi_cv.config.TalentProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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
            throw new UncheckedIOException("Impossible de créer le dossier de stockage : " + base, e);
        }
    }

    /** Le nom de fichier est généré : jamais celui fourni par l'utilisateur. */
    public String store(byte[] bytes) {
        if (!isPdf(bytes)) {
            throw new IllegalArgumentException("Le fichier n'est pas un PDF valide");
        }
        String name = UUID.randomUUID() + ".pdf";
        try {
            Files.write(base.resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du CV impossible dans " + base, e);
        }
        return name;
    }

    public byte[] read(String name) {
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException("Chemin invalide");
        }
        try {
            return Files.readAllBytes(p);
        } catch (NoSuchFileException e) {
            throw new IllegalStateException("CV introuvable sur le disque : " + name, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du CV impossible : " + name, e);
        }
    }

    /** Un PDF commence toujours par "%PDF". */
    private static boolean isPdf(byte[] b) {
        return b != null && b.length > 4
                && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }
}