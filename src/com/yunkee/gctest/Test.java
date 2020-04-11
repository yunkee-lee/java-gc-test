package com.yunkee.gctest;

import java.util.LinkedList;
import java.util.List;

public class Test {

    private final static int SLEEP = 10;

    public static void main(String[] args) throws InterruptedException {
        String testMode = "count";
        if (args.length > 0) {
            if (args[0].equals("time")) {
                testMode = "time";
            }
        }

        MemoryStat memoryStat = new MemoryStat();
        new Thread(memoryStat).start();;

        Long startTime = System.currentTimeMillis();
        if (testMode.equals("count")) {
            runCountMode();
        }
        Long endTime = System.currentTimeMillis();
        Long elaspedSeconds = (endTime - startTime) / 1000;

        memoryStat.stop();
        System.out.println("Finished. Runtime: " +  elaspedSeconds + " s");
    }

    public static void runCountMode() throws InterruptedException {
        int n = 1024 * 256;
        List<String[]> list = new LinkedList<>();

        for (int i = 0; i < n; i++) {
            String[] emptyString = new String[1024];
            list.add(emptyString);

            if (i % 100 == 0) {
                Thread.sleep(SLEEP);
            }
            if (i % (1024 * 16) == 0) {
                list = new LinkedList<>();
            }
        }
    }
}