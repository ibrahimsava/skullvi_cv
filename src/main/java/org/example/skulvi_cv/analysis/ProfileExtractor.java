package org.example.skulvi_cv.analysis;

/** Transforme le texte brut d'un CV en profil structuré. Deux implémentations : Ollama (IA) ou règles. */
public interface ProfileExtractor {
    StructuredProfile extract(String cvText);
}
