package org.example.skulvi_cv.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.application.Application;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Worker de la file d'analyses.
 *
 * <p>Un seul worker consomme la file, parce qu'un seul modèle Ollama tourne à la fois : lancer
 * deux analyses en parallèle sur le même CPU ne les rend pas plus rapides, ça les rend seulement
 * toutes les deux lentes. Le débit se règle en ajoutant des conteneurs, pas des threads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisQueue {

    /** Tâches traitées par réveil : vide une rafale sans monopoliser le thread indéfiniment. */
    private static final int BATCH = 5;

    private final AnalysisJobStore store;
    private final AnalysisPipeline pipeline;

    /** Place une candidature dans la file, à la priorité du plan de son organisation. */
    public void enqueue(Application application, int priority, boolean billable) {
        store.enqueue(application.getId(), application.getOrganization().getId(), priority, billable);
    }

    /**
     * Traite les tâches en attente.
     *
     * <p>{@code fixedDelay} et non {@code fixedRate} : le prochain réveil n'a lieu qu'après la
     * fin du précédent, ce qui sérialise naturellement les appels au modèle.
     */
    @Scheduled(fixedDelayString = "${talent.queue.poll-ms:3000}",
            initialDelayString = "${talent.queue.initial-delay-ms:10000}")
    public void drain() {
        for (int i = 0; i < BATCH; i++) {
            AnalysisJob job = store.claimNext();
            if (job == null) {
                return;
            }
            process(job.getId(), job.getApplicationId());
        }
    }

    /** Exécute l'analyse hors transaction, puis enregistre le verdict. */
    private void process(UUID jobId, UUID applicationId) {
        boolean ok;
        String error = null;
        try {
            ok = pipeline.run(applicationId);
        } catch (RuntimeException e) {
            ok = false;
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Tâche {} en erreur : {}", jobId, error);
        }
        store.finish(jobId, ok, error);
    }

    /**
     * Au démarrage, remet en attente ce qui était en cours quand le processus s'est arrêté.
     *
     * <p>Sans cela, une candidature resterait bloquée en {@code ANALYZING} pour toujours après
     * un simple redéploiement.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void requeueInterrupted() {
        int requeued = store.requeueInterrupted();
        if (requeued > 0) {
            log.info("{} analyse(s) interrompue(s) remise(s) en file", requeued);
        }
    }
}
