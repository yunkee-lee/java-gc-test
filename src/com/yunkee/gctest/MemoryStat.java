package com.yunkee.gctest;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MemoryStat implements Runnable {

    private final static int SLEEP = 5000;

    private volatile boolean running = true;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss");

    @Override
    public void run() {

        while (running) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException ex) {
                System.out.println("[MemoryStat] " + ex.getMessage());
            }

            MemoryUsage memoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            Date now = new Date();

            print(dateFormat.format(now) + " - Init", memoryUsage.getInit());
            print("Used", memoryUsage.getUsed());
            print("Committed", memoryUsage.getUsed());
            print("Max", memoryUsage.getMax());
            System.out.println();
        }
    }

    private void print(String title, Long value) {
        System.out.print(title + ": " + Math.round(value / 1024 / 1024) + "M ");
    }

    public void stop() {
        setRunning(false);
    }

    private void setRunning(boolean running) {
        this.running = running;
    }
}