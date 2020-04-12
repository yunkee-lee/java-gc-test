# Garbage Collectors - G1GC & ZGC

## G1GC
- Garbage First Garbage Collector
- reclaims heap during Stop-The-World (STW, halting all application threads) and GC pause
- geared towards consistently short STW times and more overall time in GC
- optimized for throughput/latency balance
  - higher pause -> higher throughput, higher latency
  - lower pause -> lower throughput, lower latency
- a generational collector
	- 2 types of heap in young generation (in order to weed out transient data that manages to survive through an epoch cycle or two)
		- **Eden**
		- 2 Survivor spaces: **To** & **From**
	- a single heap in old generation
		- **Tenured**

- **epoch**: a full circuit of GC life cycle
	- app threads started
	- GC triggered
	- app threads stopped (STW)
	- heap reclaimed
	- heap size adjusted (and repeat)

- **Region**: a small independent heap
	- G1GC divides the total heap into homogenous regions that are logically combined into heaps (Eden, To, From, and Tenured)
	- smaller heaps cost more in overhead, but flexible
		- allow Tenured to be collected in portions, which caps latency
		- allow generations to be resized as necessary
    
- Humongous object: an object bigger than a Region
	- allocated in continuous regions of Free space
	- instantly added to Tenured
  
- G1GC reclaims heap by copying live data out of existing regions into empty regions
	- naturally defragments as data gets consolidated into empty regions

- Eden
	- consists of objects that are allocated since the current epoch started
	- all new objects except Humongous objects are allocated here
- Survivor (From & To)
	- From consists of objects that have survived at least one epoch
	- To is allocated but empty during epoch
	- objects surviving long enough get promoted to Tenured
	- the goal is to keep transient data from making it into Tenured (more expensive to remove)
- Tenured
	- consists of working set, Humongous objects, and fragmentation
	- GC'd in batches during qualifying epochs
	- old generation
- Free
	- consists of regions not allocated to any logical heap
	- Humongous objects are allocated from this region, which then get added to Tenured

- GC cycle:
  - Epoch starts: Eden and To are allocated and empty
  - Eden runs out of space and an allocation request fails; app threads will be stopped
  - Beginning of **STW**
  - GC worker threads scan through everywhere heap pointers can exist (threads, stacks, registers)
	  - for all **live** objects traced back to:
		  - Tenured -> ignored
		  - Eden -> evacuated into To with survival counter 1
		  - From -> ones with survival counter greater than a threshold get promoted to Tenured, otherwise they get evacuated to To with increased survival count
  > Evacuation: live objects are copied into new memory areas, compacting them in the process; the space previously occupied by live objects  is reused for allocation
  - All objects left in Eden and From are not referenced at this point, therefore are not live and can be reclaimed as Free space
  - To is renamed to From, Eden's size is recalculated and its regions are allocated from Free space
	  - `MaxGCPauseMillis` is used
  - The new To is calculated off of Eden's recalculated size and its regions are allocated
  - End of **STW**
  - App threads are allowed to continue

- Length of STW
	- actions taken while app threads are stopped:
		- scanning threads, stacks, registers
		- copying objects from one region to another
		- updating pointers
	- the main driver of STW time is the size and amount of objects being copied

- GC events
	- minor (or young): Eden + From -> To
	- mixed: minor + reclaiming Tenured region
	- full GC: all regions evacuated
	- minor/mixed + To-space exhaustion: minor/mixed + rollback + full GC
- In a smoothly running app, you'd see batches of minor and mixed events. You don't want to see full GC or To-space exhaustion when running G1GC

