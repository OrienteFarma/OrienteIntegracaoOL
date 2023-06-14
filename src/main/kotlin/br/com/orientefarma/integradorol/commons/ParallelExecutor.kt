package br.com.orientefarma.integradorol.commons

import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class Task(val name: String, val runnable: Runnable)

class ParallelExecutor private constructor(threadPoolSize: Int) {
    private val executor = Executors.newFixedThreadPool(threadPoolSize)
    private val tasks: Queue<Task> = LinkedList()
    private val estaExecutando: AtomicBoolean = AtomicBoolean(false)

    companion object {
        private var instance: ParallelExecutor? = null

        @Synchronized
        fun getInstance(threadPoolSize: Int): ParallelExecutor {
            if (instance == null) {
                instance = ParallelExecutor(threadPoolSize)
            }
            return instance!!
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
        tasks.add(newTask)
    }

    fun executeTasks() {
        if (!estaEmExecucao()) {
            while (tasks.isNotEmpty()) {
                val task = tasks.poll()
                executor.execute {
                    try {
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