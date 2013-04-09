package com.codexperiments.robolabor.task;

public interface TaskResult<TResult>
{
    /**
     * Si l'exécution de onProgress() se termine correctement, alors onFinish() est appelé sur le
     * thread UI. C'est ici que doivent être modifiés les objets liés à l'UI (ex: fusion des
     * résultats de la tâche avec les résultats déjà affichés).
     */
    void onFinish(TResult pResult);

    /**
     * Si l'exécution de onProgress() échoue, onError() est alors appelé sur le thread UI. Tout
     * message d'erreur ou modification/effacement des données affichées dans l'UI doivent être
     * réalisé ici.
     */
    void onError(Throwable pThrowable);
}