- Mixed events
	- At the end of a minor event, G1 checks if Multi-Phase Concurrent Marking Cycle (MPCMC) should be kicked off
	- The check occurs after Eden and From have been evacuated (minor event), so it asks if Tenured's size exceeds a threshold of the total heap (= IHOP, 45% by default)
		- initiating Heap Occupancy Percent
	- If the IHOP check passes, MPCMC runs **concurrently** with the app threads to produce liveness metadata for each Tenured region
	- MPCMC traces pointers into heap; each Tenured object that is found is marked as live, objects in Tenured that are not found are considered unreferenced and can be reclaimed
	- liveness metadata -> next GC event -> reclaimability check
		- the check decides if a GC event should be a minor or mixed
		- if the check fails, a GC event is a minor and liveness metadata gets thrown away
	- Once the reclaimability check passes (can be reclaimed), the GC event is a mixed and G1 determines which Tenured regions to collect in addition to Eden and From
	- Regions to be collected are the ones with the most garbage in them (least liveness, regions at the head of Reclaimable list) -> hence the name of the algorithm (G1GC)
	- Additional Tenured regions can be collected if doing so can be done under the target pause time of `-XX:MaxGCPauseMillis`
		- G1GC estimates the maximum number of regions it can collect at once without overstepping this limit based on previous collections as well as on the amount of detected garbage

- Humongous object
	- any single data collection greater than or equal to `G1HeapRegionSize / 2`
	- allocation failure from Free space triggers Full GC
	- MPCMC runs more often when Humongous objects are present as G1 checks IHOP threshold at every humongous object allocation

- Enhancements since JDK 11
  - Abortable mixed GC
    - collection set is split into mandatory and optional regions
    - optional regions get collected incrementally until there is no time left
  - Uncommit at remark
  - Old Gen on NVDIMM
  - Eliminate locks,
  - RemSet space reductions
  - Container awareness
  - 2% improvement in throughput and 18% improvement in responsiveness between JDK 11 and JDK 14

## ZGC
- pause times don't exceed **10ms**
- pause times don't increase with the heap or live-set size
	- they do increase with the root-set size (number of Java threads)
- handle heaps ranging from a 8MB to 16TB in size (16TB since JDK 13, otherwise 4TB)

- ZGC divides heap into regions, also called ZPages, which can be dynamically sized (unlike the G1GC)
- ZGC compacts heap by moving live objects from one page to another
- ZGC is **not** a generational GC (no generation)
- optimized for scalability and low latency

