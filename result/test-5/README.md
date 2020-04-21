### JVM Flags

```Makefile
G1GC_FLAGS = -XX:+UseG1GC \
	-XX:MaxGCPauseMillis=200 \
	-XX:G1HeapRegionSize=8m \
	-XX:+ParallelRefProcEnabled \
	-XX:-ResizePLAB \
	-XX:+UseThreadPriorities \
	-XX:ThreadPriorityPolicy=0 \
	-Xlog:gc*:file=g1gc.log

ZGC_FLAGS = -XX:+UnlockExperimentalVMOptions -XX:+UseZGC \
	-XX:SoftMaxHeapSize=4G -Xmx6G \
	-XX:ConcGCThreads=10 \
	-Xlog:gc*:file=zgc.log
```

### Test size
- 5 threads that send GET requests
- Each thread sends 60 requests sequentially
- The heaviest work done by a server is composed of fetching approximately 200 rows, buidling a graph, and traversing every node in the worst case