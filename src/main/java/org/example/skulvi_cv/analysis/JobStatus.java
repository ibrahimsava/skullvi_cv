package org.example.skulvi_cv.analysis;

/** État d'une tâche d'analyse dans la file. */
public enum JobStatus {

    /** En attente d'un worker. */
    PENDING,

    /** Prise en charge par un worker. Remise en attente au démarrage si le processus est tombé. */
    RUNNING,

    DONE,

    /** Échec définitif : le nombre de tentatives est épuisé. */
    FAILED
}