- Pointer Coloring
	- ZGC stores additional information in heap references
	- possible because of 64-bit platforms; ZGC is 64-bit only
	- 4-bits of possible states: marked0, marked1, remapped, finalizable
		- marked0, marked1: used to mark reachable objects
    - remapped: reference is up to date and points to the current loction of object (= not pointing into relocation set)
		- finalizable: object is only reachable through a finalizer
  - ZGC uses multi-mapping
    - multiple memory address in virtual memory -> a single address in physical memory
    - i.e. both "010<addr>" and "001<addr>" in virtual memory point to "<addr>" in physical memory
	- Having metadata information in heap references makes dereferencing more expensive, since the address needs to be masked to get the real address
		- ZGC sets exactly one bit of marked0, marked1, or remapped
		- it can map the same page to 3 different address; at any point of time only one is in use
		- [zgc/zGlobals_x86.cpp at master · openjdk/zgc · GitHub](https://github.com/openjdk/zgc/blob/master/src/hotspot/cpu/x86/gc/z/zGlobals_x86.cpp#L103)

- Load Barriers
  - code injected by JIT that run whenever an app thread loads a reference from the heap
	- the purporse is to examine the reference’s state and potentially do work before returning the reference
	- checks if a loaded object reference has a bad color; uses `test` and `jne`
		- if so, enter slow path to fix color, and set it to mark/relocate/remap (= good color)
	- depending on the stage of GC, the barrier either marks an object or relocates it if the reference isn't already marked/remapped
  - steps (works in a fall-through manner):
    - It checks whether `remap` bit is set. If so, the color is good and it returns the reference.
    - It checks whether a referenced object was in the relocation set. If it wasn't, an object doesn't need to get reloated. `remap` bit is set and it returns the updated reference.
    - If an object has been relocated, it skips to the next step. Otherwise, it relocates an object and creates an entiry in the forwarding table.
    - At this point, an object has been relocated. It updates the reference to the new address (either with the address from the previous step or by looking it up in the forwarding table), sets `remap` bit, and returns the reference
	- about 4% execution overhead.


- Striped Mark
	- heap divided into logical stripes
	- each GC thread works on its own stripe; it joins another thread if it finishes processing its stripes
	- minimized shared state, more scalability

- GC cycle
	- http://cr.openjdk.java.net/~pliden/slides/ZGC-PLMeetup-2019.pdf
	- [zgc/zDriver.cpp at master · openjdk/zgc · GitHub](https://github.com/openjdk/zgc/blob/master/src/hotspot/share/gc/z/zDriver.cpp#L393)
	- Pause -> STW, Concurrent -> app threads resumes
  
	- Phase 1: Pause Mark Start
		- scan thread stacks
		- mark objects pointed to roots (i.e. local / global variables)
		- `ZMarkRootsTask` -> `ZBarrier::mark_barrier_on_root_oop_field` -> `mark<Follow, Strong, Publish>`
		- very short pause since the number of objects is small
    
	- Phase 2: Concurrent Mark / Remap
		- heaviest work
		- Mark: walk object graph and mark all accessible objects (live set)
			- the load barrier tests all loaded references against a mask that determines if they've been marked or not yet (if not marked, they're added to a queue for marking)
      - it also collects liveness information
			- `ZMarkConcurrentRootsTask` -> `ZBarrier::ZBarrier::mark_barrier_on_oop_slow_path` -> `mark<Follow, Strong, Overflow>`
		- Remap: remap live data
			- walk object graph, which is the same as Mark phase, so Remap phase overlaps with Mark phase of the next GC cycle
      - adjust old pointers to new pages as shown in forwarding tables
      
	- Phase 3: Pause Mark End
		- synchronization point
		- short pause of 1 ms to deal with a possible race due to weak referencing
			- if roots are too big or this phase is not completed, ZGC keeps trying Concurrent Mark (`ZMarkTask`)
		- liveness/reachability analysis is completed at the end of this phase
    
  - Phase 4: Concurrent Process Non-Strong References
    - Steps:
      - process Soft/Weak/Final/PhantomReferences
      - process concurrent weak roots (i.e. string table)
      - unlink stale metadata (class, class loader) and nmethods (JIT-compiled native methods)
      - perform handshake
        - ensures that stale metadata and nmethods are no longer observable
        - prevent the race where a mutator first loads a pointer, which is logically null but not yet cleared
        - load barriers block resurrection attempt by returning null for old objects
      - unblock resurrection of weak/phantom references
      - purge stale metadata and nmethods that are unlinked
        - it doesn't remove anything, but it makes sure that nothing touches purged objects
    - concurrent class unloading occurs during unlink, handshake, and purge
    
  - Phase 5: Concurrent Reset Relocation Set
    - reset forwarding table and relocation set
    
  - Phase 6: Pause Verify
    - verify roots and weak references
    
  - Phase 7: Concurrent Select Relocation Set
    - ZGC divides the heap up in to pages and concurrently selects a set of pages whose live objects need to be relocated 
    - register relocatable pages with selector
    - register live and garbage pages (garbase pages get reclaimed immediately)
    - allow pages to be deleted
    - select pages to relocate
		- setup forwarding table (off heap)
    
  - Phase 8: Pause Relocate Start
    - another synchronization point; it finishes unloading stale metadata and nmethod, including class unloading
		- scan thread stacks to handle roots pointing to relocating set
		- ZGC relocates objects in the relocation set that are referenced as a root and remaps their reference to the new location
		- insert a forwarding entry into forwarding tables
		- `ZRelocateRootsTask` -> `ZBarrier::relocate_barrier_on_root_oop_field`

  - Phase 9: Concurrent Relocate
    - walk the relocation set and relocate all objects (pages) in it
    - insert a forwarding entry into forwarding tables
    - app threads can also relocate objects if they try to load them before GC has relocated them; achieved through load barrier
      - When app threads see a different color, they take slow paths meaning that they relocate objects
      - Then GC tries to relocate the same object by copying it and inserting a new entry into forwarding tables
      - GC finds the same entry entered by app threads, throws out the copied object
      - ZGC uses an atomic CAS-operation
    - compact heap (heap region becomes usable)
    - all objects are guaranteed to be relocated, but there may be references that still need to be remapped to their new addresses. This means that marking needs to inspect the forwarding table. It also explains why there are 2 marking bits and the marking phase alternates between the them. References that haven't been remapped still have the bit from last marking phase. If a new marking phase would use the same marking bit, the barrier would detect this reference as already marked.
    - `ZRelocate::relocate` ensures that all references the app sees will have been updated

- Tuning
  - increase heap size: `-Xmx<size>`
    - heap should accomodate the live-set of an application
    - there should be enough headroom in heap to allow allocations to be servied while GC is running
    - trade memory for lower latency
  - (maybe) increase number of concurrent GC threads: `XX:ConGCThreads=<number>`
    - ZGC has heuristics to automatically select this number
    - trade CPU-time for lower latency
  
- Long-term goal
  - make ZGC generational: higher allocation rates, lower heap overhead, lower CPU usage
  - sub-ms max pause times (WIP)
    - concurrent thread stack scanning
    - low latency VM

- Enhancements since JDK 11
  - concurrent class unloading
  - thread-local handshakes
  - max heap size is 16 TB
  - uncommit unused memory

## Appendix

- JIT (Just-in-time) compliation: compliation at runtime
  - compiles a series of bytecode to native machine code and performs certain optimizations as well

- Class metadata: internal representation of a class within JVM
  - a class loader allocates space for metadata, which gets deallocated when the corresponding class is unloaded

- Class loader: responsible for loading Java classes during runtime dinamically to JVM
  - load classes into memory when they're required by an application

- Finalizer: executed during object destruction, prior to the object being deallocated, and is complementary to an initializer

- Strong reference: the default type/class of a reference object
  - An object is strongly reachable if it can be reached by some thread without traversing any reference objects. A newly-created object is strongly reachable by the thread that created it.

- Soft reference
  - cleared at the discretion of GC in response to memory demand
  - all soft references to softly-reachable objects are guaranteed to have been cleared before JVM throws an `OutOfMemoryError`
  - most often used to implement memory-sensitive caches
  - An object is softly reachable if it is not strongly reachable but can be reached by traversing a soft reference.
  - Suppose an object is softly reachable, GC may choose to clear atomically all soft references to that object and all soft references to any other softly reachable objects from which that object is reachable through a chain of strong references

- Weak reference
  - Weak reference objects do not prevent their referent from being made finalizable, finalized, and then reclaimed
  - most often used to implement canonicalizing mappings
  - An object is weakly reachable if it is neither strongly nor softly reachable but can be reached by traversing a weak reference. When the weak references to a weakly-reachable object are cleared, the object becomes eligible for finalization.
  - Suppose an object is weakly rechable. GC will clear atomically all weak references to that object, and all weak references to any other weakly reachable objects from which that object is reaschable through a chain of strong and soft references

- Phantom reference
  - enqueued after GC determines that their referents may otherwise be reclaimed
  - most often used to schedule post-mortem cleanup actions
  - An object is phantom reachable if it is neither strongly, softly, nor weakly reachable, it has been finalized, and some phantom reference refers to it
  - Suppose an object is phantom reachable, GC will atomically clear all phantom references to that object and all phantom references to any other phantom reachable objects from which that object is reachable

- Weak root: all references in it are weak references; they don't affect the liveness of objects referred to