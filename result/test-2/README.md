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
	-Xlog:gc*:file=zgc.log
```

### Test size
```java
// Test.java
int n = 1024 * 512;
```