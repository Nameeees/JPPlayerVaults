package me.jp.jpvaults.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Planificadores compatibles con Paper y Folia.
 *
 * En Paper/Folia existen el planificador global, el asincrono y el de entidad;
 * en Spigot (y en versiones antiguas) solo el clasico. Todo se resuelve por
 * reflexion:
 *
 *  - Las tareas globales (autoguardado periodico, lotes de migracion) van al
 *    planificador global si existe.
 *  - Lo que toca a un jugador concreto (cerrar un inventario, guardar sus
 *    vaults) se programa en el hilo de la region de ESE jugador, que es la
 *    regla de Folia: asi el plugin no accede a estado de otra region.
 */
public final class Schedulers {

    /** Handle de una tarea periodica, sin atarse a BukkitTask ni ScheduledTask. */
    public interface Task {
        void cancel();
    }

    private static boolean resolved;
    private static Object globalScheduler;
    private static Method globalRunAtFixedRate;
    private static Method globalExecute;

    private Schedulers() {
    }

    /** Tarea periodica global (o en el planificador clasico si no hay otro). */
    public static Task runGlobalTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (resolveGlobal(plugin)) {
            try {
                Object task = globalRunAtFixedRate.invoke(globalScheduler, plugin,
                        (Consumer<Object>) scheduled -> runnable.run(), delayTicks, periodTicks);
                return cancelHandle(task);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                plugin.getLogger().warning("Could not use the global region scheduler (" + failure.getMessage()
                        + "); falling back to the Bukkit scheduler.");
                globalScheduler = null;
            }
        }
        final org.bukkit.scheduler.BukkitTask task =
                Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return new Task() {
            @Override
            public void cancel() {
                task.cancel();
            }
        };
    }

    /** Ejecuta algo en el hilo global correcto. */
    public static void runGlobal(Plugin plugin, Runnable runnable) {
        if (resolveGlobal(plugin)) {
            try {
                globalExecute.invoke(globalScheduler, plugin, (Runnable) runnable);
                return;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                globalScheduler = null;
            }
        }
        runnable.run();
    }

    /**
     * Ejecuta algo en el hilo de la region del jugador/entidad. En Spigot se
     * ejecuta directamente (ya estamos en el hilo del servidor).
     */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (entity == null) {
            runnable.run();
            return;
        }
        if (entitySchedulerRun == null && !resolveEntity()) {
            runnable.run();
            return;
        }
        try {
            Object scheduler = entitySchedulerOf(entity);
            if (scheduler == null) {
                runnable.run();
                return;
            }
            entitySchedulerRun.invoke(scheduler, plugin, (Consumer<Object>) scheduled -> runnable.run(), null);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            runnable.run();
        }
    }

    /**
     * Igual que {@link #runForEntity} pero un tick despues: abrir un inventario
     * dentro del propio evento de clic es fragil en algunas versiones.
     */
    public static void runLaterForEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (entity == null) {
            runLater(plugin, runnable, 1L);
            return;
        }
        if (entitySchedulerRunDelayed == null && !resolveEntity()) {
            runLater(plugin, runnable, 1L);
            return;
        }
        try {
            Object scheduler = entitySchedulerOf(entity);
            if (scheduler == null) {
                runLater(plugin, runnable, 1L);
                return;
            }
            entitySchedulerRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) scheduled -> runnable.run(),
                    null, 1L);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            runLater(plugin, runnable, 1L);
        }
    }

    /** Entity#getScheduler() sin compilar contra el: no existe en la API 1.8. */
    private static Object entitySchedulerOf(Entity entity) throws ReflectiveOperationException {
        Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
        return scheduler;
    }

    /** Ejecuta algo un tick despues en el planificador global. */
    public static void runLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (resolveGlobalDelayed(plugin)) {
            try {
                globalRunDelayed.invoke(globalScheduler, plugin,
                        (Consumer<Object>) scheduled -> runnable.run(), delayTicks);
                return;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                globalRunDelayed = null;
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }

    /** True si el servidor usa el modelo de hilos de Folia. */
    public static boolean folia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }

    private static Method entitySchedulerRun;
    private static Method entitySchedulerRunDelayed;
    private static Method globalRunDelayed;

    private static boolean resolveEntity() {
        try {
            Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entitySchedulerRun = entityScheduler.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entitySchedulerRunDelayed = entityScheduler.getMethod("runDelayed", Plugin.class, Consumer.class,
                    Runnable.class, long.class);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            return false;
        }
    }

    private static boolean resolveGlobalDelayed(Plugin plugin) {
        if (!resolveGlobal(plugin)) {
            return false;
        }
        if (globalRunDelayed != null) {
            return true;
        }
        try {
            Class<?> type = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalRunDelayed = type.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            globalRunDelayed = null;
        }
        return globalRunDelayed != null;
    }

    private static boolean resolveGlobal(Plugin plugin) {
        if (!resolved) {
            resolved = true;
            try {
                Method getter = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
                globalScheduler = getter.invoke(Bukkit.getServer());
                Class<?> type = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                globalRunAtFixedRate = type.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
                globalExecute = type.getMethod("execute", Plugin.class, Runnable.class);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                     | InvocationTargetException | RuntimeException missing) {
                globalScheduler = null;
            }
        }
        return globalScheduler != null;
    }

    /**
     * Envuelve la tarea del planificador moderno. Se resuelve cancel() desde la
     * INTERFAZ ScheduledTask a proposito: las clases que la implementan en Paper
     * no son publicas, y invocar el metodo desde su clase da IllegalAccessException
     * (la tarea seguiria corriendo).
     */
    private static Task cancelHandle(final Object scheduledTask) {
        return new Task() {
            @Override
            public void cancel() {
                try {
                    Method cancel = scheduledTaskCancel();
                    if (cancel == null) {
                        cancel = scheduledTask.getClass().getMethod("cancel");
                    }
                    cancel.invoke(scheduledTask);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Si ya habia terminado, no hay nada que cancelar.
                }
            }
        };
    }

    private static Method scheduledTaskCancel;

    private static Method scheduledTaskCancel() {
        if (scheduledTaskCancel == null) {
            try {
                scheduledTaskCancel = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask")
                        .getMethod("cancel");
            } catch (ClassNotFoundException | NoSuchMethodException missing) {
                scheduledTaskCancel = null;
            }
        }
        return scheduledTaskCancel;
    }
}
