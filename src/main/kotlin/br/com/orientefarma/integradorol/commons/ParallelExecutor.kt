package br.com.orientefarma.integradorol.commons

import java.util.concurrent.Executors

data class Task(val name: String, val runnable: Runnable)

class ParallelExecutor private constructor(threadPoolSize: Int) {
    private val executor = Executors.newFixedThreadPool(threadPoolSize)
    private val tasks: MutableList<Task> = ArrayList()

    companion object {
        private var instance: ParallelExecutor? = null

        @Synchronized
        fun getInstance(threadPoolSize: Int): ParallelExecutor {
            if (instance == null || instance!!.executor.isShutdown) {
                instance = ParallelExecutor(threadPoolSize)
            }
            return instance!!
        }
    }

    fun addTask(name: String, task: Runnable) {
        val newTask = Task(name, task)
        tasks.add(newTask)
    }

    fun executeTasks() {
        for (task in tasks) {
            executor.execute {
                try {
                    task.runnable.run()
                } catch (e: Exception) {
                    e.printStackTrace()
                    System.err.println("Exceção ignorada na tarefa '${task.name}': ${e.message}")
                }
            }
        }
        executor.shutdown()
    }
}