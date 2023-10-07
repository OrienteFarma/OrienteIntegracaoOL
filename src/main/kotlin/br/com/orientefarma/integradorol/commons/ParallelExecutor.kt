package br.com.orientefarma.integradorol.commons

import java.util.concurrent.BlockingDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean

data class Task(val name: String, val runnable: Runnable, val millisEntrada: Long = System.currentTimeMillis())

class ParallelExecutor private constructor(threadPoolSize: Int) {
    private val executor = Executors.newFixedThreadPool(threadPoolSize)
    private val tasks: BlockingDeque<Task> = LinkedBlockingDeque()
    private val processedTasks = CopyOnWriteArraySet<String>()
    private val estaExecutando: AtomicBoolean = AtomicBoolean(false)

    companion object {
        private var instance: ParallelExecutor? = null

        @Synchronized
        fun getInstance(threadPoolSize: Int = 1): ParallelExecutor {
            if (instance == null) {
                instance = ParallelExecutor(threadPoolSize)
            }
            return instance!!
        }

        fun liberarReprocessamentoJob(codProjeto: Int, nuPedOL: String){
            val processedTasks = getInstance().processedTasks
            processedTasks.remove("$codProjeto/$nuPedOL")
        }
        @Suppress("unused")
        fun limparFilaProcessados(){
            getInstance().processedTasks.clear()
        }
    }

    @Synchronized
    fun estaEmExecucao(): Boolean {
        if(!estaExecutando.get()){
            estaExecutando.set(true)
            return false
        }
        return estaExecutando.get()
    }

    fun addTask(name: String, task: Runnable) {
        val newTask = Task(name, task)
        val tarefaExistenteFila = tasks.any { it.name == newTask.name } ||
                processedTasks.any { it == newTask.name }
        if (!tarefaExistenteFila) {
            println("ParalleExecutor.OL Adicionando task $name na fila...")
            tasks.add(newTask)
        }
    }

    fun executeTasks() {
        if (!estaEmExecucao()) {
            while (tasks.isNotEmpty()) {
                val task: Task
                synchronized(tasks){
                    task = tasks.poll()
                    processedTasks.add(task.name)
                }
                executor.execute {
                    try {
                        val milisAgora = System.currentTimeMillis()
                        val segundosNaFila = milisAgora.minus(task.millisEntrada) / 1000
                        println("ParalleExecutor.OL Executando task ${task.name} da fila. $segundosNaFila seg na fila.")
                        task.runnable.run()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        System.err.println("Exceção ignorada na tarefa '${task.name}': ${e.message}")
                    }
                }
            }
            estaExecutando.set(false)
        }
    }
}