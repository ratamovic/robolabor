package com.codexperiments.robolabor.task;

public interface Task<TResult> extends TaskCallback<TResult>
{
    /**
     * Exécution de la tâche de fond sur un thread séparé. Ne pas modifier d'objets liés à l'UI ici.
     * @throws Exception Si une erreur quelconque survient, alors celle-ci est renvoyé au
     *             gestionnaire onError.
     */
    TResult onProcess() throws Exception;
}
