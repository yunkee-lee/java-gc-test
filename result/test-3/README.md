### JVM Flags

```Makefile
ZGC_FLAGS = -XX:+UnlockExperimentalVMOptions -XX:+UseZGC \
	-XX:SoftMaxHeapSize=4G -Xmx6G \
	-Xlog:gc*:file=zgc.log
```

### Test size
```java
// Test.java
int n = 1024 * 512;
```