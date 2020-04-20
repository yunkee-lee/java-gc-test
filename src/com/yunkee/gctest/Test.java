package com.yunkee.gctest;

import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;

public class Test {

    private final static int SLEEP = 10;

    public static void main(String[] args) throws InterruptedException {
        MemoryStat memoryStat = new MemoryStat();
        new Thread(memoryStat).start();;

        Long startTime = System.currentTimeMillis();
        run();
        Long endTime = System.currentTimeMillis();
        Long elaspedSeconds = (endTime - startTime) / 1000;

        memoryStat.stop();
        System.out.println("Finished. Runtime: " +  elaspedSeconds + " s");
    }

    public static void run() throws InterruptedException {
        // small: 1024 * 256
        // large: 1024 * 512
        int n = 1024 * 256;
        List<String[]> list = new LinkedList<>();
        WeakHashMap<String, String[]> wMap = new WeakHashMap<>();

        for (int i = 0; i < n; i++) {
            list.add(new String[1024]);
            wMap.put(String.valueOf(i), new String[1024]);

            if (i % 1024 == 0) {
                Thread.sleep(SLEEP);
                wMap.remove(String.valueOf(i));
            }
        }
    }
}